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
package uk.me.parabola.imgfmt.app.labelenc;

import java.nio.charset.Charset;

/**
 * An encoder for latin script
 * @author Steve Ratcliffe
 */
public class LatinEncoder implements CharacterEncoder {
	private final Transliterator trans = new TableTransliterator("latin1");
	private final Charset latinCharset = Charset.forName("latin1");

	public EncodedText encodeText(String text) {
		// Need to add a null character at the end of the string for this format.
		String s = trans.transliterate(text + "\000");
		byte[] chars = s.getBytes(latinCharset);
		return new EncodedText(chars, chars.length);
	}
}
