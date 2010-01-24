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

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Represents an individual file in the MDX file.
 *
 * I don't really understand the difference between what I call hex mapname
 * and mapname.  We shall always make them equal.
 */
public class MapInfo {
	private int hexMapname;
	private int mapname;
	private char familyId;
	private char productId;

	void write(ByteBuffer os) throws IOException {
		os.putInt(hexMapname);
		os.putChar(productId);
		os.putChar(familyId);
		os.putInt(mapname);
	}

	public void setHexMapname(int hexMapname) {
		this.hexMapname = hexMapname;
	}

	public void setMapname(int mapname) {
		this.mapname = mapname;
	}

	public void setFamilyId(char familyId) {
		this.familyId = familyId;
	}

	public void setProductId(char productId) {
		this.productId = productId;
	}
}
