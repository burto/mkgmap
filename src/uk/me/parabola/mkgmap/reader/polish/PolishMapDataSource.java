/*
 * Copyright (C) 2006 Steve Ratcliffe
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
 * Create date: 16-Dec-2006
 */
package uk.me.parabola.mkgmap.reader.polish;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.FormatException;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.trergn.ExtTypeAttributes;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.LevelInfo;
import uk.me.parabola.mkgmap.general.LoadableMapDataSource;
import uk.me.parabola.mkgmap.general.MapElement;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.reader.MapperBasedMapDataSource;


/**
 * Read an data file in Polish format.  This is the format used by a number
 * of other garmin map making programs notably cGPSmapper.
 * <p>
 * As the input format is designed for garmin maps, it is fairly easy to read
 * into mkgmap.  Not every feature of the format is read yet, but it shouldn't
 * be too difficult to add them in as needed.
 * <p>
 * Now will place elements at the level specified in the file and not at the
 * automatic level that is used in eg. the OSM reader.
 *
 * @author Steve Ratcliffe
 */
public class PolishMapDataSource extends MapperBasedMapDataSource implements LoadableMapDataSource {
	private static final Logger log = Logger.getLogger(PolishMapDataSource.class);

	private static final String READING_CHARSET = "UTF-8";

	private static final int S_IMG_ID = 1;
	private static final int S_POINT = 2;
	private static final int S_POLYLINE = 3;
	private static final int S_POLYGON = 4;

	private MapPoint point;
	private MapLine polyline;
	private MapShape shape;

	private final RoadHelper roadHelper = new RoadHelper();

	private Map<String, String> extraAttributes;

	private String copyright;
	private int section;
	private LevelInfo[] levels;
	private int endLevel;
	private char elevUnits;
	private static final double METERS_TO_FEET = 3.2808399;

	private int lineNo;

	// Use to decode labels if they are not in cp1252
	private CharsetDecoder dec;

	public boolean isFileSupported(String name) {
		// Supported if the extension is .mp
		return name.endsWith(".mp") || name.endsWith(".MP") || name.endsWith(".mp.gz");
	}

	/**
	 * Load the .osm file and produce the intermediate format.
	 *
	 * @param name The filename to read.
	 * @throws FileNotFoundException If the file does not exist.
	 */
	public void load(String name) throws FileNotFoundException, FormatException {
		Reader reader;
		try {
			reader = new InputStreamReader(openFile(name), READING_CHARSET);
		} catch (UnsupportedEncodingException e) {
			// Java is required to support iso-8859-1 so this is unlikely
			throw new FormatException("Unrecognised charset " + READING_CHARSET);
		}

		BufferedReader in = new BufferedReader(reader);
		try {
			String line;
			while ((line = in.readLine()) != null) {
				++lineNo;
				if (line.trim().length() == 0 || line.charAt(0) == ';')
					continue;
				if (line.startsWith("[END"))
					endSection();
				else if (line.charAt(0) == '[')
					sectionStart(line);
				else
					processLine(line);
			}
		} catch (IOException e) {
			throw new FormatException("Reading file failed", e);
		}

		addBackground();
	}

	public LevelInfo[] mapLevels() {
		if (levels == null) {
			// If it has not been set then supply some defaults.
			levels = new LevelInfo[] {
					new LevelInfo(3, 17),
					new LevelInfo(2, 18),
					new LevelInfo(1, 22),
					new LevelInfo(0, 24),
			};
		}
		levels[0].setTop(true);
		return levels;
	}

	
	/**
	 * Get the copyright message.  We use whatever was specified inside the
	 * MPF itself.
	 *
	 * @return A string description of the copyright.
	 */
	public String[] copyrightMessages() {
		return new String[] {copyright};
	}

	/**
	 * Record that we are starting a new section.
	 * Section names are enclosed in square brackets.  Inside the section there
	 * are a number of lines with the key=value format.
	 *
	 * @param line The raw line from the input file.
	 */
	private void sectionStart(String line) {
		String name = line.substring(1, line.length() - 1);
		log.debug("section name", name);

		extraAttributes = null;

		if (name.equals("IMG ID")) {
			section = S_IMG_ID;
		} else if (name.equals("POI") || name.equals("RGN10") || name.equals("RGN20")) {
			point = new MapPoint();
			section = S_POINT;
		} else if (name.equals("POLYLINE") || name.equals("RGN40")) {
			polyline = new MapLine();
			roadHelper.clear();
			section = S_POLYLINE;
		} else if (name.equals("POLYGON") || name.equals("RGN80")) {
			shape = new MapShape();
			section = S_POLYGON;
		}
	}

