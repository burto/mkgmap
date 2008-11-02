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
package uk.me.parabola.imgfmt.app.lbl;

import uk.me.parabola.imgfmt.app.CommonHeader;
import uk.me.parabola.imgfmt.app.ImgFileReader;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.labelenc.CodeFunctions;

/**
 * The header for the LBL file.
 *
 * @author Steve Ratcliffe
 */
public class LBLHeader extends CommonHeader {
	public static final int HEADER_LEN = 196; // Other lengths are possible

	static final int INFO_LEN = 28;

	private static final char UNK3_REC_LEN = 0;

	private int labelStart; // Start of labels.
	private int labelSize; // Size of file.

	// Code page.
	private int codePage;

	// The type of encoding employed.  This is not a length.
	private int encodingType = CodeFunctions.ENCODING_FORMAT6;

	// The label section also contains all kinds of records related to place,
	// so these have all been put in their own class.
	private final PlacesHeader placeHeader;

	public LBLHeader() {
		super(HEADER_LEN, "GARMIN LBL");
		placeHeader = new PlacesHeader();
	}

	/**
	 * Read the rest of the header.  Specific to the given file.  It is guaranteed
	 * that the file position will be set to the correct place before this is
	 * called.
	 *
	 * @param reader The header is read from here.
	 */
	protected void readFileHeader(ImgFileReader reader) {
		labelStart = reader.getInt();
		labelSize = reader.getInt();
		reader.get();
		encodingType = reader.get();

		// Read the places part of the header.
		placeHeader.readFileHeader(reader);

		// more to do but not needed yet...  Just set position
		reader.position(labelStart);
	}

	/**
	 * Write the rest of the header.  It is guaranteed that the writer will be set
	 * to the correct position before calling.
	 *
	 * @param writer The header is written here.
	 */
	protected void writeFileHeader(ImgFileWriter writer) {
		// LBL1 section, these are regular labels
		writer.putInt(HEADER_LEN + INFO_LEN);
		writer.putInt(getLabelSize());

		writer.put((byte) 0);
		writer.put((byte) encodingType);

		placeHeader.writeFileHeader(writer);

		writer.putChar((char) getCodePage()); //code
		writer.putInt(0);

		// Sort descriptor ??? what does that mean
		writer.putInt(HEADER_LEN);
		writer.putInt(INFO_LEN);

		writer.putInt(placeHeader.getLastPos());
		writer.putInt(0);
		writer.putChar(UNK3_REC_LEN);
		writer.putChar((char) 0);
	}

	protected int getEncodingType() {
		return encodingType;
	}

	public void setEncodingType(int type) {
		this.encodingType = type;
	}

	protected int getLabelSize() {
		return labelSize;
	}

	public void setLabelSize(int labelSize) {
		this.labelSize = labelSize;
		placeHeader.setLabelEnd(HEADER_LEN + INFO_LEN + labelSize);
	}

	protected int getCodePage() {
		return codePage;
	}

	public void setCodePage(int codePage) {
		this.codePage = codePage;
	}

	public int getLabelStart() {
		return labelStart;
	}

	public PlacesHeader getPlaceHeader() {
		return placeHeader;
	}
}
