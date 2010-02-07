/*
 * Copyright (C) 2008 Steve Ratcliffe
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
 * Create date: 06-Nov-2008
 */
package uk.me.parabola.mkgmap.osmstyle.eval;

import uk.me.parabola.mkgmap.reader.osm.Element;

/**
 * An OR operation.  The second is only run if the first fails.
 * 
 * @author Steve Ratcliffe
 */
public class OrOp extends AbstractBinaryOp {

	public OrOp() {
		setType(OR);
	}

	public boolean eval(Element el) {
		return getFirst().eval(el) || getSecond().eval(el);
	}

	public int priority() {
		return 3;
	}
}
