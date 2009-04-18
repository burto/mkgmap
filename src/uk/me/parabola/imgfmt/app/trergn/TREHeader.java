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
 * Create date: Dec 14, 2007
 */
package uk.me.parabola.imgfmt.app.trergn;

import uk.me.parabola.imgfmt.ReadFailedException;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.CommonHeader;
import uk.me.parabola.imgfmt.app.ImgFileReader;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Section;
import uk.me.parabola.log.Logger;
import uk.me.parabola.util.EnhancedProperties;

/**
 * @author Steve Ratcliffe
 */
public class TREHeader extends CommonHeader {
	private static final Logger log = Logger.getLogger(TREHeader.class);

	// The tre section comes in different versions with different length
	// headers.  We just refer to them by the header length for lack of any
	// better description.
	public static final int TRE_120 = 120;
	public static final int TRE_184 = 184;
	private static final int TRE_188 = 188;

	// The header length to use when creating a file.
	private static final int DEFAULT_HEADER_LEN = TRE_188;

	// A map has a display priority that determines which map is on top
	// when two maps cover the same area.
	private static final int DEFAULT_DISPLAY_PRIORITY = 0x19;

	static final int MAP_LEVEL_REC_SIZE = 4;
	private static final char POLYLINE_REC_LEN = 2;
	private static final char POLYGON_REC_LEN = 2;
	private static final char POINT_REC_LEN = 3;
	private static final char COPYRIGHT_REC_SIZE = 0x3;
	static final int SUBDIV_REC_SIZE = 14;
	static final int SUBDIV_REC_SIZE2 = 16;

	private static final int POI_FLAG_TRANSPARENT = 0x2;

	// Bounding box.  All units are in map units.
	private Area area = new Area(0,0,0,0);

	private int mapInfoSize;

	private int mapLevelPos;
	private int mapLevelsSize;

	private int subdivPos;
	private int subdivSize;

	private byte poiDisplayFlags;

	private int displayPriority = DEFAULT_DISPLAY_PRIORITY;

	private final Section copyright = new Section(COPYRIGHT_REC_SIZE);
	private final Section polyline = new Section(POLYLINE_REC_LEN);
	private final Section polygon = new Section(POLYGON_REC_LEN);
	private final Section points = new Section(POINT_REC_LEN);
	private final Section tre7 = new Section(points, (char) 13);
	private final Section tre8 = new Section(tre7, (char) 4);
	//private Section tre9 = new Section(tre8);

	private int mapId;

	public TREHeader() {
		super(DEFAULT_HEADER_LEN, "GARMIN TRE");
	}

	/**
	 * Read the rest of the header.  Specific to the given file.  It is guaranteed
	 * that the file position will be set to the correct place before this is
	 * called.
	 *
	 * @param reader The header is read from here.
	 */
	protected void readFileHeader(ImgFileReader reader) throws ReadFailedException {
		assert reader.position() == COMMON_HEADER_LEN;
		int maxLat = reader.get3();
		int maxLon = reader.get3();
		int minLat = reader.get3();
		int minLon = reader.get3();
		setBounds(new Area(minLat, minLon, maxLat, maxLon));
		log.info("read area is", getBounds());

		// more to do...
		mapLevelPos = reader.getInt();
		mapLevelsSize = reader.getInt();
		subdivPos = reader.getInt();
		subdivSize = reader.getInt();

		copyright.readSectionInfo(reader, true);
		reader.getInt();

		poiDisplayFlags = reader.get();
		displayPriority = reader.get3();
		reader.getInt();
		reader.getChar();
		reader.get();

		polyline.readSectionInfo(reader, true);
		reader.getInt();
		polygon.readSectionInfo(reader, true);
		reader.getInt();
		points.readSectionInfo(reader, true);
		reader.getInt();

		int mapInfoOff = mapLevelPos;
		if (subdivPos < mapInfoOff)
			mapInfoOff = subdivPos;
		if (copyright.getPosition() < mapInfoOff)
			mapInfoOff = copyright.getPosition();

		mapInfoSize = mapInfoOff - getHeaderLength();
		
		reader.getInt();
		reader.getInt();
		reader.getInt();

		copyright.readSectionInfo(reader, true);
		reader.getInt();

		if (getHeaderLength() > 116) {
			reader.position(116);
			mapId = reader.getInt();
		}
	}

