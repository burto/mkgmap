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
 * Create date: 03-Dec-2006
 */
package uk.me.parabola.imgfmt.app.typ;

import uk.me.parabola.imgfmt.app.BufferedImgFileReader;
import uk.me.parabola.imgfmt.app.BufferedImgFileWriter;
import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.log.Logger;

/**
 * The TYP file.  I know next to nothing about this file, so the code will be
 * very experimental for a while and indeed will be mostly to investigate
 * the file format.
 * 
 * @author Steve Ratcliffe
 */
public class TYPFile extends ImgFile {
	private static final Logger log = Logger.getLogger(TYPFile.class);

	private final TYPHeader header = new TYPHeader();

	public TYPFile(ImgChannel chan, boolean write) {
		setHeader(header);
		if (write) {
			setWriter(new BufferedImgFileWriter(chan));
			position(TYPHeader.HEADER_LEN);
		} else {
			setReader(new BufferedImgFileReader(chan));
			header.readHeader(getReader());
		}
	}

	public void write() {
	}

	public void writePost() {
		getHeader().writeHeader(getWriter());
	}

	
}