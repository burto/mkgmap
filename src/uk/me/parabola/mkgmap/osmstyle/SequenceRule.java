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
 * Create date: Apr 27, 2008
 */
package uk.me.parabola.mkgmap.osmstyle;

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.TypeRule;
import uk.me.parabola.mkgmap.reader.osm.GType;

/**
 * A list of rules, the first one that matches wins.
 * 
 * @author Steve Ratcliffe
 */
public class SequenceRule extends BaseRule implements TypeRule {
	private final List<TypeRule> list = new ArrayList<TypeRule>();

	public GType resolveType(Element el) {
		for (TypeRule rule : list) {
			GType gt = rule.resolveType(el);
			if (gt != null)
				return gt;
		}
		return null;
	}

	public void add(TypeRule type) {
		// Later rules override earlier ones, so insert at the begining.
		list.add(0, type);
	}
}