	/**
	 * At the end of a section, we add what ever element that we have been
	 * building to the map.
	 */
	private void endSection() {
		switch (section) {
		case S_POINT:
			if(extraAttributes != null && point.hasExtendedType())
				point.setExtTypeAttributes(makeExtTypeAttributes());
			mapper.addToBounds(point.getLocation());
			mapper.addPoint(point);
			break;
		case S_POLYLINE:
			if (polyline.getPoints() != null) {
				if(extraAttributes != null && polyline.hasExtendedType())
					polyline.setExtTypeAttributes(makeExtTypeAttributes());
				if (roadHelper.isRoad())
					mapper.addRoad(roadHelper.makeRoad(polyline));
				else
					mapper.addLine(polyline);
			}
			break;
		case S_POLYGON:
			if (shape.getPoints() != null) {
				if(extraAttributes != null && shape.hasExtendedType())
					shape.setExtTypeAttributes(makeExtTypeAttributes());
				mapper.addShape(shape);
			}
			break;
		default:
			log.warn("unexpected default in switch", section);
			break;
		}

		// Clear the section state.
		section = 0;
		endLevel = 0;
	}

	/**
	 * This should be a line that is a key value pair.  We switch out to a
	 * routine that is dependant on the section that we are in.
	 *
	 * @param line The raw input line from the file.
	 */
	private void processLine(String line) {
		String[] nameVal = line.split("=", 2);
		if (nameVal.length != 2) {
			log.warn("short line? " + line);
			return;
		}
		String name = nameVal[0];
		String value = nameVal[1];

		log.debug("LINE: ", name, "|", value);
		
		switch (section) {
		case S_IMG_ID:
			imgId(name, value);
			break;
		case S_POINT:
			if (!isCommonValue(point, name, value))
				point(name, value);
			break;
		case S_POLYLINE:
			if (!isCommonValue(polyline, name, value))
				line(name, value);
			break;
		case S_POLYGON:
			if (!isCommonValue(shape, name, value))
				shape(name, value);
			break;
		default:
			log.debug("line ignored");
			break;
		}
	}


	/**
	 * This is called for every line within the POI section.  The lines are
	 * key value pairs that have already been decoded into name and value.
	 * For each name we recognise we set the appropriate property on
	 * the <i>point</i>.
	 *
	 * @param name Parameter name.
	 * @param value Its value.
	 */
	private void point(String name, String value) {
		if (name.equals("Type")) {
			Integer type = Integer.decode(value);
			point.setType(type);
		}  else if (name.startsWith("Data") || name.startsWith("Origin")) {
			Coord co = makeCoord(value);
			setResolution(point, name);
			point.setLocation(co);
		}
		else {
			if(extraAttributes == null)
				extraAttributes = new HashMap<String, String>();
			extraAttributes.put(name, value);
		}
	}

	/**
	 * Called for each command in a POLYLINE section.  There will be a Data
	 * line consisting of a number of co-ordinates that must be separated out
	 * into points.
	 *
	 * @param name Command name.
	 * @param value Command value.
	 * @see #point
	 */
	private void line(String name, String value) {
		if (name.equals("Type")) {
			polyline.setType(Integer.decode(value));
		} else if (name.startsWith("Data")) {
			List<Coord> points = coordsFromString(value);
			// If it is a contour line, then fix the elevation if required.
			if ((polyline.getType() == 0x20) ||
			    (polyline.getType() == 0x21) ||
			    (polyline.getType() == 0x22)) {
				fixElevation();
			}

			setResolution(polyline, name);
			polyline.setPoints(points); // XXX: multiple DATA sections?
		} else if (name.equals("RoadID")) {
			roadHelper.setRoadId(Integer.parseInt(value));
		} else if (name.startsWith("Nod")) {
			int nodIndex = Integer.parseInt(name.substring(3));
			roadHelper.addNode(nodIndex, value);
		} else if (name.equals("RouteParam") || name.equals("RouteParams")) {
			roadHelper.setParam(value);
		} else if (name.equals("DirIndicator")) {
			polyline.setDirection(Integer.parseInt(value) > 0);
		}
		else {
			if(extraAttributes == null)
				extraAttributes = new HashMap<String, String>();
			extraAttributes.put(name, value);
		}
	}

	private List<Coord> coordsFromString(String value) {
		String[] ords = value.split("\\) *, *\\(");
		List<Coord> points = new ArrayList<Coord>();

		for (String s : ords) {
			Coord co = makeCoord(s);
			if (log.isDebugEnabled())
				log.debug(" L: ", co);
			mapper.addToBounds(co);
			points.add(co);
		}
		log.debug(points.size() + " points from " + value);
		return points;
	}

