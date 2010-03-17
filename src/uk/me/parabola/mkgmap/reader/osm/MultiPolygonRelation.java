package uk.me.parabola.mkgmap.reader.osm;

import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;

/**
 * Representation of an OSM Multipolygon Relation.<br/>
 * The different way of the multipolygon are joined to polygons and inner
 * polygons are cut out from the outer polygons.
 * 
 * @author WanMil
 */
public class MultiPolygonRelation extends Relation {
	private static final Logger log = Logger
			.getLogger(MultiPolygonRelation.class);

	private final Map<Long, Way> tileWayMap;
	private final Map<Long, String> roleMap = new HashMap<Long, String>();

	private ArrayList<BitSet> containsMatrix;
	private ArrayList<JoinedWay> polygons;
	private Set<JoinedWay> intersectingPolygons;

	private final uk.me.parabola.imgfmt.app.Area bbox;
	private Area bboxArea;
	
	/** 
	 * A point that has a lower or equal squared distance from 
	 * a line is treated as if it lies one the line.<br/>
	 * 1.0d is very exact. 2.0d covers rounding problems when converting
	 * OSM locations to mkgmap internal format. A larger value 
	 * is more tolerant against imprecise OSM data.
	 */
	private final double OVERLAP_TOLERANCE_DISTANCE = 2.0d;
	
	/**
	 * if one of these tags are contained in the multipolygon then the outer
	 * ways use the mp tags instead of their own tags.
	 */
	private static final List<String> polygonTags = Arrays.asList("boundary",
			"natural", "landuse", "land_area", "building", "waterway");

	/**
	 * Create an instance based on an existing relation. We need to do this
	 * because the type of the relation is not known until after all its tags
	 * are read in.
	 * 
	 * @param other
	 *            The relation to base this one on.
	 * @param wayMap
	 *            Map of all ways.
	 */
	public MultiPolygonRelation(Relation other, Map<Long, Way> wayMap, 
			uk.me.parabola.imgfmt.app.Area bbox) {
		this.tileWayMap = wayMap;
		this.bbox = bbox;

		setId(other.getId());

		if (log.isDebugEnabled()) {
			log.debug("Construct multipolygon", toBrowseURL());
		}

		for (Map.Entry<String, Element> pair : other.getElements()) {
			String role = pair.getKey();
			Element el = pair.getValue();
			if (log.isDebugEnabled()) {
				log.debug(" ", role, el.toBrowseURL());
			}
			addElement(role, el);
			roleMap.put(el.getId(), role);
		}

		setName(other.getName());
		copyTags(other);
	}

	/**
	 * Retrieves the mp role of the given element.
	 * 
	 * @param element
	 *            the element
	 * @return the role of the element
	 */
	private String getRole(Element element) {
		String role = roleMap.get(element.getId());
		if (role != null) {
			return role;
		}

		for (Map.Entry<String, Element> r_e : getElements()) {
			if (r_e.getValue() == element) {
				return r_e.getKey();
			}
		}
		return null;
	}

	/**
	 * Try to join the two ways.
	 * 
	 * @param joinWay
	 *            the way to which tempWay is added in case both ways could be
	 *            joined and checkOnly is false.
	 * @param tempWay
	 *            the way to be added to joinWay
	 * @param checkOnly
	 *            <code>true</code> checks only and does not perform the join
	 *            operation
	 * @return <code>true</code> if tempWay way is (or could be) joined to
	 *         joinWay
	 */
	private boolean joinWays(JoinedWay joinWay, JoinedWay tempWay,
			boolean checkOnly) {
		// use == or equals as comparator??
		if (joinWay.getPoints().get(0) == tempWay.getPoints().get(0)) {
			if (checkOnly == false) {
				for (Coord point : tempWay.getPoints().subList(1,
						tempWay.getPoints().size())) {
					joinWay.addPoint(0, point);
				}
				joinWay.addWay(tempWay);
			}
			return true;
		} else if (joinWay.getPoints().get(joinWay.getPoints().size() - 1) == tempWay
				.getPoints().get(0)) {
			if (checkOnly == false) {
				for (Coord point : tempWay.getPoints().subList(1,
						tempWay.getPoints().size())) {
					joinWay.addPoint(point);
				}
				joinWay.addWay(tempWay);
			}
			return true;
		} else if (joinWay.getPoints().get(0) == tempWay.getPoints().get(
				tempWay.getPoints().size() - 1)) {
			if (checkOnly == false) {
				int insertIndex = 0;
				for (Coord point : tempWay.getPoints().subList(0,
						tempWay.getPoints().size() - 1)) {
					joinWay.addPoint(insertIndex, point);
					insertIndex++;
				}
				joinWay.addWay(tempWay);
			}
			return true;
		} else if (joinWay.getPoints().get(joinWay.getPoints().size() - 1) == tempWay
				.getPoints().get(tempWay.getPoints().size() - 1)) {
			if (checkOnly == false) {
				int insertIndex = joinWay.getPoints().size();
				for (Coord point : tempWay.getPoints().subList(0,
						tempWay.getPoints().size() - 1)) {
					joinWay.addPoint(insertIndex, point);
				}
				joinWay.addWay(tempWay);
			}
			return true;
		}
		return false;
	}

	/**
	 * Combine a list of way segments to a list of maximally joined ways
	 * 
	 * @param segments
	 *            a list of closed or unclosed ways
	 * @return a list of closed ways
	 */
	private ArrayList<JoinedWay> joinWays(List<Way> segments) {
		// TODO check if the closed polygon is valid and implement a
		// backtracking algorithm to get other combinations

		ArrayList<JoinedWay> joinedWays = new ArrayList<JoinedWay>();
		if (segments == null || segments.size() == 0) {
			return joinedWays;
		}

		// go through all segments and categorize them to closed and unclosed
		// list
		ArrayList<JoinedWay> unclosedWays = new ArrayList<JoinedWay>();
		for (Way orgSegment : segments) {
			JoinedWay jw = new JoinedWay(orgSegment);
			roleMap.put(jw.getId(), getRole(orgSegment));
			if (orgSegment.isClosed()) {
				joinedWays.add(jw);
			} else {
				unclosedWays.add(jw);
			}
		}

		while (unclosedWays.isEmpty() == false) {
			JoinedWay joinWay = unclosedWays.remove(0);

			// check if the current way is already closed or if it is the last
			// way
			if (joinWay.isClosed() || unclosedWays.isEmpty()) {
				joinedWays.add(joinWay);
				continue;
			}

			boolean joined = false;

			// if we have a way that could be joined but which has a wrong role
			// then store it here and check in the end if it's working
			JoinedWay wrongRoleWay = null;
			String joinRole = getRole(joinWay);

			// go through all ways and check if there is a way that can be
			// joined with it
			// in this case join the two ways
			// => add all points of tempWay to joinWay, remove tempWay and put
			// joinWay to the beginning of the list
			// (not optimal but understandable - can be optimized later)
			for (JoinedWay tempWay : unclosedWays) {
				if (tempWay.isClosed()) {
					continue;
				}

				String tempRole = getRole(tempWay);
				// if a role is not 'inner' or 'outer' then it is used as
				// universal
				// check if the roles of the ways are matching
				if (("outer".equals(joinRole) == false && "inner"
						.equals(joinRole) == false)
						|| ("outer".equals(tempRole) == false && "inner"
								.equals(tempRole) == false)
						|| (joinRole != null && joinRole.equals(tempRole))) {
					// the roles are matching => try to join both ways
					joined = joinWays(joinWay, tempWay, false);
				} else {
					// the roles are not matching => test if both ways would
					// join

					// as long as we don't have an alternative way with wrong
					// role
					// or if the alternative way is shorter then check if
					// the way with the wrong role could be joined
					if (wrongRoleWay == null
							|| wrongRoleWay.getPoints().size() < tempWay
									.getPoints().size()) {
						if (joinWays(joinWay, tempWay, true)) {
							// save this way => maybe we will use it in the end
							// if we don't find any other way
							wrongRoleWay = tempWay;
						}
					}
				}

				if (joined) {
					// we have joined the way
					unclosedWays.remove(tempWay);
					break;
				}
			}

			if (joined == false && wrongRoleWay != null) {

				log.warn("Join ways with different roles. Multipolygon: "
						+ toBrowseURL());
				log.warn("Way1 Role:", getRole(joinWay));
				logWayURLs(Level.WARNING, "-", joinWay);
				log.warn("Way2 Role:", getRole(wrongRoleWay));
				logWayURLs(Level.WARNING, "-", wrongRoleWay);

				joined = joinWays(joinWay, wrongRoleWay, false);
				if (joined) {
					// we have joined the way
					unclosedWays.remove(wrongRoleWay);
					break;
				}
			}

			if (joined) {
				if (joinWay.isClosed()) {
					// it's closed => don't process it again
					joinedWays.add(joinWay);
				} else if (unclosedWays.isEmpty()) {
					// no more ways to join with
					// it's not closed but we cannot join it more
					joinedWays.add(joinWay);
				} else {
					// it is not yet closed => process it once again
					unclosedWays.add(0, joinWay);
				}
			} else {
				// it's not closed but we cannot join it more
				joinedWays.add(joinWay);
			}
		}

		return joinedWays;
	}

