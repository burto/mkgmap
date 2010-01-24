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

package uk.me.parabola.imgfmt.mdxfmt;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * The MDX index file.  Used with the global index.  This is located
 * at the family level in the windows registry and can perhaps index
 * across different products (and maybe families), although such a thing
 * hasn't been seen.
 *
 * @author Steve Ratcliffe
 */
public class MdxFile {
	private final char familyId;
	private final char productId;
	
	private final List<MapInfo> maps = new ArrayList<MapInfo>();

	/**
	 * Create with default family and product ids.
	 * @param familyId The default family id that will be used if no other one
	 * is supplied.
	 * @param productId The default product id for the maps indexed by this
	 * file.
	 */
	public MdxFile(int familyId, int productId) {
		this.familyId = (char) familyId;
		this.productId = (char) productId;
	}

	/**
	 * Add a map with the default family and product id's and with equal
	 * name and hexname.
	 * @param name The map name as an integer.
	 */
	public void addMap(int name) {
		MapInfo info = new MapInfo();
		info.setHexMapname(name);
		info.setMapname(name);
		info.setFamilyId(familyId);
		info.setProductId(productId);

		maps.add(info);
	}

	/**
	 * Write the file out to the given filename.
	 */
	public void write(String filename) throws IOException {
		FileOutputStream stream = new FileOutputStream(filename);
		FileChannel chan = stream.getChannel();
		ByteBuffer buf = ByteBuffer.allocate(1024);
		buf.order(ByteOrder.LITTLE_ENDIAN);

		try {
			writeHeader(chan, buf);
			writeBody(chan, buf);
		} finally {
			chan.close();
		}
	}

	private void writeHeader(WritableByteChannel chan, ByteBuffer buf) throws IOException {
		try {
			buf.put("Midx".getBytes("ascii"));
		} catch (UnsupportedEncodingException e) {
			throw new IOException("Could not write header");
		}
		buf.putChar((char) 100);
		buf.putInt(12);
		buf.putInt(maps.size());

		buf.flip();
		chan.write(buf);
	}

	private void writeBody(WritableByteChannel chan, ByteBuffer buf) throws IOException {
		for (MapInfo info : maps) {
			buf.compact();
			info.write(buf);

			buf.flip();
			chan.write(buf);
		}
	}

	/**
	 * Create a file for testing.  Will probably be removed at some point.
	 * @throws IOException If file cannot be written.
	 */
	public static void main(String[] args) throws IOException {
		MdxFile mdx = new MdxFile(909, 1);
		mdx.addMap(63240001);
		mdx.write("test.mdx");
	}
}
