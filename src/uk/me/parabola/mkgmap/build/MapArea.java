/*
 * Copyright (C) 2007 Steve Ratcliffe
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Steve Ratcliffe
 * Create date: 21-Jan-2007
 */
package uk.me.parabola.mkgmap.build;

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.trergn.Overview;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.filters.FilterConfig;
import uk.me.parabola.mkgmap.filters.LineSizeSplitterFilter;
import uk.me.parabola.mkgmap.filters.MapFilterChain;
import uk.me.parabola.mkgmap.filters.PolygonSizeSplitterFilter;
import uk.me.parabola.mkgmap.general.MapDataSource;
import uk.me.parabola.mkgmap.general.MapElement;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.general.RoadNetwork;

/**
 * A sub area of the map.  We have to divide the map up into areas to meet the
 * format of the Garmin map.  This class holds all the map elements that belong
 * to a particular area and provides a way of splitting areas into smaller ones.
 *
 * It also acts as a map data source so that we can derive lower level
 * areas from it.
 *
 * @author Steve Ratcliffe
 */
public class MapArea implements MapDataSource {
	private static final Logger log = Logger.getLogger(MapArea.class);

	private static final int INITIAL_CAPACITY = 100;
	private static final int MAX_RESOLUTION = 24;

	private static final int POINT_KIND = 0;
	private static final int LINE_KIND = 1;
	private static final int SHAPE_KIND = 2;

	// This is the initial area.
	private final Area bounds;

	// Because ways may extend beyond the bounds, we keep track of the actual
	// bounding box here.
	private int minLat = Integer.MAX_VALUE;
	private int minLon = Integer.MAX_VALUE;
	private int maxLat = Integer.MIN_VALUE;
	private int maxLon = Integer.MIN_VALUE;

	// The contents of the area.
	private final List<MapPoint> points = new ArrayList<MapPoint>(INITIAL_CAPACITY);
	private final List<MapLine> lines = new ArrayList<MapLine>(INITIAL_CAPACITY);
	private final List<MapShape> shapes = new ArrayList<MapShape>(INITIAL_CAPACITY);

	// Counts of features available at different resolutions.
	private final int[] pointSize = new int[MAX_RESOLUTION+1];
	private final int[] lineSize = new int[MAX_RESOLUTION+1];
	private final int[] shapeSize = new int[MAX_RESOLUTION+1];
	private final int[] elemCounts = new int[MAX_RESOLUTION+1];

	private int nActivePoints;
	private int nActiveIndPoints;
	private int nActiveLines;
	private int nActiveShapes;

	/** The resolution that this area is at */
	private final int areaResolution;

	/**
	 * Create a map area from the given map data source.  This map
	 * area will have the same bounds as the map data source and
	 * will contain all the same map elements.
	 *
	 * @param src The map data source to initialise this area with.
	 * @param resolution The resolution of this area.
	 */
	public MapArea(MapDataSource src, int resolution) {
		this.areaResolution = 0;
		this.bounds = src.getBounds();
		addToBounds(bounds);

		for (MapPoint p : src.getPoints()) {
			points.add(p);
			addSize(p, pointSize, POINT_KIND);
		}
		addLines(src, resolution);
		addPolygons(src, resolution);
	}

	/**
	 * Add the polygons, making sure that they are not too big.
	 * @param src The map data.
	 * @param resolution The resolution of this layer.
	 */
	private void addPolygons(MapDataSource src, int resolution) {
		MapFilterChain chain = new MapFilterChain() {
			public void doFilter(MapElement element) {
				MapShape shape = (MapShape) element;

				shapes.add(shape);
				addSize(element, shapeSize, SHAPE_KIND);
			}

			public void addElement(MapElement element) {
				doFilter(element);
			}
		};

		PolygonSizeSplitterFilter filter = new PolygonSizeSplitterFilter();
		FilterConfig config = new FilterConfig();
		config.setResolution(resolution);
		config.setBounds(bounds);
		filter.init(config);

		for (MapShape s : src.getShapes()) {
			filter.doFilter(s, chain);
		}
	}

	/**
	 * Add the lines, making sure that they are not too big for resolution
	 * that we are working with.
	 * @param src The map data.
	 * @param resolution The current resolution of the layer.
	 */
	private void addLines(MapDataSource src, int resolution) {
		// Split lines for size, such that it is appropriate for the
		// resolution that it is at.
		MapFilterChain chain = new MapFilterChain() {
			public void doFilter(MapElement element) {
				MapLine line = (MapLine) element;

				lines.add(line);
				addSize(element, lineSize, LINE_KIND);
			}

			public void addElement(MapElement element) {
				doFilter(element);
			}
		};

		LineSizeSplitterFilter filter = new LineSizeSplitterFilter();
		FilterConfig config = new FilterConfig();
		config.setResolution(resolution);
		config.setBounds(bounds);
		filter.init(config);
		for (MapLine l : src.getLines()) {
			filter.doFilter(l, chain);
		}
	}