	/**
	 * Write the rest of the header.  It is guaranteed that the writer will be set
	 * to the correct position before calling.
	 *
	 * @param writer The header is written here.
	 */
	protected void writeFileHeader(ImgFileWriter writer) {
		writer.put3(area.getMaxLat());
		writer.put3(area.getMaxLong());
		writer.put3(area.getMinLat());
		writer.put3(area.getMinLong());

		writer.putInt(getMapLevelsPos());
		writer.putInt(getMapLevelsSize());

		writer.putInt(getSubdivPos());
		writer.putInt(getSubdivSize());

		copyright.writeSectionInfo(writer);
		writer.putInt(0);

		writer.put(getPoiDisplayFlags());

		writer.put3(displayPriority);
		writer.putInt(0x110301);

		writer.putChar((char) 1);
		writer.put((byte) 0);

		polyline.writeSectionInfo(writer);
		writer.putInt(0);
		polygon.writeSectionInfo(writer);
		writer.putInt(0);
		points.writeSectionInfo(writer);
		writer.putInt(0);

		// There are a number of versions of the header with increasing lengths
		if (getHeaderLength() > 116)
			writer.putInt(getMapId());

		if (getHeaderLength() > 120) {
			writer.putInt(0);

			tre7.writeSectionInfo(writer);
			writer.putInt(0); // not usually zero

			tre8.writeSectionInfo(writer);
			writer.putChar((char) 0);
			writer.putInt(0);
		}

		if (getHeaderLength() > 154) {
			MapValues mv = new MapValues(mapId, getHeaderLength());
			mv.calculate();
			writer.putInt(mv.value(0));
			writer.putInt(mv.value(1));
			writer.putInt(mv.value(2));
			writer.putInt(mv.value(3));

			writer.putInt(0);
			writer.putInt(0);
			writer.putInt(0);
			writer.putChar((char) 0);
			writer.putInt(0);
		}
		
		writer.position(getHeaderLength());
	}

	public void config(EnhancedProperties props) {
		String key = "draw-priority";
		if (props.containsKey(key))
			setDisplayPriority(props.getProperty(key, 0x19));

		if (props.containsKey("transparent"))
			poiDisplayFlags |= POI_FLAG_TRANSPARENT;
	}
	
	/**
	 * Set the bounds based upon the latitude and longitude in degrees.
	 * @param area The area bounded by the map.
	 */
	public void setBounds(Area area) {
		this.area = area;
	}

	public Area getBounds() {
		return area;
	}

	public void setMapId(int id) {
		mapId = id;
	}
	
	public void setPoiDisplayFlags(byte poiDisplayFlags) {
		this.poiDisplayFlags = poiDisplayFlags;
	}	

	public int getMapInfoSize() {
		return mapInfoSize;
	}

	public void setMapInfoSize(int mapInfoSize) {
		this.mapInfoSize = mapInfoSize;
	}

	public int getMapLevelsPos() {
		return mapLevelPos;
	}

	public void setMapLevelPos(int mapLevelPos) {
		this.mapLevelPos = mapLevelPos;
	}

	public int getMapLevelsSize() {
		return mapLevelsSize;
	}

	public void setMapLevelsSize(int mapLevelsSize) {
		this.mapLevelsSize = mapLevelsSize;
	}

	public int getSubdivPos() {
		return subdivPos;
	}

	public void setSubdivPos(int subdivPos) {
		this.subdivPos = subdivPos;
	}

	public int getSubdivSize() {
		return subdivSize;
	}

	public void setSubdivSize(int subdivSize) {
		this.subdivSize = subdivSize;
	}

	public void setCopyrightPos(int copyrightPos) {
		//this.copyrightPos = copyrightPos;
		copyright.setPosition(copyrightPos);
	}

	public void incCopyrightSize() {
		copyright.inc();
	}

	protected byte getPoiDisplayFlags() {
		return poiDisplayFlags;
	}

	public void setPolylinePos(int polylinePos) {
		polyline.setPosition(polylinePos);
	}

	public void incPolylineSize() {
		polyline.inc();
	}

	public void setPolygonPos(int polygonPos) {
		polygon.setPosition(polygonPos);
	}

	public void incPolygonSize() {
		polygon.inc();
	}

	public void setPointPos(int pointPos) {
		points.setPosition(pointPos);
	}

	public void incPointSize() {
		points.inc();
	}

	protected int getMapId() {
		return mapId;
	}

	protected void setDisplayPriority(int displayPriority) {
		this.displayPriority = displayPriority;
	}

	public void setTre7Pos(int pos) {
		tre7.setPosition(pos);
	}

	public void incTre7() {
		tre7.inc();
	}

	public int getDisplayPriority() {
		return displayPriority;
	}
}
