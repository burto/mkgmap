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
 * Configuration for the MDR file.
 * Mostly used when creating a file as there are a number of different options
 * in the way that it is done.
 *
 * @author Steve Ratcliffe
 */
public class MdrConfig {
	private static final int DEFAULT_HEADER_LEN = 286;

	private boolean writable;
	private boolean forDevice;
	private int headerLen = DEFAULT_HEADER_LEN;

	/**
	 * True if we are creating the file, rather than reading it.
	 */
	public boolean isWritable() {
		return writable;
	}

	public void setWritable(boolean writable) {
		this.writable = writable;
	}

	/**
	 * The format that is used by the GPS devices is different to that used
	 * by Map Source. This parameter says which to do.
	 * @return True if we are creating the the more compact format required
	 * for a device.
	 */
	public boolean isForDevice() {
		return forDevice;
	}

	public void setForDevice(boolean forDevice) {
		this.forDevice = forDevice;
	}

	/**
	 * There are a number of different header lengths in existence.  This
	 * controls what sections can exist (and perhaps what must exist).
	 * @return The header length.
	 */
	public int getHeaderLen() {
		return headerLen;
	}

	public void setHeaderLen(int headerLen) {
		this.headerLen = headerLen;
	}
}