	/**
	 * Create an map area with the given initial bounds.
	 *
	 * @param area The bounds for this area.
	 * @param res The minimum resolution for this area.
	 */
	private MapArea(Area area, int res) {
		bounds = area;
		areaResolution = res;
		addToBounds(area);
	}

	/**
	 * Split this area into several pieces. All the map elements are reallocated
	 * to the appropriate subarea.  Usually this instance would now be thrown
	 * away and the new sub areas used instead.
	 *
	 * @param nx The number of pieces in the x (longitude) direction.
	 * @param ny The number of pieces in the y direction.
	 * @param resolution The resolution of the level.
	 * @return An array of the new MapArea's.
	 */
	public MapArea[] split(int nx, int ny, int resolution) {
		Area[] areas = bounds.split(nx, ny);
		MapArea[] mapAreas = new MapArea[nx * ny];
		for (int i = 0; i < nx * ny; i++) {
			mapAreas[i] = new MapArea(areas[i], resolution);
			if (log.isDebugEnabled())
				log.debug("area before", mapAreas[i].getBounds());
		}

		int xbase = areas[0].getMinLong();
		int ybase = areas[0].getMinLat();
		int dx = areas[0].getWidth();
		int dy = areas[0].getHeight();

		// Now sprinkle each map element into the correct map area.
		for (MapPoint p : this.points) {
			MapArea area1 = pickArea(mapAreas, p, xbase, ybase, nx, ny, dx, dy);
			area1.addPoint(p);
		}

		for (MapLine l : this.lines) {
			// Drop any zero sized lines.
			if (l.getBounds().getMaxDimention() <= 0)
				continue;

			MapArea area1 = pickArea(mapAreas, l, xbase, ybase, nx, ny, dx, dy);
			area1.addLine(l);
		}

		for (MapShape e : this.shapes) {
			MapArea area1 = pickArea(mapAreas, e, xbase, ybase, nx, ny, dx, dy);
			area1.addShape(e);
		}

		return mapAreas;
	}

	/**
	 * Get the full bounds of this area.  As lines and polylines are
	 * added then may go outside of the intial area.  Whe this happens
	 * we need to increase the size of the area.
	 *
	 * @return The full size required to hold all the included
	 * elements.
	 */
	public Area getFullBounds() {
		return new Area(minLat, minLon, maxLat, maxLon);
	}

	/**
	 * Get an estimate of the size of the RGN space that will be required to
	 * hold the points in this area.  For points we can be fairly accurate, but
	 * we just guess an upper bound for lines and shapes since we don't know the
	 * exact size until later.
	 *
	 * @param res The resolution to test for.  At lower resolutions, there are
	 * less elements so less room is needed.
	 * @return An estimate of the max size that will be needed in the RGN file
	 * for this sub-division.
	 */
	public int getSizeAtResolution(int res) {
		int psize = 0;
		int lsize = 0;
		int ssize = 0;
		
		for (int i = 0; i <= res; i++) {

			psize += pointSize[i];
			lsize += lineSize[i];
			ssize += shapeSize[i];
		}

		// Return the largest one as an overflow of any means that we have to
		// split the area.
		int size = psize;
		if (lsize > size)
			size = lsize;
		if (ssize > size)
			size = ssize;

		return size;
	}

	/**
	 * Get the initial bounds of this area.  That is the initial
	 * bounds before anything was added.
	 *
	 * @return The initial bounds as when it was created.
	 * @see #getFullBounds
	 */
	public Area getBounds() {
		return bounds;
	}

	/**
	 * Get a list of all the points.
	 *
	 * @return The points.
	 */
	public List<MapPoint> getPoints() {
		return points;
	}

	/**
	 * Get a list of all the lines.
	 *
	 * @return The lines.
	 */
	public List<MapLine> getLines() {
		return lines;
	}

	/**
	 * Get a list of all the shapes.
	 *
	 * @return The shapes.
	 */
	public List<MapShape> getShapes() {
		return shapes;
	}

	public RoadNetwork getRoadNetwork() {
		// I don't think this is needed here.
		return null;
	}

	/**
	 * This is not used for areas.
	 * @return Always returns null.
	 */
	public List<Overview> getOverviews() {
		return null;
	}

	/**
	 * True if there are any 'active' points in this area.  Ie ones that will be
	 * shown because their resolution is at least as high as that of the
	 * area.
	 *
	 * @return True if any active points in this area.
	 */
	public boolean hasPoints() {
		return nActivePoints > 0;
	}

	/**
	 * True if there are active indexed points in the area.
	 * @return True if any active indexed points in the area.
	 */
	public boolean hasIndPoints() {
		return nActiveIndPoints > 0;
	}

	/**
	 * True if there are any 'active' points in this area.  Ie ones that will be
	 * shown because their resolution is at least as high as that of the
	 * area.
	 *
	 * @return True if any active points in this area.
	 */
	public boolean hasLines() {
		return nActiveLines > 0;
	}

