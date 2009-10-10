/*
 * Copyright (C) 2008
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
 * Create date: 07-Jul-2008
 */
package uk.me.parabola.imgfmt.app.net;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.log.Logger;

/**
 * An arc joins two nodes within a {@link RouteCenter}.  This may be renamed
 * to a Segement.
 * The arc also references the road that it is a part of.
 *
 * There are also links between nodes in different centers.
 *
 * @author Steve Ratcliffe
 */
public class RouteArc {
	private static final Logger log = Logger.getLogger(RouteArc.class);
	
	// Flags A
	private static final int FLAG_NEWDIR = 0x80;
	private static final int FLAG_FORWARD = 0x40;
	private static final int MASK_DESTCLASS = 0x7;
	public static final int MASK_CURVE_LEN = 0x38;

	// Flags B
	private static final int FLAG_LAST_LINK = 0x80;
	private static final int FLAG_EXTERNAL = 0x40;

	private int offset;

	private int initialHeading; // degrees
	private int finalHeading; // degrees

	private final RoadDef roadDef;

	// The nodes that this arc comes from and goes to
	private final RouteNode source;
	private final RouteNode dest;

	// The index in Table A describing this arc.
	private byte indexA;
	// The index in Table B that this arc goes via, if external.
	private byte indexB;
	
	private byte flagA;
	private byte flagB;

	private boolean curve;
	private int length;
	private int pointsHash;

	/**
	 * Create a new arc.
	 *
	 * @param roadDef The road that this arc segment is part of.
	 * @param source The source node.
	 * @param dest The destination node.
	 * @param initialHeading The initial heading (signed degrees)
	 */
	public RouteArc(RoadDef roadDef,
					RouteNode source, RouteNode dest,
					int initialHeading, int finalHeading,
					double length, int pointsHash) {
		this.roadDef = roadDef;
		this.source = source;
		this.dest = dest;
		this.initialHeading = initialHeading;
		this.finalHeading = finalHeading;
		this.length = convertMeters(length);
		this.pointsHash = pointsHash;
	}

	public int getInitialHeading() {
		return initialHeading;
	}

	public void setInitialHeading(int ih) {
		initialHeading = ih;
	}

	public int getFinalHeading() {
		return finalHeading;
	}

	public void setFinalHeading(int fh) {
		finalHeading = fh;
	}

	public RouteNode getSource() {
		return source;
	}

	public RouteNode getDest() {
		return dest;
	}

	public int getLength() {
		return length;
	}

	public int getPointsHash() {
		return pointsHash;
	}

	/**
	 * Provide an upper bound for the written size in bytes.
	 */
	public int boundSize() {
		// XXX: this could be reduced, and may increase
		// currently: 1 (flagA) + 1-2 (offset) + 1 (indexA)
		//          + 2 (length) + 1 (initialHeading)
		// needs updating when curve data is written
		return 7;
	}

	/**
	 * Is this an arc within the RouteCenter?
	 */
	public boolean isInternal() {
		// we might check that setInternal has been called before
		return (flagB & FLAG_EXTERNAL) == 0;
	}

	public void setInternal(boolean internal) {
		if (internal)
			flagB &= ~FLAG_EXTERNAL;
		else
			flagB |= FLAG_EXTERNAL;
	}


	/**
	 * Set this arc's index into Table A.
	 */
	public void setIndexA(byte indexA) {
		this.indexA = indexA;
	}

	/**
	 * Get this arc's index into Table A.
	 *
	 * Required for writing restrictions (Table C).
	 */
	public byte getIndexA() {
		return indexA;
	}

	/**
	 * Set this arc's index into Table B. Applies to external arcs only.
	 */
	public void setIndexB(byte indexB) {
		assert !isInternal() : "Trying to set index on internal arc.";
		this.indexB = indexB;
	}

	/**
	 * Get this arc's index into Table B.
	 *
	 * Required for writing restrictions (Table C).
	 */
	public byte getIndexB() {
		return indexB;
	}
	 

