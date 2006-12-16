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
 * Create date: 09-Dec-2006
 */
package uk.me.parabola.imgfmt.app;

import org.apache.log4j.Logger;


/**
 * Labels are used for names of roads, points of interest etc.  They are
 * stored in a compressed form in uppercase only. There are escape codes to
 * force lowercase and other special characters.
 *
 * @author Steve Ratcliffe
 */
public class Label {
	static private Logger log = Logger.getLogger(Label.class);

	// The compressed form of the label text.
	private byte[] ctext;
	private int length;

	// The offset in to the data section.
	private int offset;

	/**
	 * Create a new label.
	 * @param text The normal text of the label.
	 */
	public Label(String text) {
		ctext = compressText6(text);
	}


	public int getLength() {
		return length;
	}


	public int getOffset() {
		return offset;
	}

	public void setOffset(int offset) {
		this.offset = offset;
	}

	/**
	 * Compress the string to it's 6 bit form.
	 * @param text The original text.
	 * @return The encoded text.  There may be extra bytes on the end so
	 * you have to look at the length field.
	 */
	private byte[] compressText6(String text) {
		String s = text.toUpperCase();

		byte[] buf = new byte[s.length()+1];
		int off = 0;
		for (char c : s.toCharArray()) {
			if (c == ' ') {
				put6(buf, off++, 0);
			} else if (c >= 'A' && c <= 'Z') {
				put6(buf, off++, c - 'A' + 1);
			} // else if ... more TODO
		}

		put6(buf, off++, 0xff);
		this.length = ((off-1) * 6)/8 + 1;
		dumpBuf(buf);
		return buf;
	}

	/**
	 * Each character is packed into 6 bits.  This keeps track of everything so
	 * that the character can be put into the right place in the byte array.
	 *
	 * @param buf The buffer to populate.
	 * @param off The character offset, that is the number of the six bit
	 * character.
	 * @param c The character to place.
	 */
	private void put6(byte[] buf, int off, int c) {
		int bitOff = off * 6;

		// The byte offset
		int byteOff = bitOff/8;

		// The offset withing the byte
		int shift = bitOff - 8*byteOff;

		int mask = 0xfc >> shift;
		buf[byteOff] |= ((c << 2) >> shift) & mask;

		// IF the shift is greater than two we have to put the rest in the
		// next byte.
		if (shift > 2) {
			mask = 0xfc << (8 - shift);
			buf[byteOff + 1] = (byte) (((c << 2) << (8 - shift)) & mask);
		}
	}

	public void write(ImgFile imgFile) {
		log.debug("put label " + this.length);
		imgFile.put(ctext, 0, this.length);
	}

	// For debugging only
	private void dumpBuf(byte[] buf) {
		StringBuffer sb = new StringBuffer();

		for (int i = 0; i < this.length; i++) {
			byte b = buf[i];
			sb.append("0x");
			sb.append(Integer.toHexString(b & 0xff));
			sb.append(' ');
		}
		log.debug(sb);
	}
}