	/**
	 * Return number of lines in this area.
	 */
	public int getNumLines() {
		return nActiveLines;
	}

	/**
	 * True if there are any 'active' points in this area.  Ie ones that will be
	 * shown because their resolution is at least as high as that of the
	 * area.
	 *
	 * @return True if any active points in this area.
	 */
	public boolean hasShapes() {
		return nActiveShapes > 0;
	}

	/**
	 * Add an estimate of the size that will be required to hold this element
	 * if it should be displayed at the given resolution.  We also keep track
	 * of the number of <i>active</i> elements here ie elements that will be
	 * shown because they are at a resolution at least as great as the resolution
	 * of the area.
	 *
	 * @param p The element containing the minimum resolution that it will be
	 * displayed at.
	 * @param sizes An array of sizes, this routine updates the correct one.
	 * @param kind What kind of element this is KIND_POINT etc.
	 */
	private void addSize(MapElement p, int[] sizes, int kind) {
		int res = p.getMinResolution();
		if (res > MAX_RESOLUTION)
			return;

		int s;
		int n;

		switch (kind) {
		case POINT_KIND:
			if (res <= areaResolution) {
				if(((MapPoint) p).isCity())
					nActiveIndPoints++;
				else
					nActivePoints++;
			}

			// Points are predictibly less than 9 bytes.
			s = 9;
			break;

		case LINE_KIND:
			if (res <= areaResolution)
				nActiveLines++;

			// Estimate the size taken by lines and shapes as a constant plus
			// a factor based on the number of points.
			n = ((MapLine) p).getPoints().size();
			s = 11 + n * 2;
			break;
		case SHAPE_KIND:
			if (res <= areaResolution)
				nActiveShapes++;

			// Estimate the size taken by lines and shapes as a constant plus
			// a factor based on the number of points.
			n = ((MapLine) p).getPoints().size();
			s = 11 + n * 2;
			break;

		default:
			log.error("should not be here");
			assert false;
			s = 0;
			break;
		}

		sizes[res] += s;
		elemCounts[res]++;
	}

	/**
	 * Add a single point to this area.
	 *
	 * @param p The point to add.
	 */
	private void addPoint(MapPoint p) {
		points.add(p);
		addToBounds(p.getBounds());
		addSize(p, pointSize, POINT_KIND);
	}

	/**
	 * Add a single line to this area.
	 *
	 * @param l The line to add.
	 */
	private void addLine(MapLine l) {
		lines.add(l);
		addToBounds(l.getBounds());
		addSize(l, lineSize, LINE_KIND);
	}

	/**
	 * Add a single shape to this map area.
	 *
	 * @param s The shape to add.
	 */
	private void addShape(MapShape s) {
		shapes.add(s);
		addToBounds(s.getBounds());
		addSize(s, shapeSize, SHAPE_KIND);
	}

	/**
	 * Add to the bounds of this area.  That is the new bounds
	 * for this area will cover the existing ones plus the new
	 * area.
	 *
	 * @param a Area to add into this map area.
	 */
	private void addToBounds(Area a) {
		int l = a.getMinLat();
		if (l < minLat)
			minLat = l;
		l = a.getMaxLat();
		if (l > maxLat)
			maxLat = l;

		l = a.getMinLong();
		if (l < minLon)
			minLon = l;
		l = a.getMaxLong();
		if (l > maxLon)
			maxLon = l;
	}

	/**
	 * Out of all the available areas, it picks the one that the map element
	 * should be placed into.
	 *
	 * Since we know how the area is divided (equal sizes) we can work out
	 * which one it fits into without stepping through them all and checking
	 * coordinates.
	 *
	 * @param areas The available areas to choose from.
	 * @param e The map element.
	 * @param xbase The x coord at the origin
	 * @param ybase The y coord of the origin
	 * @param nx number of divisions.
	 * @param ny number of divisions in y.
	 * @param dx The size of each division (x direction)
	 * @param dy The size of each division (y direction)
	 * @return The area from areas where the map element fits.
	 */
	private MapArea pickArea(MapArea[] areas, MapElement e,
			int xbase, int ybase,
			int nx, int ny,
			int dx, int dy)
	{
		int x = e.getLocation().getLongitude();
		int y = e.getLocation().getLatitude();

		int xcell = (x - xbase) / dx;
		int ycell = (y - ybase) / dy;

		if (xcell < 0) {
			log.warn("xcell was", xcell, "x", x, "xbase", xbase);
			xcell = 0;
		}
		if (ycell < 0) {
			log.warn("ycell was", ycell, "y", y, "ybase", ybase);
			ycell = 0;
		}
		
		if (xcell >= nx)
			xcell = nx - 1;
		if (ycell >= ny)
			ycell = ny - 1;

		if (log.isDebugEnabled()) {
			log.debug("adding", e.getLocation(), "to", xcell, "/", ycell,
					areas[xcell * ny + ycell].getBounds());
		}
		return areas[xcell * ny + ycell];
	}
}