	private static int convertMeters(double l) {
		// XXX: really a constant factor?
		// this factor derived by looking at a variety
		// of arcs in an IMG of Berlin; 1/4 of
		// what used to be here
		double factor = 3.28 / 16;
		return (int) (l * factor);
	}

	public void write(ImgFileWriter writer) {
		offset = writer.position();
		if(log.isDebugEnabled())
			log.debug("writing arc at", offset, ", flagA=", Integer.toHexString(flagA));

		// fetch destination class -- will have been set correctly by now
		setDestinationClass(dest.getNodeClass());

		// determine how to write length and curve bit
		int[] lendat = encodeLength();

		writer.put(flagA);

		if (isInternal()) {
			// space for 14 bit node offset, written in writeSecond.
			writer.put(flagB);
			writer.put((byte) 0);
		} else {
			writer.put((byte) (flagB | indexB));
		}

		writer.put(indexA);

		if(log.isDebugEnabled())
			log.debug("writing length", length);
		for (int aLendat : lendat)
			writer.put((byte) aLendat);

		writer.put((byte)(256 * initialHeading / 360));

		if (curve) {
			int[] curvedat = encodeCurve();
			for (int aCurvedat : curvedat)
				writer.put((byte) aCurvedat);
		}
	}

	/**
	 * Second pass over the nodes in this RouteCenter.
	 * Node offsets are now all known, so we can write the pointers
	 * for internal arcs.
	 */
	public void writeSecond(ImgFileWriter writer) {
		if (!isInternal())
			return;

		writer.position(offset + 1);
		char val = (char) (flagB << 8);
		int diff = dest.getOffsetNod1() - source.getOffsetNod1();
		assert diff < 0x2000 && diff >= -0x2000
			: "relative pointer too large for 14 bits (source offset = " + source.getOffsetNod1() + ", dest offset = " + dest.getOffsetNod1() + ")";
		val |= diff & 0x3fff;

		// We write this big endian
		if(log.isDebugEnabled())
			log.debug("val is", Integer.toHexString((int)val));
		writer.put((byte) (val >> 8));
		writer.put((byte) val);
	}

	/*
	 * length and curve flag are stored in a variety of ways, involving
	 * 1. flagA & 0x38 (3 bits)
	 * 2. 1-3 bytes following the possible Table A index
	 *
	 * There's even more different encodings supposedly.
	 */
	private int[] encodeLength() {
		// we'll just use a special encoding with curve=false for
		// now, 14 bits for length
		assert !curve : "not writing curve data yet";
		if (length >= (1 << 14)) {
			log.error("Way " + roadDef.getName() + " (id " + roadDef.getId() + ") contains an arc whose length is too big to be encoded so the road will not be routable");
			length = (1 << 14) - 1;
		}

		flagA |= 0x38; // all three bits set
		int[] lendat = new int[2]; // two bytes of data
		lendat[0] = 0x80 | (length & 0x3f); // 0x40 not set, 6 low bits of length
		lendat[1] = (length >> 6) & 0xff; // 8 more bits of length

		return lendat;
	}

	/**
	 * Encode the curve data into a sequence of bytes.
	 *
	 * 1 or 2 bytes show up in practice, but they're not at
	 * all well understood yet.
	 */
	private int[] encodeCurve() {
		assert !curve : "not writing curve data yet";
		return null;
	}

	public RoadDef getRoadDef() {
		return roadDef;
	}

	public void setNewDir() {
		flagA |= FLAG_NEWDIR;
	}

	public void setForward() {
		flagA |= FLAG_FORWARD;
	}

	public boolean isForward() {
		return (flagA & FLAG_FORWARD) != 0;
	}

	public void setLast() {
		flagB |= FLAG_LAST_LINK;
	}

	protected void setDestinationClass(int destinationClass) {
		if(log.isDebugEnabled())
			log.debug("setting destination class", destinationClass);
		flagA |= (destinationClass & MASK_DESTCLASS);
	}
}
