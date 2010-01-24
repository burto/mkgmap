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
 * Create date: Dec 15, 2007
 */
package uk.me.parabola.imgfmt.app;

import java.io.IOException;

/**
 * Represents an item size the position where those items start and the
 * total size of the section.
 */
public class Section {
	private char itemSize;
	private int size;
	private int position;
	private Section link;
	private int extraValue;

	public Section() {
	}

	public Section(char itemSize) {
		this.itemSize = itemSize;
	}

	public Section(Section link, char itemSize) {
		this.itemSize = itemSize;
		this.link = link;
	}

	public Section(Section link) {
		this.link = link;
	}

	public void inc() {
		size += itemSize;
	}

	public char getItemSize() {
		return itemSize;
	}

	public void setItemSize(char itemSize) {
		this.itemSize = itemSize;
	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}

	/**
	 * Get the start position of this section.  If this is linked to another
	 * section, then we return the end address of that section.
	 * @return The first offset for this section.
	 */
	public int getPosition() {
		if (link != null)
			return link.getEndPos();
		return position;
	}

	public void setPosition(int position) {
		this.position = position;
		// Setting a position breaks the link
		this.link = null;
	}

	/**
	 * Get the position of the end of the section.
	 * @return The offset of the end of the section relative to the beginning
	 * of the application file.
	 */
	public int getEndPos() {
		return getPosition() + size;
	}

	public String toString() {
		return "pos=" + getPosition() + ", size=" + size + ", itemSize=" + itemSize;
	}

	/**
	 * Get the number of items in the section.  This should only be called
	 * if the itemSize is set.
	 * @return The number of items in the section, or zero if this is not
	 * a fixed size item kind of section.
	 */
	public int getNumItems() {
		if (itemSize == 0)
			return 0;
		
		return size/ (int) itemSize;
	}

	public int getExtraValue() {
		return extraValue;
	}

	public void setExtraValue(int extraValue) {
		this.extraValue = extraValue;
	}

	public void readSectionInfo(ImgFileReader reader, boolean withItemSize) {
		setPosition(reader.getInt());
		setSize(reader.getInt());
		if (withItemSize)
			setItemSize(reader.getChar());
	}

	public void writeSectionInfo(ImgFileWriter writer) {
		writeSectionInfo(writer, false);
	}

	public void writeSectionInfo(ImgFileWriter writer, boolean withItemSize) {
		writeSectionInfo(writer, withItemSize, false);
	}

	public void writeSectionInfo(ImgFileWriter writer, boolean withItemSize, boolean withExtraValue) {
		writer.putInt(getPosition());
		writer.putInt(getSize());
		if (withItemSize || getItemSize() > 0)
			writer.putChar(getItemSize());
		if (withExtraValue)
			writer.putInt(getExtraValue());
	}

	public static void close(ImgFileWriter writer) {
		assert writer instanceof SectionWriter;
		try {
			writer.close();
		} catch (IOException e) {
			// ignore as this is only for section writers.
		}
	}
}