	/**
	 * Try to close all unclosed ways in the given list of ways.
	 * 
	 * @param wayList
	 *            a list of ways
	 */
	private void closeWays(ArrayList<JoinedWay> wayList) {
		// this is a VERY simple algorithm to close the ways
		// need to be improved

		for (JoinedWay way : wayList) {
			if (way.isClosed() || way.getPoints().size() <= 3) {
				continue;
			}
			Coord p1 = way.getPoints().get(0);
			Coord p2 = way.getPoints().get(way.getPoints().size() - 1);
			
			// check if both endpoints are outside the bounding box 
			// and if they are on the same side of the bounding box
			if ((p1.getLatitude() <= bbox.getMinLat() && p2.getLatitude() <= bbox.getMinLat())
				 || (p1.getLatitude() >= bbox.getMaxLat() && p2.getLatitude() >= bbox.getMaxLat()) 		
				 || (p1.getLongitude() <= bbox.getMinLong() && p2.getLongitude() <= bbox.getMinLong()) 		
				 || (p1.getLongitude() >= bbox.getMaxLong() && p2.getLongitude() >= bbox.getMaxLong())) {
				// they are on the same side outside of the bbox
				// so just close them without worrying about if
				// they intersect itself because the intersection also
				// is outside the bbox
				way.closeWayArtificially();
				log.info("Endpoints of way",way,"are both outside the bbox. Closing it directly.");
				continue;
			}
			
			Line2D closingLine = new Line2D.Float(p1.getLongitude(), p1
					.getLatitude(), p2.getLongitude(), p2.getLatitude());

			boolean intersects = false;
			Coord lastPoint = null;
			// don't use the first and the last point
			// the closing line can intersect only in one point or complete.
			// Both isn't interesting for this check
			for (Coord thisPoint : way.getPoints().subList(1,
					way.getPoints().size() - 1)) {
				if (lastPoint != null) {
					if (closingLine.intersectsLine(lastPoint.getLongitude(),
							lastPoint.getLatitude(), thisPoint.getLongitude(),
							thisPoint.getLatitude())) {
						intersects = true;
						break;
					}
				}
				lastPoint = thisPoint;
			}

			if (intersects == false) {
				// close the polygon
				// the new way segment does not intersect the rest of the
				// polygon
				log.info("Closing way", way);
				log.info("from", way.getPoints().get(0).toOSMURL());
				log.info("to", way.getPoints().get(way.getPoints().size() - 1)
						.toOSMURL());
				// mark this ways as artificially closed
				way.closeWayArtificially();
			}
		}
	}

	/**
	 * Removes all ways non closed ways from the given list (
	 * <code>{@link Way#isClosed()} == false</code>)
	 * 
	 * @param wayList
	 *            list of ways
	 */
	private void removeUnclosedWays(ArrayList<JoinedWay> wayList) {
		Iterator<JoinedWay> it = wayList.iterator();
		boolean first = true;
		while (it.hasNext()) {
			JoinedWay tempWay = it.next();
			if (tempWay.isClosed() == false) {
				if (first) {
					log.warn(
						"Cannot join the following ways to closed polygons. Multipolygon",
						toBrowseURL());
				}
				logWayURLs(Level.WARNING, "- way:", tempWay);

				it.remove();
				first = false;
			}
		}
	}

	/**
	 * Removes all ways that are completely outside the bounding box. 
	 * This reduces error messages from problems on the tile bounds.
	 * @param wayList list of ways
	 */
	private void removeWaysOutsideBbox(ArrayList<JoinedWay> wayList) {
		ListIterator<JoinedWay> wayIter = wayList.listIterator();
		while (wayIter.hasNext()) {
			JoinedWay w = wayIter.next();
			boolean remove = true;
			// check all points
			for (Coord c : w.getPoints()) {
				if (bbox.contains(c)) {
					// if one point is in the bounding box the way should not be removed
					remove = false;
					break;
				}
			}

			if (remove) {
				// check if the polygon contains the complete bounding box
				if (w.getBounds().contains(bboxArea.getBounds())) {
					remove = false;
				}
			}
			
			if (remove) {
				if (log.isDebugEnabled()) {
					log.debug("Remove way", w.getId(),
						"because it is completely outside the bounding box.");
				}
				wayIter.remove();
			}
		}
	}

	/**
	 * Find all polygons that are not contained by any other polygon.
	 * 
	 * @param candidates
	 *            all polygons that should be checked
	 * @param roleFilter
	 *            an additional filter
	 * @return all polygon indexes that are not contained by any other polygon
	 */
	private BitSet findOutmostPolygons(BitSet candidates, BitSet roleFilter) {
		BitSet realCandidates = ((BitSet) candidates.clone());
		realCandidates.and(roleFilter);
		return findOutmostPolygons(realCandidates);
	}

	/**
	 * Finds all polygons that are not contained by any other polygons and that match
	 * to the given role. All polygons with index given by <var>candidates</var>
	 * are used.
	 * 
	 * @param candidates
	 *            indexes of the polygons that should be used
	 * @return the bits of all outmost polygons are set to true
	 */
	private BitSet findOutmostPolygons(BitSet candidates) {
		BitSet outmostPolygons = new BitSet();

		// go through all candidates and check if they are contained by any
		// other candidate
		for (int candidateIndex = candidates.nextSetBit(0); candidateIndex >= 0; candidateIndex = candidates
				.nextSetBit(candidateIndex + 1)) {
			// check if the candidateIndex polygon is not contained by any
			// other candidate polygon
			boolean isOutmost = true;
			for (int otherCandidateIndex = candidates.nextSetBit(0); otherCandidateIndex >= 0; otherCandidateIndex = candidates
					.nextSetBit(otherCandidateIndex + 1)) {
				if (contains(otherCandidateIndex, candidateIndex)) {
					// candidateIndex is not an outermost polygon because it is
					// contained by the otherCandidateIndex polygon
					isOutmost = false;
					break;
				}
			}
			if (isOutmost) {
				// this is an outmost polygon
				// put it to the bitset
				outmostPolygons.set(candidateIndex);
			}
		}

		return outmostPolygons;
	}

	private ArrayList<PolygonStatus> getPolygonStatus(BitSet outmostPolygons,
			String defaultRole) {
		ArrayList<PolygonStatus> polygonStatusList = new ArrayList<PolygonStatus>();
		for (int polyIndex = outmostPolygons.nextSetBit(0); polyIndex >= 0; polyIndex = outmostPolygons
				.nextSetBit(polyIndex + 1)) {
			// polyIndex is the polygon that is not contained by any other
			// polygon
			JoinedWay polygon = polygons.get(polyIndex);
			String role = getRole(polygon);
			// if the role is not explicitly set use the default role
			if (role == null || "".equals(role)) {
				role = defaultRole;
			} 
			polygonStatusList.add(new PolygonStatus("outer".equals(role), polyIndex, polygon));
		}
		return polygonStatusList;
	}

