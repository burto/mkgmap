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
import uk.me.parabola.imgfmt.app.CommonHeader;
import uk.me.parabola.imgfmt.app.ImgFileReader;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Section;

/**
 * The header for the RGN file.  This is very simple, just a location and size.
 *
 * @author Steve Ratcliffe
 */
public class RGNHeader extends CommonHeader {
	//private static final int DEF_LEN = 29;
	private static final int DEF_LEN = 125;
	public static final int HEADER_LEN = DEF_LEN;

	private final Section data = new Section();

	private int extTypeAreasOffset;
	private int extTypeAreasSize;
	private int extTypeLinesOffset;
	private int extTypeLinesSize;
	private int extTypePointsOffset;
	private int extTypePointsSize;

	public RGNHeader() {
		super(HEADER_LEN, "GARMIN RGN");
		data.setPosition(HEADER_LEN);
	}

	/**
	 * Read the rest of the header.  Specific to the given file.  It is guaranteed
	 * that the file position will be set to the correct place before this is
	 * called.
	 *
	 * @param reader The header is read from here.
	 */
	protected void readFileHeader(ImgFileReader reader) throws ReadFailedException {
		data.readSectionInfo(reader, false);
	}

	/**
	 * Write the rest of the header.  It is guaranteed that the writer will be set
	 * to the correct position before calling.
	 *
	 * @param writer The header is written here.
	 */
	protected void writeFileHeader(ImgFileWriter writer) {
		data.writeSectionInfo(writer, false);

		if (getHeaderLength() > 29) {
			writer.putInt(extTypeAreasOffset);
			writer.putInt(extTypeAreasSize);
			writer.putInt(0);
			writer.putInt(0);
			writer.putInt(0);
			writer.putInt(0);
			writer.putInt(0);

			writer.putInt(extTypeLinesOffset);
			writer.putInt(extTypeLinesSize);
			writer.putInt(0);
			writer.putInt(0);
			writer.putInt(0);
			writer.putInt(0);
			writer.putInt(0);

			writer.putInt(extTypePointsOffset);
			writer.putInt(extTypePointsSize);
			writer.putInt(0);
			writer.putInt(0);
			writer.putInt(0);
			writer.putInt(0);
			writer.putInt(0);
			writer.putInt(0);
			writer.putInt(0);
			writer.putInt(0);
		}
	}

	public int getDataOffset() {
		return data.getPosition();
	}
	
	public void setDataSize(int dataSize) {
		data.setSize(dataSize);
	}

	public void setExtTypeAreasInfo(int offset, int size) {
		extTypeAreasOffset = offset;
		extTypeAreasSize = size;
	}

	public void setExtTypeLinesInfo(int offset, int size) {
		extTypeLinesOffset = offset;
		extTypeLinesSize = size;
	}

	public void setExtTypePointsInfo(int offset, int size) {
		extTypePointsOffset = offset;
		extTypePointsSize = size;
	}
}