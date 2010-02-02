/*
 * Copyright (c) 2009.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * Created: 13 Sep 2009
 * By: steve
 */

package uk.me.parabola.mkgmap.reader.osm;

import java.util.Formatter;

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.LevelInfo;

/**
 * Holds the garmin type of an element and all the information that
 * will be needed to represent it on the map.  So we have a range of
 * resolutions at which it will be present.
 */
public class GType {
	private static final Logger log = Logger.getLogger(GType.class);

	public static final int POINT = 1;
	public static final int POLYLINE = 2;
	public static final int POLYGON = 3;

	private final int featureKind;
	private final int type;

	private int minResolution = 24;
	private int maxResolution = 24;

	private int maxLevel = -1;
	private int minLevel;

	private String defaultName;

	// road class and speed will be set on roads.
	private int roadClass;
	private int roadSpeed;

	private boolean road;

	/** If this is set, then we look for further types after this one is matched */
	private boolean continueSearch;

	// by default, a rule's actions are skipped when searching for
	// further rules to match - by setting this true, the rule's
	// actions will always be executed
	private boolean propogateActionsOnContinue;

	public GType(int featureKind, String type) {
		this.featureKind = featureKind;
		try {
			this.type = Integer.decode(type);
		} catch (NumberFormatException e) {
			log.error("not numeric " + type);
			throw new ExitException("non-numeric type in map-features file");
		}
	}

	public GType(int featureKind, String type, String subtype) {
		this.featureKind = featureKind;
		try {
			this.type = (Integer.decode(type) << 8) + Integer.decode(subtype);
		} catch (NumberFormatException e) {
			log.error("not numeric " + type + ' ' + subtype);
			throw new ExitException("non-numeric type in map-features file");
		}
	}

	public int getFeatureKind() {
		return featureKind;
	}

	public int getType() {
		return type;
	}

	public int getMinResolution() {
		return minResolution;
	}

	public void setMinResolution(int minResolution) {
		this.minResolution = minResolution;
	}

	public int getMaxResolution() {
		return maxResolution;
	}

	public void setMaxResolution(int maxResolution) {
		this.maxResolution = maxResolution;
	}

	public String getDefaultName() {
		return defaultName;
	}

	public void setDefaultName(String defaultName) {
		this.defaultName = defaultName;
	}

	/**
	 * Set minLevel and maxLevel based on the resolution values set and
	 * the given levels info.  We do this because we used to work only
	 * on resolution, but we want to move more towards working with
	 * levels.
	 */
	public void fixLevels(LevelInfo[] levels) {
		for (LevelInfo info : levels) {
			if (info.getBits() <= minResolution)
				maxLevel = info.getLevel();
			if (info.getBits() <= maxResolution)
				minLevel = info.getLevel();
		}
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		Formatter fmt = new Formatter(sb);
		sb.append('[');
		fmt.format("%#x", type);
		if (maxLevel == -1) {
			if (maxResolution == 24)
				fmt.format(" resolution %d", minResolution);
			else
				fmt.format(" resolution %d-%d", maxResolution, minResolution);
		} else {
			if (minLevel == 0)
				fmt.format(" level %d", maxLevel);
			else
				fmt.format(" level %d-%d", minLevel, maxLevel);
		}
		if (road)
			fmt.format(" road_class=%d road_speed=%d", roadClass, roadSpeed);
		
		if (continueSearch)
			fmt.format(" continue");
		if (propogateActionsOnContinue)
			fmt.format(" propagate");
		sb.append(']');
		return sb.toString();
	}

	public int getMinLevel() {
		return minLevel;
	}

	public int getMaxLevel() {
		return maxLevel;
	}

	public int getRoadClass() {
		return roadClass;
	}

	public void setRoadClass(int roadClass) {
		road = true;
		this.roadClass = roadClass;
	}

	public int getRoadSpeed() {
		return roadSpeed;
	}

	public void setRoadSpeed(int roadSpeed) {
		road = true;
		this.roadSpeed = roadSpeed;
	}

	public boolean isRoad() {
		return road;
	}

	public boolean isContinueSearch() {
		return continueSearch;
	}

	public void propagateActions(boolean propagate) {
		propogateActionsOnContinue = propagate;
	}

	public boolean isPropogateActions() {
		return !continueSearch || propogateActionsOnContinue;
	}

	public void setContinueSearch(boolean continueSearch) {
		this.continueSearch = continueSearch;
	}
}