	/**
	 * Process the ways in this relation. Joins way with the role "outer" Adds
	 * ways with the role "inner" to the way with the role "outer"
	 */
	public void processElements() {
		log.info("Processing multipolygon", toBrowseURL());

		// don't care about outer and inner declaration
		// because this is a first try
		ArrayList<Way> allWays = new ArrayList<Way>();

		for (Map.Entry<String, Element> r_e : getElements()) {
			if (r_e.getValue() instanceof Way) {
				allWays.add((Way) r_e.getValue());
			} else {
				log.warn("Non way element", r_e.getValue().getId(),
						"in multipolygon", getId());
			}
		}

		// create an Area for the bbox to clip the polygons
		bboxArea = new Area(new Rectangle(bbox.getMinLong(), bbox
			.getMinLat(), bbox.getMaxLong() - bbox.getMinLong(),
			bbox.getMaxLat() - bbox.getMinLat()));

		// join all single ways to polygons, try to close ways and remove non closed ways 
		polygons = joinWays(allWays);
		closeWays(polygons);
		removeUnclosedWays(polygons);

		// now only closed ways are left => polygons only

		// check if we have at least one polygon left
		if (polygons.isEmpty()) {
			// do nothing
			log.info("Multipolygon " + toBrowseURL()
					+ " does not contain a closed polygon.");
			cleanup();
			return;
		}

		removeWaysOutsideBbox(polygons);

		if (polygons.isEmpty()) {
			// do nothing
			log.info("Multipolygon " + toBrowseURL()
					+ " is completely outside the bounding box. It is not processed.");
			cleanup();
			return;
		}

		// the intersectingPolygons marks all intersecting/overlapping polygons
		intersectingPolygons = new HashSet<JoinedWay>();
		
		// check which polygons lie inside which other polygon 
		createContainsMatrix(polygons);

		// unfinishedPolygons marks which polygons are not yet processed
		BitSet unfinishedPolygons = new BitSet(polygons.size());
		unfinishedPolygons.set(0, polygons.size());

		// create bitsets which polygons belong to the outer and to the inner role
		BitSet innerPolygons = new BitSet();
		BitSet taggedInnerPolygons = new BitSet();
		BitSet outerPolygons = new BitSet();
		BitSet taggedOuterPolygons = new BitSet();
		
		int wi = 0;
		for (Way w : polygons) {
			String role = getRole(w);
			if ("inner".equals(role)) {
				innerPolygons.set(wi);
				taggedInnerPolygons.set(wi);
			} else if ("outer".equals(role)) {
				outerPolygons.set(wi);
				taggedOuterPolygons.set(wi);
			} else {
				// unknown role => it could be both
				innerPolygons.set(wi);
				outerPolygons.set(wi);
			}
			wi++;
		}

		if (outerPolygons.isEmpty()) {
			log.warn("Multipolygon", toBrowseURL(),
				"does not contain any way tagged with role=outer or empty role.");
			cleanup();
			return;
		}

		Queue<PolygonStatus> polygonWorkingQueue = new LinkedBlockingQueue<PolygonStatus>();
		BitSet nestedOuterPolygons = new BitSet();
		BitSet nestedInnerPolygons = new BitSet();

		BitSet outmostPolygons ;
		BitSet outmostInnerPolygons = new BitSet();
		boolean outmostInnerFound = false;
		do {
			outmostInnerFound = false;
			outmostPolygons = findOutmostPolygons(unfinishedPolygons);

			if (outmostPolygons.intersects(taggedInnerPolygons)) {
				outmostInnerPolygons.or(outmostPolygons);
				outmostInnerPolygons.and(taggedInnerPolygons);

				if (log.isDebugEnabled())
					log.debug("wrong inner polygons: " + outmostInnerPolygons);
				// do not process polygons tagged with role=inner but which are
				// not
				// contained by any other polygon
				unfinishedPolygons.andNot(outmostInnerPolygons);
				outmostPolygons.andNot(outmostInnerPolygons);
				outmostInnerFound = true;
			}
		} while (outmostInnerFound);
		
		if (outmostPolygons.isEmpty() == false) {
			polygonWorkingQueue.addAll(getPolygonStatus(outmostPolygons, "outer"));
		}

		while (polygonWorkingQueue.isEmpty() == false) {

			// the polygon is not contained by any other unfinished polygon
			PolygonStatus currentPolygon = polygonWorkingQueue.poll();

			// this polygon is now processed and should not be used by any
			// further step
			unfinishedPolygons.clear(currentPolygon.index);

			BitSet polygonContains = new BitSet();
			polygonContains.or(containsMatrix.get(currentPolygon.index));
			// use only polygon that are contained by the polygon
			polygonContains.and(unfinishedPolygons);
			// polygonContains is the intersection of the unfinished and
			// the contained polygons

			// get the holes
			// these are all polygons that are in the main polygon
			// and that are not contained by any other polygon
			boolean holesOk = true;
			BitSet holeIndexes;
			do {
				holeIndexes = findOutmostPolygons(polygonContains);
				holesOk = true;
				if (currentPolygon.outer) {
					// for role=outer only role=inner is allowed
					if (holeIndexes.intersects(taggedOuterPolygons)) {
						BitSet addOuterNestedPolygons = new BitSet();
						addOuterNestedPolygons.or(holeIndexes);
						addOuterNestedPolygons.and(taggedOuterPolygons);
						nestedOuterPolygons.or(addOuterNestedPolygons);
						holeIndexes.andNot(addOuterNestedPolygons);
						// do not process them
						unfinishedPolygons.andNot(addOuterNestedPolygons);
						polygonContains.andNot(addOuterNestedPolygons);
						
						// recalculate the holes again to get all inner polygons 
						// in the nested outer polygons
						holesOk = false;
					}
				} else {
					// for role=inner both role=inner and role=outer is supported
					// although inner in inner is not officially allowed
					if (holeIndexes.intersects(taggedInnerPolygons)) {
						// process inner in inner but issue a warning later
						BitSet addInnerNestedPolygons = new BitSet();
						addInnerNestedPolygons.or(holeIndexes);
						addInnerNestedPolygons.and(taggedInnerPolygons);
						nestedInnerPolygons.or(addInnerNestedPolygons);
					}
				}
			} while (holesOk == false);

			ArrayList<PolygonStatus> holes = getPolygonStatus(holeIndexes, 
				(currentPolygon.outer ? "inner" : "outer"));

			// these polygons must all be checked for holes
			polygonWorkingQueue.addAll(holes);

			// check if the polygon has tags and therefore should be processed
			boolean processPolygon = currentPolygon.outer
					|| hasPolygonTags(currentPolygon.polygon);

			if (processPolygon) {
				List<Way> singularOuterPolygons;
				if (holes.isEmpty()) {
					singularOuterPolygons = Collections
							.singletonList((Way) new JoinedWay(currentPolygon.polygon));
				} else {
					List<Way> innerWays = new ArrayList<Way>(holes.size());
					for (PolygonStatus polygonHoleStatus : holes) {
						innerWays.add(polygonHoleStatus.polygon);
					}

					singularOuterPolygons = cutOutInnerPolygons(currentPolygon.polygon,
						innerWays);
				}
				
				if (currentPolygon.polygon.getOriginalWays().size() == 1) {
					// the original way was a closed polygon which
					// has been replaced by the new cutted polygon
					// the original way should not appear
					// so we remove all tags
					currentPolygon.polygon.removeAllTagsDeep();
				} else {
					// remove all polygons tags from the original ways
					// sometimes the ways seem to be autoclosed later on
					// in mkgmap
					for (Way w : currentPolygon.polygon.getOriginalWays()) {
						for (String polygonTag : polygonTags) {
							w.deleteTag(polygonTag);
						}
					}
				}

				boolean useRelationTags = currentPolygon.outer
						&& hasPolygonTags(this);
				if (useRelationTags) {
					// the multipolygon contains tags that overwhelm the
					// tags of the outer polygon
					for (Way p : singularOuterPolygons) {
						p.copyTags(this);
					}
				}

				for (Way mpWay : singularOuterPolygons) {
					// put the cut out polygons to the
					// final way map
					tileWayMap.put(mpWay.getId(), mpWay);
				}
			}
		}
		
		if (log.isLoggable(Level.WARNING) && 
				(outmostInnerPolygons.cardinality()+unfinishedPolygons.cardinality()+nestedOuterPolygons.cardinality()+nestedInnerPolygons.cardinality() >= 1)) {
			log.warn("Multipolygon", toBrowseURL(), "contains errors.");

			runIntersectionCheck(unfinishedPolygons);
			runOutmostInnerPolygonCheck(outmostInnerPolygons);
			runNestedOuterPolygonCheck(nestedOuterPolygons);
			runNestedInnerPolygonCheck(nestedInnerPolygons);
			runWrongInnerPolygonCheck(unfinishedPolygons, innerPolygons);

			// we have at least one polygon that could not be processed
			// Probably we have intersecting or overlapping polygons
			// one possible reason is if the relation overlaps the tile
			// bounds
			// => issue a warning
			List<JoinedWay> lostWays = getWaysFromPolygonList(unfinishedPolygons);
			for (JoinedWay w : lostWays) {
				log.warn("Polygon", w, "is not processed due to an unknown reason.");
				logWayURLs(Level.WARNING, "-", w);
			}
		}

		cleanup();
	}

