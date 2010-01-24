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

import uk.me.parabola.imgfmt.app.lbl.City;

/**
 * Holds information about a city that will make its way into mdr 5.
 * This class is used in several places as the information has to be gathered
 * from the cities section of LBL and the points in RGN.
 * 
 * @author Steve Ratcliffe
 */
public class Mdr5Record extends RecordBase implements Comparable<Mdr5Record> {
	/** The city index within its own map */
	private int cityIndex;

	/** The index across all maps */
	private int globalCityIndex;

	private int region;
	private int lblOffset;
	private int stringOffset;
	private String name;

	public Mdr5Record() {
	}

	public Mdr5Record(City c) {
		cityIndex = c.getIndex();
		region = c.getRegionNumber();
	}

	public int compareTo(Mdr5Record o) {
		return name.compareTo(o.name);
	}

	public int getCityIndex() {
		return cityIndex;
	}

	public int getGlobalCityIndex() {
		return globalCityIndex;
	}

	public void setGlobalCityIndex(int globalCityIndex) {
		this.globalCityIndex = globalCityIndex;
	}

	public int getRegion() {
		return region;
	}

	public void setRegion(int region) {
		this.region = region;
	}

	public int getLblOffset() {
		return lblOffset;
	}

	public void setLblOffset(int lblOffset) {
		this.lblOffset = lblOffset;
	}

	public int getStringOffset() {
		return stringOffset;
	}

	public void setStringOffset(int stringOffset) {
		this.stringOffset = stringOffset;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}
}
