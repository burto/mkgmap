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
package uk.me.parabola.mkgmap.reader.osm;

import uk.me.parabola.mkgmap.general.LoadableMapDataSource;
import uk.me.parabola.mkgmap.general.MapDetails;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.imgfmt.FormatException;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Overview;

import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.ParserConfigurationException;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

import org.xml.sax.SAXException;


/**
 * Read an OpenStreetMap data file in .osm format.  It is converted into a
 * generic format that the map is built from.
 * <p>Although not yet implemented, the intermediate format is important
 * as several passes are required to produce the map at different zoom levels.
 * At lower resolutions, some roads will have fewer points or won't be shown at
 * all.
 *
 * @author Steve Ratcliffe
 */
public class OsmMapDataSource implements LoadableMapDataSource {

	private final MapDetails mapper = new MapDetails();

	/**
	 * Load the .osm file and produce the intermediate format.
	 *
	 * @param name The filename to read.
	 * @throws FileNotFoundException If the file does not exist.
	 */
	public void load(String name) throws FileNotFoundException, FormatException {
		try {
			FileInputStream is = new FileInputStream(name);
			SAXParserFactory parserFactory = SAXParserFactory.newInstance();
			SAXParser parser = parserFactory.newSAXParser();

			try {
				OSMXmlHandler handler = new OSMXmlHandler();
				handler.setCallbacks(mapper);
				parser.parse(is, handler);
			} catch (IOException e) {
				throw new FormatException("Error reading file", e);
			}
		} catch (SAXException e) {
			throw new FormatException("Error parsing file", e);
		} catch (ParserConfigurationException e) {
			throw new FormatException("Internal error configuring xml parser", e);
		}
	}

	public String copyrightMessage() {
		return "OpenStreetMap.org contributers.";
	}


	/**
	 * Get the area that this map covers. Delegates to the map collector.
	 *
	 * @return The area the map covers.
	 */
	public Area getBounds() {
		return mapper.getBounds();
	}

	/**
	 * Get the list of lines that need to be rendered to the map. Delegates to
	 * the map collector.
	 *
	 * @return A list of {@link MapLine} objects.
	 */
	public List<MapLine> getLines() {
		return mapper.getLines();
	}

	public List<MapShape> getShapes() {
		return mapper.getShapes();
	}

	/**
	 * Get a list of every feature that is used in the map.  As features are
	 * created a list is kept of each separate feature that is used.  This
	 * goes into the .img file and is important for points and polygons although
	 * it doesn't seem to matter if lines are represented or not on my Legend Cx
	 * anyway.
	 *
	 * @return A list of all the types of point, polygon and polyline that are
	 * used in the map.
	 */
	public List<Overview> getOverviews() {
		return mapper.getOverviews();
	}


	public List<MapPoint> getPoints() {
		return mapper.getPoints();
	}
}