	private void runIntersectionCheck(BitSet unfinishedPolys) {
		if (intersectingPolygons.isEmpty()) {
			// nothing to do
			return;
		}

		log.warn("Some polygons are intersecting. This is not allowed in multipolygons.");

		boolean oneOufOfBbox = false;
		for (JoinedWay polygon : intersectingPolygons) {
			int pi = polygons.indexOf(polygon);
			unfinishedPolys.clear(pi);

			boolean outOfBbox = false;
			for (Coord c : polygon.getPoints()) {
				if (bbox.contains(c) == false) {
					outOfBbox = true;
					oneOufOfBbox = true;
					break;
				}
			}

			logWayURLs(Level.WARNING, (outOfBbox ? "*" : "-"), polygon);
		}
		if (oneOufOfBbox) {
			log.warn("Some of these intersections/overlaps may be caused by incomplete data on bounding box edges (*).");
		}
	}

	private void runNestedOuterPolygonCheck(BitSet nestedOuterPolygons) {
		// just print out warnings
		// the check has been done before
		for (int wiIndex = nestedOuterPolygons.nextSetBit(0); wiIndex >= 0; wiIndex = nestedOuterPolygons
				.nextSetBit(wiIndex + 1)) {
			Way outerWay = polygons.get(wiIndex);
			log.warn("Polygon",	outerWay, "carries role outer but lies inside an outer polygon. Potentially its role should be inner.");
		}
	}
	
	private void runNestedInnerPolygonCheck(BitSet nestedInnerPolygons) {
		// just print out warnings
		// the check has been done before
		for (int wiIndex = nestedInnerPolygons.nextSetBit(0); wiIndex >= 0; wiIndex = nestedInnerPolygons
				.nextSetBit(wiIndex + 1)) {
			Way innerWay = polygons.get(wiIndex);
			log.warn("Polygon",	innerWay, "carries role", getRole(innerWay), "but lies inside an inner polygon. Potentially its role should be outer.");
		}
	}	
	
	private void runOutmostInnerPolygonCheck(BitSet outmostInnerPolygons) {
		// just print out warnings
		// the check has been done before
		for (int wiIndex = outmostInnerPolygons.nextSetBit(0); wiIndex >= 0; wiIndex = outmostInnerPolygons
				.nextSetBit(wiIndex + 1)) {
			Way innerWay = polygons.get(wiIndex);
			log.warn("Polygon",	innerWay, "carries role", getRole(innerWay), "but is not inside any other polygon. Potentially it does not belong to this multipolygon.");
		}
	}

	private void runWrongInnerPolygonCheck(BitSet unfinishedPolygons,
			BitSet innerPolygons) {
		// find all unfinished inner polygons that are not contained by any
		BitSet wrongInnerPolygons = findOutmostPolygons(unfinishedPolygons, innerPolygons);
		if (log.isDebugEnabled()) {
			log.debug("unfinished", unfinishedPolygons);
			log.debug("inner", innerPolygons);
			// other polygon
			log.debug("wrong", wrongInnerPolygons);
		}
		if (wrongInnerPolygons.isEmpty() == false) {
			// we have an inner polygon that is not contained by any outer polygon
			// check if
			for (int wiIndex = wrongInnerPolygons.nextSetBit(0); wiIndex >= 0; wiIndex = wrongInnerPolygons
					.nextSetBit(wiIndex + 1)) {
				BitSet containedPolygons = new BitSet();
				containedPolygons.or(unfinishedPolygons);
				containedPolygons.and(containsMatrix.get(wiIndex));

				Way innerWay = polygons.get(wiIndex);
				if (containedPolygons.isEmpty()) {
					log.warn("Polygon",	innerWay, "carries role", getRole(innerWay),
						"but is not inside any outer polygon. Potentially it does not belong to this multipolygon.");
				} else {
					log.warn("Polygon",	innerWay, "carries role", getRole(innerWay),
						"but is not inside any outer polygon. Potentially the roles are interchanged with the following",
						(containedPolygons.cardinality() > 1 ? "ways" : "way"), ".");

					for (int wrIndex = containedPolygons.nextSetBit(0); wrIndex >= 0; wrIndex = containedPolygons
							.nextSetBit(wrIndex + 1)) {
						logWayURLs(Level.WARNING, "-", polygons.get(wrIndex));
						unfinishedPolygons.set(wrIndex);
						wrongInnerPolygons.set(wrIndex);
					}
				}

				unfinishedPolygons.clear(wiIndex);
				wrongInnerPolygons.clear(wiIndex);
			}
		}
	}

	private void cleanup() {
		roleMap.clear();
		containsMatrix = null;
		polygons = null;
		bboxArea = null;
		intersectingPolygons = null;
	}

	private CutPoint calcNextCutPoint(AreaCutData areaData) {
		if (areaData.innerAreas == null || areaData.innerAreas.isEmpty()) {
			return null;
		}
		
		if (areaData.innerAreas.size() == 1) {
			// make it short if there is only one inner area
			Rectangle outerBounds = areaData.outerArea.getBounds();
			CoordinateAxis axis = (outerBounds.width < outerBounds.height ? CoordinateAxis.LONGITUDE : CoordinateAxis.LATITUDE);
			CutPoint oneCutPoint = new CutPoint(axis);
			oneCutPoint.addArea(areaData.innerAreas.get(0));
			return oneCutPoint;
		}
		
		ArrayList<Area> innerStart = new ArrayList<Area>(
				areaData.innerAreas);
		
		ArrayList<CutPoint> bestCutPoints = new ArrayList<CutPoint>(CoordinateAxis.values().length);
		
		for (CoordinateAxis axis : CoordinateAxis.values()) {
			CutPoint bestCutPoint = new CutPoint(axis);
			CutPoint currentCutPoint = new CutPoint(axis);

			Collections.sort(innerStart, (axis == CoordinateAxis.LONGITUDE ? COMP_LONG_START: COMP_LAT_START));

			Iterator<Area> startIter = innerStart.iterator();
			while (startIter.hasNext()) {
				Area nextStart = startIter.next();
				currentCutPoint.addArea(nextStart);

				if (currentCutPoint.compareTo(bestCutPoint) > 0) {
					bestCutPoint = currentCutPoint.duplicate();
				}
			}
			bestCutPoints.add(bestCutPoint);
		}

		return Collections.max(bestCutPoints);
		
	}

