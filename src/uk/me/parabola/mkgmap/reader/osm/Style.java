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
 * Create date: 02-Nov-2008
 */
package uk.me.parabola.mkgmap.reader.osm;

import java.util.Properties;

import uk.me.parabola.mkgmap.osmstyle.OverlayReader;
import uk.me.parabola.mkgmap.osmstyle.RuleSet;

/**
 * A style converts an OSM element into a garmin element.
 *
 * You start with an OSM element which is just a bunch of name/value tags
 * and you need to convert this to the information required by the .img
 * format which is basically just a name and an
 * integer type.  You also need to know at what zoom levels to show the
 * element at.
 *
 * The Style interface holds the rules for doing this.
 * 
 * @author Steve Ratcliffe
 */
public interface Style {
	public String[] getNameTagList();

	public String getOption(String name);

	public StyleInfo getInfo();

	/**
	 * After the style is loaded we override any options that might
	 * have been set in the style itself with the command line options.
	 *
	 * We may have to filter some options that we don't ever want to
	 * set on the command line.
	 *
	 * @param config The command line options.
	 */
	void applyOptionOverride(Properties config);

	/**
	 * Get the rules that apply to ways.  This includes lines and polygons
	 * as they are not separate primatives in osm.
	 */
	public RuleSet getWayRules();

	/**
	 * Get the rules that apply to nodes.
	 */
	public RuleSet getNodeRules();

	/**
	 * Get the relation rules.
	 */
	public RuleSet getRelationRules();

	/**
	 * Get the overlay definitions.  Most styles will not use this.
	 */
	public OverlayReader getOverlays();
}