	/**
	 * The elevation needs to be in feet.  So if it is given in meters then
	 * convert it.
	 */
	private void fixElevation() {
		if (elevUnits == 'm') {
			String h = polyline.getName();
			try {
				// Convert to feet.
				int n = Integer.parseInt(h);
				n *= METERS_TO_FEET;
				polyline.setName(String.valueOf(n));

			} catch (NumberFormatException e) {
				// OK it wasn't a number, leave it alone
			}
		}
	}

	/**
	 * Called for each command in a POLYGON section.  There will be a Data
	 * line consisting of a number of co-ordinates that must be separated out
	 * into points.
	 *
	 * @param name Command name.
	 * @param value Command value.
	 * @see #line
	 */
	private void shape(String name, String value) {
		if (name.equals("Type")) {
			shape.setType(Integer.decode(value));
		} else if (name.startsWith("Data")) {
			List<Coord> points = coordsFromString(value);

			shape.setPoints(points);
			setResolution(shape, name);
		}
		else {
			if(extraAttributes == null)
				extraAttributes = new HashMap<String, String>();
			extraAttributes.put(name, value);
		}
	}

	private boolean isCommonValue(MapElement elem, String name, String value) {
		if (name.equals("Label")) {
			elem.setName(unescape(recode(value)));
		} else if (name.equals("Levels") || name.equals("EndLevel") || name.equals("LevelsNumber")) {
			try {
				endLevel = Integer.valueOf(value);
			} catch (NumberFormatException e) {
				endLevel = 0;
			}
		} else if (name.equals("ZipCode")) {
		  elem.setZip(recode(value));
		} else if (name.equals("CityName")) {
		  elem.setCity(recode(value));		  
		} else if (name.equals("StreetDesc")) {
		  elem.setStreet(recode(value));
		} else if (name.equals("HouseNumber")) {
		  elem.setHouseNumber(recode(value));
		} else if (name.equals("is_in")) {
		  elem.setIsIn(recode(value));		  
		} else if (name.equals("Phone")) {
		  elem.setPhone(recode(value));			
		} else if (name.equals("CountryName")) {
		  elem.setCountry(recode(value));
		} else if (name.equals("RegionName")) {
			//System.out.println("RegionName " + value);
		  elem.setRegion(recode(value));				
		} else {
			return false;
		}

		// We dealt with it
		return true;
	}

	/**
	 * Deal with the polish map escape codes of the form ~[0x##].  These
	 * stand for a single character and is usually used for highway
	 * symbols, name separators etc.
	 *
	 * The code ~[0x05] stands for the character \005 for example.
	 * 
	 * @param s The original string that may contain codes.
	 * @return A string with the escape codes replaced by the single character.
	 */
	public static String unescape(String s) {
		int ind = s.indexOf("~[");
		if (ind < 0)
			return s;

		StringBuilder sb = new StringBuilder();
		if (ind > 0)
			sb.append(s.substring(0, ind));

		char[] buf = s.toCharArray();
		while (ind < buf.length) {
			if (ind < buf.length-2 && buf[ind] == '~' && buf[ind+1] == '[') {
				StringBuffer num = new StringBuffer();
				ind += 2; // skip "~["
				while (ind < buf.length && buf[ind++] != ']')
					num.append(buf[ind - 1]);

				try {
					int inum = Integer.decode(num.toString());
					sb.append((char) inum);
				} catch (NumberFormatException e) {
					// Input is malformed so lets just ignore it.
				}
			} else {
				sb.append(buf[ind]);
				ind++;
			}
		}
		return sb.toString();
	}

	/**
	 * Convert the value of a label into a string based on the declared
	 * code page in the file.
	 *
	 * This makes assumptions about the way that the .mp file is written
	 * that may not be correct.
	 *
	 * @param value The string that has been read with ISO-8859-1.
	 * @return A possibly different string that is obtained by taking the
	 * bytes in the input string and decoding them as if they had the
	 * declared code page.
	 */
	private String recode(String value) {
		if (dec != null) {
			try {
				// Get the bytes that were actually in the file.
				byte[] bytes = value.getBytes(READING_CHARSET);
				ByteBuffer buf = ByteBuffer.wrap(bytes);

				// Decode from bytes with the correct code page.
				CharBuffer out = dec.decode(buf);
				return out.toString();
			} catch (UnsupportedEncodingException e) {
				// Java requires this support, so unlikely to happen
				log.warn("no support for " + READING_CHARSET);
			} catch (CharacterCodingException e) {
				log.error("error decoding label", e);
			}
		}
		return value;
	}

	private void setResolution(MapElement elem, String name) {
		if (endLevel > 0) {
			elem.setMinResolution(extractResolution(endLevel));
		    elem.setMaxResolution(extractResolution(name));
		} else {
			int res = extractResolution(name);
			elem.setMinResolution(res);
			elem.setMaxResolution(res);
		}
	}