	/**
	 * Cut out all inner polygons from the outer polygon. This will divide the outer
	 * polygon in several polygons.
	 * 
	 * @param outerPolygon
	 *            the outer polygon
	 * @param innerPolygons
	 *            a list of inner polygons
	 * @return a list of polygons that make the outer polygon cut by the inner
	 *         polygons
	 */
	private List<Way> cutOutInnerPolygons(Way outerPolygon, List<Way> innerPolygons) {
		if (innerPolygons.isEmpty()) {
			Way outerWay = new JoinedWay(outerPolygon);
			if (log.isDebugEnabled()) {
				log.debug("Way", outerPolygon.getId(), "splitted to way", outerWay.getId());
			}
			return Collections.singletonList(outerWay);
		}

		// use the java.awt.geom.Area class because it's a quick
		// implementation of what's needed

		// this list contains all non overlapping and singular areas
		// of the outerPolygon
		Queue<AreaCutData> areasToCut = new LinkedList<AreaCutData>();
		Collection<Area> finishedAreas = new ArrayList<Area>(innerPolygons.size());
		
		// create a list of Area objects from the outerPolygon (clipped to the bounding box)
		List<Area> outerAreas = createAreas(outerPolygon, true);
		
		// create the inner areas
		List<Area> innerAreas = new ArrayList<Area>(innerPolygons.size()+2);
		for (Way innerPolygon : innerPolygons) {
			// don't need to clip to the bounding box because 
			// these polygons are just used to cut out holes
			innerAreas.addAll(createAreas(innerPolygon, false));
		}

		// initialize the cut data queue
		if (innerAreas.isEmpty()) {
			// this is a multipolygon without any inner areas
			// nothing to cut
			finishedAreas.addAll(outerAreas);
		} else if (outerAreas.size() == 1) {
			// there is one outer area only
			// it is checked before that all inner areas are inside this outer area
			AreaCutData initialCutData = new AreaCutData();
			initialCutData.outerArea = outerAreas.get(0);
			initialCutData.innerAreas = innerAreas;
			areasToCut.add(initialCutData);
		} else {
			// multiple outer areas
			for (Area outerArea : outerAreas) {
				AreaCutData initialCutData = new AreaCutData();
				initialCutData.outerArea = outerArea;
				initialCutData.innerAreas = new ArrayList<Area>(innerAreas
						.size());
				for (Area innerArea : innerAreas) {
					if (outerArea.getBounds().intersects(
						innerArea.getBounds())) {
						initialCutData.innerAreas.add(innerArea);
					}
				}
				
				if (initialCutData.innerAreas.isEmpty()) {
					// this is either an error
					// or the outer area has been cut into pieces on the tile bounds
					finishedAreas.add(outerArea);
				} else {
					areasToCut.add(initialCutData);
				}
			}
		}

		while (areasToCut.isEmpty() == false) {
			AreaCutData areaCutData = areasToCut.poll();
			CutPoint cutPoint = calcNextCutPoint(areaCutData);
			
			if (cutPoint == null) {
				finishedAreas.add(areaCutData.outerArea);
				continue;
			}
			
			assert cutPoint.getNumberOfAreas() > 0 : "Number of cut areas == 0 in mp "+getId();
			
			// cut out the holes
			for (Area cutArea : cutPoint.getAreas()) {
				areaCutData.outerArea.subtract(cutArea);
			}
			
			if (areaCutData.outerArea.isEmpty()) {
				// this outer area space can be abandoned
				continue;
			} 
			
			// the inner areas of the cut point have been processed
			// they are no longer needed
			areaCutData.innerAreas.removeAll(cutPoint.getAreas());

			if (areaCutData.outerArea.isSingular()) {
				// the area is singular
				// => no further splits necessary
				if (areaCutData.innerAreas.isEmpty()) {
					// this area is finished and needs no further cutting
					finishedAreas.add(areaCutData.outerArea);
				} else {
					// readd this area to further processing
					areasToCut.add(areaCutData);
				}
			} else {
				// we need to cut the area into two halfs to get singular areas
				Rectangle r1 = cutPoint.getCutRectangleForArea(areaCutData.outerArea, true);
				Rectangle r2 = cutPoint.getCutRectangleForArea(areaCutData.outerArea, false);

				// Now find the intersection of these two boxes with the
				// original polygon. This will make two new areas, and each
				// area will be one (or more) polygons.
				Area a1 = areaCutData.outerArea;
				Area a2 = (Area) a1.clone();
				a1.intersect(new Area(r1));
				a2.intersect(new Area(r2));

				if (areaCutData.innerAreas.isEmpty()) {
					finishedAreas.addAll(areaToSingularAreas(a1));
					finishedAreas.addAll(areaToSingularAreas(a2));
				} else {
					ArrayList<Area> cuttedAreas = new ArrayList<Area>();
					cuttedAreas.addAll(areaToSingularAreas(a1));
					cuttedAreas.addAll(areaToSingularAreas(a2));
					
					for (Area nextOuterArea : cuttedAreas) {
						ArrayList<Area> nextInnerAreas = null;
						// go through all remaining inner areas and check if they
						// must be further processed with the nextOuterArea 
						for (Area nonProcessedInner : areaCutData.innerAreas) {
							if (nextOuterArea.intersects(nonProcessedInner.getBounds2D())) {
								if (nextInnerAreas == null) {
									nextInnerAreas = new ArrayList<Area>();
								}
								nextInnerAreas.add(nonProcessedInner);
							}
						}
						
						if (nextInnerAreas == null || nextInnerAreas.isEmpty()) {
							finishedAreas.add(nextOuterArea);
						} else {
							AreaCutData outCutData = new AreaCutData();
							outCutData.outerArea = nextOuterArea;
							outCutData.innerAreas= nextInnerAreas;
							areasToCut.add(outCutData);
						}
					}
				}
			}
			
		}
		
		// convert the java.awt.geom.Area back to the mkgmap way
		List<Way> cuttedOuterPolygon = new ArrayList<Way>(finishedAreas.size());
		for (Area area : finishedAreas) {
			Way w = singularAreaToWay(area, FakeIdGenerator.makeFakeId());
			if (w != null) {
				w.copyTags(outerPolygon);
				cuttedOuterPolygon.add(w);
				if (log.isDebugEnabled()) {
					log.debug("Way", outerPolygon.getId(), "splitted to way", w.getId());
				}
			}
		}

		return cuttedOuterPolygon;
	}

	/**
	 * Convert an area that may contains multiple areas to a list of singular
	 * areas
	 * 
	 * @param area
	 *            an area
	 * @return list of singular areas
	 */
	private List<Area> areaToSingularAreas(Area area) {
		if (area.isEmpty()) {
			return Collections.emptyList();
		} else if (area.isSingular()) {
			return Collections.singletonList(area);
		} else {
			List<Area> singularAreas = new ArrayList<Area>();

			// all ways in the area MUST define outer areas
			// it is not possible that one of the areas define an inner segment

			float[] res = new float[6];
			PathIterator pit = area.getPathIterator(null);
			float[] prevPoint = new float[6];

			Polygon p = null;
			while (!pit.isDone()) {
				int type = pit.currentSegment(res);

				switch (type) {
				case PathIterator.SEG_LINETO:
					if (Arrays.equals(res, prevPoint) == false) {
						p.addPoint(Math.round(res[0]), Math.round(res[1]));
					}
					break;
				case PathIterator.SEG_CLOSE:
					p.addPoint(p.xpoints[0], p.ypoints[0]);
					Area a = new Area(p);
					if (a.isEmpty() == false) {
						singularAreas.add(a);
					}
					p = null;
					break;
				case PathIterator.SEG_MOVETO:
					if (p != null) {
						Area a2 = new Area(p);
						if (a2.isEmpty() == false) {
							singularAreas.add(a2);
						}
					}
					p = new Polygon();
					p.addPoint(Math.round(res[0]), Math.round(res[1]));
					break;
				default:
					log.warn(toBrowseURL(), "Unsupported path iterator type"
							+ type, ". This is an mkgmap error.");
				}

				System.arraycopy(res, 0, prevPoint, 0, 6);
				pit.next();
			}
			return singularAreas;
		}
	}

	/**
	 * Create a polygon from a list of points.
	 * 
	 * @param points
	 *            list of points
	 * @return the polygon
	 */
	private Polygon createPolygon(List<Coord> points) {
		Polygon polygon = new Polygon();
		for (Coord co : points) {
			polygon.addPoint(co.getLongitude(), co.getLatitude());
		}
		return polygon;
	}

	/**
	 * Create the areas that are enclosed by the way. Usually the result should
	 * only be one area but some ways contain intersecting lines. To handle these
	 * erroneous cases properly the method might return a list of areas.
	 * 
	 * @param w a closed way
	 * @param clipBbox true if the areas should be clipped to the bounding box; false else
	 * @return a list of enclosed ares
	 */
	private List<Area> createAreas(Way w, boolean clipBbox) {
		Area area = new Area(createPolygon(w.getPoints()));
		if (clipBbox && bboxArea.contains(area.getBounds())==false) {
			// the area intersects the bounding box => clip it
			area.intersect(bboxArea);
		}
		List<Area> areaList = areaToSingularAreas(area);
		if (log.isDebugEnabled()) {
			log.debug("Bbox clipped way",w.getId()+"=>",areaList.size(),"distinct area(s).");
		}
		return areaList;
	}

