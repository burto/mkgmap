/*
 * Copyright (C) 2009.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */
package uk.me.parabola.imgfmt.app.mdr;

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.fs.ImgChannel;

/**
 * The section MDR 1 contains a list of maps and for each map
 * an offset to a reverse index for that map.
 *
 * The reverse index consists of a number of sections, that I call sub-sections
 * here.  The sub-sections are all lists of record numbers in other sections
 * in the MDR that contain records belonging to more than one map.
 *
 * Using the index you could extract records that belong to an individual map
 * from other MDR sections without having to go through them all and check
 * which map they belong to.
 *
 * The subsections are as follows:
 *
 * sub1 points into MDR 11 (POIs)
 * sub2 points into MDR 10 (POI types)
 * sub3 points into MDR 7 (street names)
 * sub4 points into MDR 5 (cities)
 * sub5 points into MDR 6
 * sub6
 * sub7
 * sub8
 *
 * @author Steve Ratcliffe
 */
public class Mdr1 extends MdrSection {
	private final List<Mdr1Record> maps = new ArrayList<Mdr1Record>();

	private final ImgChannel chan;

	public Mdr1(MdrConfig config, ImgChannel chan) {
		this.chan = chan;

		setConfig(config);
	}

	/**
	 * Add a map.  Create an MDR1 record for it and also allocate its reverse
	 * index if this is not for a device.
	 * @param mapNumber The map index number.
	 */
	public void addMap(int mapNumber) {
		Mdr1Record rec = new Mdr1Record(mapNumber, getConfig());
		maps.add(rec);

		if (!isForDevice()) {
			Mdr1MapIndex mapIndex = new Mdr1MapIndex();
			rec.setMdrMapIndex(mapIndex);
		}
	}

	public void writeSubSections(ImgFileWriter writer) {
		for (Mdr1Record rec : maps) {
			rec.setIndexOffset(writer.position());
			Mdr1MapIndex mapIndex = rec.getMdrMapIndex();
			mapIndex.writeSubSection(writer);
		}
	}

	/**
	 * This is written right at the end after we know all the offsets in
	 * the MDR 1 record.
	 * @param writer The mdr 1 records are written out to this writer.
	 */
	public void writeSectData(ImgFileWriter writer) {
		for (Mdr1Record rec : maps)
			rec.write(writer);
	}

	public int getItemSize() {
		return isForDevice()? 4: 8;
	}

	public void setStartPosition(int sectionNumber) {
		for (Mdr1Record mi : maps)
			mi.getMdrMapIndex().startSection(sectionNumber);
	}

	public void setEndPosition(int sectionNumber) {
		for (Mdr1Record mi : maps) {
			mi.getMdrMapIndex().endSection(sectionNumber);
	}
}

	public void setPointerSize(int sectionSize, int recordSize) {
		for (Mdr1Record mi : maps) {
			Mdr1MapIndex mapIndex = mi.getMdrMapIndex();
			mapIndex.setPointerSize(sectionSize, recordSize);
		}
	}

	public void addPointer(int mapNumber, int recordNumber) {
		Mdr1MapIndex mi = maps.get(mapNumber - 1).getMdrMapIndex();
		mi.addPointer(recordNumber);
	}
}