	/**
	 * Extract the resolution from the Data label.  The name will be something
	 * like Data2: from that we know it is at level 2 and we can look up
	 * the resolution.
	 *
	 * @param name The name tag DataN, where N is a digit corresponding to the
	 * level.
	 *
	 * @return The resolution that corresponds to the level.
	 */
	private int extractResolution(String name) {
		int level = Integer.valueOf(name.substring(name.charAt(0) == 'O'? 6: 4));
		return extractResolution(level);
	}

	/**
	 * Extract resolution from the level.
	 *
	 * @param level The level (0..)
	 * @return The resolution.
	 * @see #extractResolution(String name)
	 */
	private int extractResolution(int level) {
		int nlevels = levels.length;

		LevelInfo li = levels[nlevels - level - 1];
		return li.getBits();
	}


	/**
	 * The initial 'IMG ID' section.  Contains miscellaneous parameters for
	 * the map.
	 *
	 * @param name Command name.
	 * @param value Command value.
	 */
	private void imgId(String name, String value) {
		if (name.equals("Copyright")) {
			copyright = value;
		} else if (name.equals("Levels")) {
			int nlev = Integer.valueOf(value);
			levels = new LevelInfo[nlev];
		} else if (name.startsWith("Level")) {
			int level = Integer.valueOf(name.substring(5));
			int bits = Integer.valueOf(value);
			LevelInfo info = new LevelInfo(level, bits);

			int nlevels = levels.length;
			if (level >= nlevels)
				return;

			levels[nlevels - level - 1] = info;
		} else if (name.startsWith("Elevation")) {
			char fc = value.charAt(0);
			if (fc == 'm' || fc == 'M')
				elevUnits = 'm';
		} else if (name.equals("CodePage")) {
			dec = Charset.forName("cp" + value).newDecoder();
		}
	}

	/**
	 * Create a coordinate from a string.  The string will look similar:
	 * (2.3454,-0.23), but may not have the leading opening parenthesis.
	 * @param value A string representing a lat,long pair.
	 * @return The coordinate value.
	 */
	private Coord makeCoord(String value) {
		String[] fields = value.split("[(,)]");

		int i = 0;
		if (fields[0].length() == 0)
			i = 1;

		Double f1 = Double.valueOf(fields[i]);
		Double f2 = Double.valueOf(fields[i+1]);
		return new Coord(f1, f2);
	}

	private ExtTypeAttributes makeExtTypeAttributes() {
		Map<String, String> eta = new HashMap<String, String>();
		int colour = 0;
		int style = 0;

		for(String key : extraAttributes.keySet()) {
			String v = extraAttributes.get(key);
			if(key.equals("Depth")) {
				String u = extraAttributes.get("DepthUnit");
				if("f".equals(u))
					v += "ft";
				eta.put("depth", v);
			}
			else if(key.equals("Height")) {
				String u = extraAttributes.get("HeightUnit");
				if("f".equals(u))
					v += "ft";
				eta.put("height", v);
			}
			else if(key.equals("HeightAboveFoundation")) {
				String u = extraAttributes.get("HeightAboveFoundationUnit");
				if("f".equals(u))
					v += "ft";
				eta.put("height-above-foundation", v);
			}
			else if(key.equals("HeightAboveDatum")) {
				String u = extraAttributes.get("HeightAboveDatumUnit");
				if("f".equals(u))
					v += "ft";
				eta.put("height-above-datum", v);
			}
			else if(key.equals("Color")) {
				colour = Integer.decode(v);
			}
			else if(key.equals("Style")) {
				style = Integer.decode(v);
			}
			else if(key.equals("Position")) {
				eta.put("position", v);
			}
			else if(key.equals("FoundationColor")) {
				eta.put("color", v);
			}
			else if(key.equals("Light")) {
				eta.put("light", v);
			}
			else if(key.equals("LightType")) {
				eta.put("type", v);
			}
			else if(key.equals("Period")) {
				eta.put("period", v);
			}
			else if(key.equals("Note")) {
				eta.put("note", v);
			}
			else if(key.equals("LocalDesignator")) {
				eta.put("local-desig", v);
			}
			else if(key.equals("InternationalDesignator")) {
				eta.put("int-desig", v);
			}
			else if(key.equals("FacilityPoint")) {
				eta.put("facilities", v);
			}
			else if(key.equals("Racon")) {
				eta.put("racon", v);
			}
			else if(key.equals("LeadingAngle")) {
				eta.put("leading-angle", v);
			}
		}

		if(colour != 0 || style != 0)
			eta.put("style", "0x" + Integer.toHexString((style << 8) | colour));

		return new ExtTypeAttributes(eta, "Line " + lineNo);
	}
}