	/**
	 * Convert an area to an mkgmap way. The caller must ensure that the area is singular.
	 * Otherwise only the first part of the area is converted.
	 * 
	 * @param area
	 *            the area
	 * @param wayId
	 *            the wayid for the new way
	 * @return a new mkgmap way
	 */
	private Way singularAreaToWay(Area area, long wayId) {
		if (area.isEmpty()) {
			if (log.isDebugEnabled()) {
				log.debug("Empty area "+wayId+".", toBrowseURL());
			}
			return null;
		}

		Way w = null;

		float[] res = new float[6];
		PathIterator pit = area.getPathIterator(null);

		while (!pit.isDone()) {
			int type = pit.currentSegment(res);

			switch (type) {
			case PathIterator.SEG_MOVETO:
				w = new Way(wayId);
				w.addPoint(new Coord(Math.round(res[1]), Math.round(res[0])));
				break;
			case PathIterator.SEG_LINETO:
				w.addPoint(new Coord(Math.round(res[1]), Math.round(res[0])));
				break;
			case PathIterator.SEG_CLOSE:
				w.addPoint(w.getPoints().get(0));
				return w;
			default:
				log.warn(toBrowseURL(),
						"Unsupported path iterator type" + type,
						". This is an mkgmap error.");
			}
			pit.next();
		}
		return w;
	}

