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

/**
 * @author Steve Ratcliffe
 */
public abstract class RecordBase extends ConfigBase {
	private int mapIndex;

	public int getMapIndex() {
		return mapIndex;
	}

	public void setMapIndex(int mapIndex) {
		this.mapIndex = mapIndex;
	}
}
