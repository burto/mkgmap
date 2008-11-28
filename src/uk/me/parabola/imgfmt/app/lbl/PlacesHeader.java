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
 * Create date: Jan 1, 2008
 */
package uk.me.parabola.imgfmt.app.lbl;

import uk.me.parabola.imgfmt.app.Section;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.ImgFileReader;

/**
 * This is not a separate header, but rather part of the LBL header.  It is just
 * separated out for convenience.  All the records that have some kind of
 * meaning associated with a place are put here.
 *
 * @author Steve Ratcliffe
 */
public class PlacesHeader {
	private static final char COUNTRY_REC_LEN = 3;
	private static final char REGION_REC_LEN = 5;
	private static final char CITY_REC_LEN = 5;
	private static final char UNK1_REC_LEN = 4;
	private static final char UNK2_REC_LEN = 4;
	private static final char ZIP_REC_LEN = 3;
	private static final char HIGHWAY_REC_LEN = 6;
	private static final char EXIT_REC_LEN = 5;
	private static final char HIGHWAYDATA_REC_LEN = 3;

	private final Section country = new Section(COUNTRY_REC_LEN);
	private final Section region = new Section(country, REGION_REC_LEN);
	private final Section city = new Section(region, CITY_REC_LEN);
	private final Section unk1 = new Section(city, UNK1_REC_LEN);
	private final Section poiProperties = new Section(unk1);
	private final Section unk2 = new Section(poiProperties, UNK2_REC_LEN);
	private final Section zip = new Section(unk2, ZIP_REC_LEN);
	private byte POIGlobalFlags ;

	void setPOIGlobalFlags(byte flags) {
		this.POIGlobalFlags = flags;
	}

	byte getPOIGlobalFlags() {
		return POIGlobalFlags;
	}

	void writeFileHeader(ImgFileWriter writer) {
		writer.putInt(country.getPosition());
		writer.putInt(country.getSize());
		writer.putChar(country.getItemSize());
		writer.putInt(0);

		writer.putInt(region.getPosition());
		writer.putInt(region.getSize());
		writer.putChar(region.getItemSize());
		writer.putInt(0);

		writer.putInt(city.getPosition());
		writer.putInt(city.getSize());
		writer.putChar(city.getItemSize());
		writer.putInt(0);

		writer.putInt(unk1.getPosition());
		writer.putInt(unk1.getSize());
		writer.putChar(unk1.getItemSize());
		writer.putInt(0);

		writer.putInt(poiProperties.getPosition());
		writer.putInt(poiProperties.getSize());
		writer.put((byte) 0); // offset multiplier
		writer.put(POIGlobalFlags); // properties global mask
		writer.putChar((char) 0);
		writer.put((byte) 0);

		writer.putInt(unk2.getPosition());
		writer.putInt(unk2.getSize());
		writer.putChar(unk2.getItemSize());
		writer.putInt(0);

		writer.putInt(zip.getPosition());
		writer.putInt(zip.getSize());
		writer.putChar(zip.getItemSize());
		writer.putInt(0);

		int lastPos = zip.getEndPos();

		writer.putInt(lastPos);
		writer.putInt(0);
		writer.putChar(HIGHWAY_REC_LEN);
		writer.putInt(0);

		writer.putInt(lastPos);
		writer.putInt(0);
		writer.putChar(EXIT_REC_LEN);
		writer.putInt(0);

		writer.putInt(lastPos);
		writer.putInt(0);
		writer.putChar(HIGHWAYDATA_REC_LEN);
		writer.putInt(0);
	}

	void readFileHeader(ImgFileReader reader) {
		reader.position(0x1f);

		country.setPosition(reader.getInt());
		country.setSize(reader.getInt());
		country.setItemSize(reader.getChar());
		reader.getInt();

		region.setPosition(reader.getInt());
		region.setSize(reader.getInt());
		region.setItemSize(reader.getChar());
		reader.getInt();

		city.setPosition(reader.getInt());
		city.setSize(reader.getInt());
		city.setItemSize(reader.getChar());
		reader.getInt();
	}

	int getLastPos() {
		// Beware this is not really valid until all is written.
		return zip.getEndPos();
	}

	void setLabelEnd(int pos) {
		country.setPosition(pos);
	}

	void endCountries(int pos) {
		country.setSize(pos - country.getPosition());
	}

	void endRegions(int pos) {
		region.setSize(pos - region.getPosition());
	}

	void endCity(int pos) {
		city.setSize(pos - city.getPosition());
	}

	void endPOI(int pos) {
		poiProperties.setSize(pos - poiProperties.getPosition());
	}

	void endZip(int pos) {
		zip.setSize(pos - zip.getPosition());
	}

	public int getNumCities() {
		return city.getNumItems();
	}
}