	private boolean hasPolygonTags(JoinedWay way) {
		for (Way segment : way.getOriginalWays()) {
			if (hasPolygonTags(segment)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasPolygonTags(Element element) {
		for (Map.Entry<String, String> tagEntry : element.getEntryIteratable()) {
			if (polygonTags.contains(tagEntry.getKey())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Creates a matrix which polygon contains which polygon. A polygon does not
	 * contain itself.
	 * 
	 * @param polygonList
	 *            a list of polygons
	 */
	private void createContainsMatrix(List<JoinedWay> polygonList) {
		containsMatrix = new ArrayList<BitSet>();
		for (int i = 0; i < polygonList.size(); i++) {
			containsMatrix.add(new BitSet());
		}

		long t1 = System.currentTimeMillis();

		if (log.isDebugEnabled())
			log.debug("createContainsMatrix listSize:", polygonList.size());

		// use this matrix to check which matrix element has been
		// calculated
		ArrayList<BitSet> finishedMatrix = new ArrayList<BitSet>(polygonList
				.size());

		for (int i = 0; i < polygonList.size(); i++) {
			BitSet matrixRow = new BitSet();
			// a polygon does not contain itself
			matrixRow.set(i);
			finishedMatrix.add(matrixRow);
		}

		for (int rowIndex = 0; rowIndex < polygonList.size(); rowIndex++) {
			JoinedWay potentialOuterPolygon = polygonList.get(rowIndex);
			BitSet containsColumns = containsMatrix.get(rowIndex);
			BitSet finishedCol = finishedMatrix.get(rowIndex);

			if (log.isDebugEnabled())
				log.debug("check polygon", rowIndex);

			// get all non calculated columns of the matrix
			for (int colIndex = finishedCol.nextClearBit(0); colIndex >= 0
					&& colIndex < polygonList.size(); colIndex = finishedCol
					.nextClearBit(colIndex + 1)) {

				JoinedWay innerPolygon = polygonList.get(colIndex);

				if (potentialOuterPolygon.getBounds().intersects(
					innerPolygon.getBounds()) == false) {
					// both polygons do not intersect
					// we can flag both matrix elements as finished
					finishedMatrix.get(colIndex).set(rowIndex);
					finishedMatrix.get(rowIndex).set(colIndex);
				} else {
					boolean contains = contains(potentialOuterPolygon,
						innerPolygon);

					if (contains) {
						containsColumns.set(colIndex);

						// we also know that the inner polygon does not contain the
						// outer polygon
						// so we can set the finished bit for this matrix
						// element
						finishedMatrix.get(colIndex).set(rowIndex);

						// additionally we know that the outer polygon contains all
						// polygons that are contained by the inner polygon
						containsColumns.or(containsMatrix.get(colIndex));
						finishedCol.or(containsColumns);
					}
				}
				// this matrix element is calculated now
				finishedCol.set(colIndex);
			}
		}

		if (log.isDebugEnabled()) {
			long t2 = System.currentTimeMillis();
			log.debug("createMatrix for", polygonList.size(), "polygons took",
				(t2 - t1), "ms");

			log.debug("Containsmatrix");
			for (BitSet b : containsMatrix) {
				log.debug(b);
			}
		}
	}

	/**
	 * Checks if the polygon with polygonIndex1 contains the polygon with polygonIndex2.
	 * 
	 * @return true if polygon(polygonIndex1) contains polygon(polygonIndex2)
	 */
	private boolean contains(int polygonIndex1, int polygonIndex2) {
		return containsMatrix.get(polygonIndex1).get(polygonIndex2);
	}

	/**
	 * Checks if polygon1 contains polygon2.
	 * 
	 * @param polygon1
	 *            a closed way
	 * @param polygon2
	 *            a 2nd closed way
	 * @return true if polygon1 contains polygon2
	 */
	private boolean contains(JoinedWay polygon1, JoinedWay polygon2) {
		if (polygon1.isClosed() == false) {
			return false;
		}
		// check if the bounds of polygon2 are completely inside/enclosed the bounds
		// of polygon1
		if (polygon1.getBounds().contains(polygon2.getBounds()) == false) {
			return false;
		}

		Polygon p = createPolygon(polygon1.getPoints());
		// check first if one point of polygon2 is in polygon1

		// ignore intersections outside the bounding box
		// so it is necessary to check if there is at least one
		// point of polygon2 in polygon1 ignoring all points outside the bounding box
		boolean onePointContained = false;
		boolean allOnLine = true;
		for (Coord px : polygon2.getPoints()) {
			if (p.contains(px.getLongitude(), px.getLatitude())) {
				// there's one point that is in polygon1 and in the bounding
				// box => polygon1 may contain polygon2
				onePointContained = true;
				if (locatedOnLine(px, polygon1.getPoints()) == false) {
					allOnLine = false;
					break;
				}
			} else if (bbox.contains(px)) {
				// we have to check if the point is on one line of the polygon1
				
				if (locatedOnLine(px, polygon1.getPoints()) == false) {
					// there's one point that is not in polygon1 but inside the
					// bounding box => polygon1 does not contain polygon2
					allOnLine = false;
					return false;
				} 
			}
		}
		
		if (allOnLine) {
			onePointContained = false;
			// all points of polygon2 lie on lines of polygon1
			// => the middle of each line polygon must NOT lie outside polygon1
			ArrayList<Coord> middlePoints2 = new ArrayList<Coord>(polygon2.getPoints().size());
			Coord p1 = null;
			for (Coord p2 : polygon2.getPoints()) {
				if (p1 != null) {
					int mLat = p1.getLatitude()+(int)Math.round((p2.getLatitude()-p1.getLatitude())/2d);
					int mLong = p1.getLongitude()+(int)Math.round((p2.getLongitude()-p1.getLongitude())/2d);
					Coord pm = new Coord(mLat, mLong);
					middlePoints2.add(pm);
				}
				p1 = p2;
			}
			
			for (Coord px : middlePoints2) {
				if (p.contains(px.getLongitude(), px.getLatitude())) {
					// there's one point that is in polygon1 and in the bounding
					// box => polygon1 may contain polygon2
					onePointContained = true;
					break;
				} else if (bbox.contains(px)) {
					// we have to check if the point is on one line of the polygon1
					
					if (locatedOnLine(px, polygon1.getPoints()) == false) {
						// there's one point that is not in polygon1 but inside the
						// bounding box => polygon1 does not contain polygon2
						return false;
					} 
				}
			}			
		}

		if (onePointContained == false) {
			// no point of polygon2 is in polygon1 => polygon1 does not contain polygon2
			return false;
		}
		
		Iterator<Coord> it1 = polygon1.getPoints().iterator();
		Coord p1_1 = it1.next();
		Coord p1_2 = null;

		while (it1.hasNext()) {
			p1_2 = p1_1;
			p1_1 = it1.next();

			if (polygon2.linePossiblyIntersectsWay(p1_1, p1_2) == false) {
				// don't check it - this segment of the outer polygon
				// definitely does not intersect the way
				continue;
			}

			int lonMin = Math.min(p1_1.getLongitude(), p1_2.getLongitude());
			int lonMax = Math.max(p1_1.getLongitude(), p1_2.getLongitude());
			int latMin = Math.min(p1_1.getLatitude(), p1_2.getLatitude());
			int latMax = Math.max(p1_1.getLatitude(), p1_2.getLatitude());

			// check all lines of way1 and way2 for intersections
			Iterator<Coord> it2 = polygon2.getPoints().iterator();
			Coord p2_1 = it2.next();
			Coord p2_2 = null;

			// for speedup we divide the area around the second line into
			// a 3x3 matrix with lon(-1,0,1) and lat(-1,0,1).
			// -1 means below min lon/lat of bbox line p1_1-p1_2
			// 0 means inside the bounding box of the line p1_1-p1_2
			// 1 means above max lon/lat of bbox line p1_1-p1_2
			int lonField = p2_1.getLongitude() < lonMin ? -1 : p2_1
					.getLongitude() > lonMax ? 1 : 0;
			int latField = p2_1.getLatitude() < latMin ? -1 : p2_1
					.getLatitude() > latMax ? 1 : 0;

			int prevLonField = lonField;
			int prevLatField = latField;

			while (it2.hasNext()) {
				p2_2 = p2_1;
				p2_1 = it2.next();

				int changes = 0;
				// check if the field of the 3x3 matrix has changed
				if ((lonField >= 0 && p1_1.getLongitude() < lonMin)
						|| (lonField <= 0 && p1_1.getLongitude() > lonMax)) {
					changes++;
					lonField = p1_1.getLongitude() < lonMin ? -1 : p1_1
							.getLongitude() > lonMax ? 1 : 0;
				}
				if ((latField >= 0 && p1_1.getLatitude() < latMin)
						|| (latField <= 0 && p1_1.getLatitude() > latMax)) {
					changes++;
					latField = p1_1.getLatitude() < latMin ? -1 : p1_1
							.getLatitude() > latMax ? 1 : 0;
				}

				// an intersection is possible if
				// latField and lonField has changed
				// or if we come from or go to the inner matrix field
				boolean intersectionPossible = (changes == 2)
						|| (latField == 0 && lonField == 0)
						|| (prevLatField == 0 && prevLonField == 0);

				boolean intersects = intersectionPossible
					&& linesCutEachOther(p1_1, p1_2, p2_1, p2_2);
				
				if (intersects) {
					if ((polygon1.isClosedArtificially() && it1.hasNext() == false)
							|| (polygon2.isClosedArtificially() && it2.hasNext() == false)) {
						// don't care about this intersection
						// one of the polygons is closed by this mp code and the
						// closing segment causes the intersection
						log.info("Polygon", polygon1, "may contain polygon", polygon2,
							". Ignoring artificial generated intersection.");
					} else if ((bbox.contains(p1_1) == false)
							|| (bbox.contains(p1_2) == false)
							|| (bbox.contains(p2_1) == false)
							|| (bbox.contains(p2_2) == false)) {
						// at least one point is outside the bounding box
						// we ignore the intersection because the ways may not
						// be complete
						// due to removals of the tile splitter or osmosis
						log.info("Polygon", polygon1, "may contain polygon", polygon2,
							". Ignoring because at least one point is outside the bounding box.");
					} else {
						// store them in the intersection polygons set
						// the error message will be printed out in the end of
						// the mp handling
						intersectingPolygons.add(polygon1);
						intersectingPolygons.add(polygon2);
						return false;
					}
				}

				prevLonField = lonField;
				prevLatField = latField;
			}
		}

		// don't have any intersection
		// => polygon1 contains polygon2
		return true;
	}

	/**
	 * Checks if the point p is located on one line of the given points.
	 * @param p a point
	 * @param points a list of points; all consecutive points are handled as lines
	 * @return true if p is located on one line given by points
	 */
	private boolean locatedOnLine(Coord p, List<Coord> points) {
		Coord cp1 = null;
		for (Coord cp2 : points) {
			if (p.equals(cp2)) {
				return true;
			}

			try {
				if (cp1 == null) {
					// first init
					continue;
				}

				if (p.getLongitude() < Math.min(cp1.getLongitude(), cp2
						.getLongitude())) {
					continue;
				}
				if (p.getLongitude() > Math.max(cp1.getLongitude(), cp2
						.getLongitude())) {
					continue;
				}
				if (p.getLatitude() < Math.min(cp1.getLatitude(), cp2
						.getLatitude())) {
					continue;
				}
				if (p.getLatitude() > Math.max(cp1.getLatitude(), cp2
						.getLatitude())) {
					continue;
				}

				double dist = Line2D.ptSegDistSq(cp1.getLongitude(), cp1
						.getLatitude(), cp2.getLongitude(), cp2.getLatitude(),
					p.getLongitude(), p.getLatitude());

				if (dist <= OVERLAP_TOLERANCE_DISTANCE) {
					log.debug("Point", p, "is located on line between", cp1, "and",
						cp2, ". Distance:", dist);
					return true;
				}
			} finally {
				cp1 = cp2;
			}
		}
		return false;
	}
	
	/**
	 * Check if the line p1_1 to p1_2 cuts line p2_1 to p2_2 in two pieces and vice versa.
	 * This is a form of intersection check where it is allowed that one line ends on the
	 * other line or that the two lines overlap.
	 * @param p1_1 first point of line 1
	 * @param p1_2 second point of line 1
	 * @param p2_1 first point of line 2
	 * @param p2_2 second point of line 2
	 * @return true if both lines intersect somewhere in the middle of each other
	 */
	private boolean linesCutEachOther(Coord p1_1, Coord p1_2, Coord p2_1,
			Coord p2_2) {
		int width1 = p1_2.getLongitude() - p1_1.getLongitude();
		int width2 = p2_2.getLongitude() - p2_1.getLongitude();

		int height1 = p1_2.getLatitude() - p1_1.getLatitude();
		int height2 = p2_2.getLatitude() - p2_1.getLatitude();

		int denominator = ((height2 * width1) - (width2 * height1));
		if (denominator == 0) {
			// the lines are parallel
			// they might overlap but this is ok for this test
			return false;
		}
		
		int x1Mx3 = p1_1.getLongitude() - p2_1.getLongitude();
		int y1My3 = p1_1.getLatitude() - p2_1.getLatitude();

		double isx = (double)((width2 * y1My3) - (height2 * x1Mx3))
				/ denominator;
		if (isx < 0 || isx > 1) {
			return false;
		}
		
		double isy = (double)((width1 * y1My3) - (height1 * x1Mx3))
				/ denominator;

		if (isy < 0 || isy > 1) {
			return false;
		} 

		return false;
	}

	private List<JoinedWay> getWaysFromPolygonList(BitSet selection) {
		if (selection.isEmpty()) {
			return Collections.emptyList();
		}
		List<JoinedWay> wayList = new ArrayList<JoinedWay>(selection
				.cardinality());
		for (int i = selection.nextSetBit(0); i >= 0; i = selection.nextSetBit(i + 1)) {
			wayList.add(polygons.get(i));
		}
		return wayList;
	}

	private void logWayURLs(Level level, String preMsg, Way way) {
		if (log.isLoggable(level)) {
			if (way instanceof JoinedWay) {
				if (((JoinedWay) way).getOriginalWays().isEmpty()) {
					log.warn("Way", way, "does not contain any original ways");
				}
				for (Way segment : ((JoinedWay) way).getOriginalWays()) {
					if (preMsg == null || preMsg.length() == 0) {
						log.log(level, segment.toBrowseURL());
					} else {
						log.log(level, preMsg, segment.toBrowseURL());
					}
				}
			} else {
				if (preMsg == null || preMsg.length() == 0) {
					log.log(level, way.toBrowseURL());
				} else {
					log.log(level, preMsg, way.toBrowseURL());
				}
			}
		}
	}

	/**
	 * This is a helper class that stores that gives access to the original
	 * segments of a joined way.
	 */
	private static class JoinedWay extends Way {
		private final List<Way> originalWays;
		private boolean closedArtificially = false;

		public int minLat;
		public int maxLat;
		public int minLon;
		public int maxLon;
		private Rectangle bounds = null;

		public JoinedWay(Way originalWay) {
			super(FakeIdGenerator.makeFakeId(), new ArrayList<Coord>(
					originalWay.getPoints()));
			this.originalWays = new ArrayList<Way>();
			addWay(originalWay);

			// we have to initialize the min/max values
			Coord c0 = originalWay.getPoints().get(0);
			minLat = maxLat = c0.getLatitude();
			minLon = maxLon = c0.getLongitude();

			updateBounds(originalWay.getPoints());
		}

		public void addPoint(int index, Coord point) {
			getPoints().add(index, point);
			updateBounds(point);
		}

		public void addPoint(Coord point) {
			super.addPoint(point);
			updateBounds(point);
		}

		private void updateBounds(List<Coord> pointList) {
			for (Coord c : pointList) {
				updateBounds(c);
			}
		}

		private void updateBounds(Coord point) {
			if (point.getLatitude() < minLat) {
				minLat = point.getLatitude();
				bounds = null;
			} else if (point.getLatitude() > maxLat) {
				maxLat = point.getLatitude();
				bounds = null;
			}

			if (point.getLongitude() < minLon) {
				minLon = point.getLongitude();
				bounds = null;
			} else if (point.getLongitude() > maxLon) {
				maxLon = point.getLongitude();
				bounds = null;
			}

		}

		public Rectangle getBounds() {
			if (bounds == null) {
				// note that we increase the rectangle by 1 because intersects
				// checks
				// only the interior
				bounds = new Rectangle(minLon - 1, minLat - 1, maxLon - minLon
						+ 2, maxLat - minLat + 2);
			}

			return bounds;
		}

		public boolean linePossiblyIntersectsWay(Coord p1, Coord p2) {
			return getBounds().intersectsLine(p1.getLongitude(),
					p1.getLatitude(), p2.getLongitude(), p2.getLatitude());
		}

		public void addWay(Way way) {
			if (way instanceof JoinedWay) {
				for (Way w : ((JoinedWay) way).getOriginalWays()) {
					addWay(w);
				}
			} else {
				if (log.isDebugEnabled()) {
					log.debug("Joined", this.getId(), "with", way.getId());
				}
				this.originalWays.add(way);
				addTagsOf(way);
				if (getName() == null && way.getName() != null) {
					setName(way.getName());
				}
			}
		}

		public void closeWayArtificially() {
			addPoint(getPoints().get(0));
			closedArtificially = true;
		}

		public boolean isClosedArtificially() {
			return closedArtificially;
		}

		private void addTagsOf(Way way) {
			for (Map.Entry<String, String> tag : way.getEntryIteratable()) {
				if (getTag(tag.getKey()) == null) {
					addTag(tag.getKey(), tag.getValue());
				}
			}
		}

		public List<Way> getOriginalWays() {
			return originalWays;
		}

		public void removeAllTagsDeep() {
			removeOriginalTags();
			removeAllTags();
		}

		public void removeOriginalTags() {
			for (Way w : getOriginalWays()) {
				if (w instanceof JoinedWay) {
					((JoinedWay) w).removeAllTagsDeep();
				} else {
					w.removeAllTags();
				}
			}
		}

		public String toString() {
			StringBuilder sb = new StringBuilder(200);
			sb.append(getId());
			sb.append("(");
			sb.append(getPoints().size());
			sb.append("P : (");
			boolean first = true;
			for (Way w : getOriginalWays()) {
				if (first) {
					first = false;
				} else {
					sb.append(",");
				}
				sb.append(w.getId());
				sb.append("[");
				sb.append(w.getPoints().size());
				sb.append("P]");
			}
			sb.append(")");
			return sb.toString();
		}
	}

	private static class PolygonStatus {
		final boolean outer;
		final int index;
		final JoinedWay polygon;

		public PolygonStatus(boolean outer, int index, JoinedWay polygon) {
			this.outer = outer;
			this.index = index;
			this.polygon = polygon;
		}
	}

	private static class AreaCutData {
		Area outerArea;
		List<Area> innerAreas;
	}

	private static class CutPoint implements Comparable<CutPoint>{
		int startPoint = Integer.MAX_VALUE;
		int stopPoint = Integer.MIN_VALUE;
		TreeSet<Area> areas;
		private final CoordinateAxis axis;

		public CutPoint(CoordinateAxis axis) {
			this.axis = axis;
			this.areas = new TreeSet<Area>(
					(axis == CoordinateAxis.LONGITUDE ? COMP_LONG_STOP : COMP_LAT_STOP));
		}
		
		public CutPoint duplicate() {
			CutPoint newCutPoint = new CutPoint(this.axis);
			newCutPoint.areas.addAll(areas);
			newCutPoint.startPoint = startPoint;
			newCutPoint.stopPoint = stopPoint;
			return newCutPoint;
		}

		public int getCutPoint() {
			return startPoint + (stopPoint - startPoint) / 2;
		}

		public Rectangle getCutRectangleForArea(Area toCut, boolean firstRect) {
			Rectangle areaRect = toCut.getBounds();
			if (axis == CoordinateAxis.LONGITUDE) {
				int newWidth = getCutPoint()-areaRect.x;
				if (firstRect) {
					return new Rectangle(areaRect.x, areaRect.y, newWidth, areaRect.height); 
				} else {
					return new Rectangle(areaRect.x+newWidth, areaRect.y, areaRect.width-newWidth, areaRect.height); 
				}
			} else {
				int newHeight = getCutPoint()-areaRect.y;
				if (firstRect) {
					return new Rectangle(areaRect.x, areaRect.y, areaRect.width, newHeight); 
				} else {
					return new Rectangle(areaRect.x, areaRect.y+newHeight, areaRect.width, areaRect.height-newHeight); 
				}
			}
		}
		
		public Collection<Area> getAreas() {
			return areas;
		}

		public void addArea(Area area) {
			// remove all areas that do not overlap with the new area
			while (areas.isEmpty() == false
					&& axis.getStop(areas.first()) < axis
							.getStart(area)) {
				// remove the first area
				areas.pollFirst();
			}

			areas.add(area);
			startPoint = axis.getStart(Collections.max(areas,
				(axis == CoordinateAxis.LONGITUDE ? COMP_LONG_START
						: COMP_LAT_START)));
			stopPoint = axis.getStop(areas.first());
		}

		public int getNumberOfAreas() {
			return this.areas.size();
		}

		public int compareTo(CutPoint o) {
			if (this == o) {
				return 0;
			}
			int ndiff = getNumberOfAreas()-o.getNumberOfAreas();
			if (ndiff != 0) {
				return ndiff;
			}
			// prefer the larger area that is splitted
			return (stopPoint-startPoint)-(o.stopPoint-o.startPoint); 
		}

		public String toString() {
			return axis +" "+getNumberOfAreas()+" "+startPoint+" "+stopPoint+" "+getCutPoint();
		}
		
	}

	private static enum CoordinateAxis {
		LATITUDE(false), LONGITUDE(true);

		private CoordinateAxis(boolean useX) {
			this.useX = useX;
		}

		private final boolean useX;

		public int getStart(Area area) {
			return getStart(area.getBounds());
		}

		public int getStart(Rectangle rect) {
			return (useX ? rect.x : rect.y);
		}

		public int getStop(Area area) {
			return getStop(area.getBounds());
		}

		public int getStop(Rectangle rect) {
			return (useX ? rect.x + rect.width : rect.y + rect.height);
		}

	}

	private static final AreaComparator COMP_LONG_START = new AreaComparator(
			true, CoordinateAxis.LONGITUDE);
	private static final AreaComparator COMP_LONG_STOP = new AreaComparator(
			false, CoordinateAxis.LONGITUDE);
	private static final AreaComparator COMP_LAT_START = new AreaComparator(
			true, CoordinateAxis.LATITUDE);
	private static final AreaComparator COMP_LAT_STOP = new AreaComparator(
			false, CoordinateAxis.LATITUDE);

	private static class AreaComparator implements Comparator<Area> {

		private final CoordinateAxis axis;
		private final boolean startPoint;

		public AreaComparator(boolean startPoint, CoordinateAxis axis) {
			this.startPoint = startPoint;
			this.axis = axis;
		}

		public int compare(Area o1, Area o2) {
			if (o1 == o2) {
				return 0;
			}

			if (startPoint) {
				int cmp = axis.getStart(o1) - axis.getStart(o2);
				if (cmp == 0) {
					return axis.getStop(o1) - axis.getStop(o2);
				} else {
					return cmp;
				}
			} else {
				int cmp = axis.getStop(o1) - axis.getStop(o2);
				if (cmp == 0) {
					return axis.getStart(o1) - axis.getStart(o2);
				} else {
					return cmp;
				}
			}
		}

	}
}
