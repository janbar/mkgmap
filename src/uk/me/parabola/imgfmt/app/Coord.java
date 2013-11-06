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
 * Create date: 11-Dec-2006
 */
package uk.me.parabola.imgfmt.app;

import java.util.Locale;

import uk.me.parabola.imgfmt.Utils;

/**
 * A point coordinate in unshifted map-units.
 * A map unit is 360/2^24 degrees.  In some places <i>shifted</i> coordinates
 * are used, which means that they are divided by some power of two to save
 * space in the file.
 *
 * You can create one of these with lat/long by calling the constructor with
 * double args.
 *
 * This is an immutable class.
 *
 * @author Steve Ratcliffe
 */
public class Coord implements Comparable<Coord> {
	private final static byte ON_BOUNDARY_MASK = 0x01; // bit in flags is true if point lies on a boundary
	private final static byte PRESERVED_MASK = 0x02; // bit in flags is true if point should not be filtered out
	private final static byte REPLACED_MASK = 0x04;  // bit in flags is true if point was replaced 
	private final static byte TREAT_AS_NODE_MASK = 0x08; // bit in flags is true if point should be treated as a node
	private final static byte FIXME_NODE_MASK = 0x10; // bit in flags is true if a node with this coords has a fixme tag
	private final int latitude;
	private final int longitude;
	private byte highwayCount; // number of highways that use this point
	private byte flags; // further attributes
	private final byte latDelta; // delta to 30 bit lat value 
	private final byte lonDelta; // delta to 30 bit lon value

	/**
	 * Construct from co-ordinates that are already in map-units.
	 * @param latitude The latitude in map units.
	 * @param longitude The longitude in map units.
	 */
	public Coord(int latitude, int longitude) {
		this.latitude = latitude;
		this.longitude = longitude;
		latDelta = lonDelta = 0;
	}

	/**
	 * Construct from regular latitude and longitude.
	 * @param latitude The latitude in degrees.
	 * @param longitude The longitude in degrees.
	 */
	public Coord(double latitude, double longitude) {
		this.latitude = Utils.toMapUnit(latitude);
		this.longitude = Utils.toMapUnit(longitude);
		int lat30 = toBit30(latitude);
		int lon30 = toBit30(longitude);
		this.latDelta = (byte) ((this.latitude << 6) - lat30); 
		this.lonDelta = (byte) ((this.longitude << 6) - lon30);
		
		// verify math
		assert (this.latitude << 6) - latDelta == lat30;
		assert (this.longitude << 6) - lonDelta == lon30;

	}
	
	private Coord (int lat, int lon, byte latDelta, byte lonDelta){
		this.latitude = lat;
		this.longitude = lon;
		this.latDelta = latDelta;
		this.lonDelta = lonDelta;
	}
	
	public Coord makeHighPrecCoord(int lat30, int lon30){
		int lat24 = (lat30 + (1 << 5)) >> 6;  
		int lon24 = (lon30 + (1 << 5)) >> 6;
		byte dLat = (byte) ((lat24 << 6) - lat30);
		byte dLon = (byte) ((lon24 << 6) - lon30);
		return new Coord(lat24,lon24,dLat,dLon);
	}
	
	/**
	 * Construct from other coord instance, copies 
	 * the lat/lon values in high precision
	 * @param other
	 */
	public Coord(Coord other) {
		this.latitude = other.latitude;
		this.longitude = other.longitude;
		this.latDelta = other.latDelta;
		this.lonDelta = other.lonDelta;
	}

	public int getLatitude() {
		return latitude;
	}

	public int getLongitude() {
		return longitude;
	}

	public long getId() {
		return 0;
	}

	public int getHighwayCount() {
		return highwayCount;
	}

	public void incHighwayCount() {
		// don't let it wrap
		if(highwayCount < Byte.MAX_VALUE)
			++highwayCount;
	}

	public boolean getOnBoundary() {
		return (flags & ON_BOUNDARY_MASK) != 0;
	}

	public void setOnBoundary(boolean onBoundary) {
		if (onBoundary) 
			this.flags |= ON_BOUNDARY_MASK;
		else 
			this.flags &= ~ON_BOUNDARY_MASK; 
	}

	public boolean preserved() {
		return (flags & PRESERVED_MASK) != 0;
	}

	public void preserved(boolean preserved) {
		if (preserved) 
			this.flags |= PRESERVED_MASK;
		else 
			this.flags &= ~PRESERVED_MASK; 
	}

	/**
	 * Returns if this coord was marked to be replaced in short arc removal.
	 * @return True means the replacement has to be looked up.
	 */
	public boolean isReplaced() {
		return (flags & REPLACED_MASK) != 0;
	}

	/**
	 * Mark a point as replaced in short arc removal process.
	 * @param replaced true or false
	 */
	public void setReplaced(boolean replaced) {
		if (replaced) 
			this.flags |= REPLACED_MASK;
		else 
			this.flags &= ~REPLACED_MASK; 
	}

	/** 
	 * Should this Coord be treated like a Garmin node in short arc removal?
	 * The value has no meaning outside of short arc removal.
	 * @return true if this coord should be treated like a Garmin node, else false
	 */
	public boolean isTreatAsNode() {
		return (flags & TREAT_AS_NODE_MASK) != 0;
	}

	/**
	 * Mark the Coord to be treated like a Node in short arc removal 
	 * @param treatAsNode true or false
	 */
	public void setTreatAsNode(boolean treatAsNode) {
		if (treatAsNode) 
			this.flags |= TREAT_AS_NODE_MASK;
		else 
			this.flags &= ~TREAT_AS_NODE_MASK; 
	}

	/**
	 * Does this coordinate belong to a node with a fixme tag?
	 * Note that the value is set after evaluating the points style. 
	 * @return true if the fixme flag is set, else false
	 */
	public boolean isFixme() {
		return (flags & FIXME_NODE_MASK) != 0;
	}
	
	public void setFixme(boolean b) {
		if (b) 
			this.flags |= FIXME_NODE_MASK;
		else 
			this.flags &= ~FIXME_NODE_MASK; 
	}
	
	public int hashCode() {
		// Use a factor for latitude to span over the whole integer range:
		// max lat: 4194304
		// max lon: 8388608
		// max hashCode: 2118123520 < 2147483647 (Integer.MAX_VALUE)
		return 503 * latitude + longitude;
	}

	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof Coord))
			return false;
		Coord other = (Coord) obj;
		return latitude == other.latitude && longitude == other.longitude;
	}

	/**
	 * Distance to other point in meters.
	 */
	public double distance(Coord other) {
		return 40075000 * Math.sqrt(distanceInDegreesSquared(other)) / 360;
	}

	public double distanceInDegreesSquared(Coord other) {
		if (equals(other))
			return 0;

		double lat1 = int30ToDegrees(getHighPrecLat());
		double lat2 = int30ToDegrees(other.getHighPrecLat());
		double long1 = int30ToDegrees(getHighPrecLon());
		double long2 = int30ToDegrees(other.getHighPrecLon());
				
		double latDiff;
		if (lat1 < lat2)
			latDiff = lat2 - lat1;
		else
			latDiff = lat1 - lat2;	
		if (latDiff > 90)
			latDiff -= 180;

		double longDiff;
		if (long1 < long2)
			longDiff = long2 - long1;
		else
			longDiff = long1 - long2;
		if (longDiff > 180)
			longDiff -= 360;

		// scale longDiff by cosine of average latitude
		longDiff *= Math.cos(Math.PI / 180 * Math.abs((lat1 + lat2) / 2));

		return (latDiff * latDiff) + (longDiff * longDiff);
	}

	public Coord makeBetweenPoint(Coord other, double fraction) {
		int lat30 = (int) (getHighPrecLat() + (other.getHighPrecLat() - getHighPrecLat()) * fraction);
		int lon30 = (int) (getHighPrecLon() + (other.getHighPrecLon() - getHighPrecLon()) * fraction);
		return makeHighPrecCoord(lat30, lon30);
	}

	
	// returns bearing (in degrees) from current point to another point
	public double bearingTo(Coord point) {
		// use high precision values for this 
		double lat1 = int30ToRadians(getHighPrecLat());
		double lat2 = int30ToRadians(point.getHighPrecLat());
		double lon1 = int30ToRadians(getHighPrecLon());
		double lon2 = int30ToRadians(point.getHighPrecLon());
		
		double dlon = lon2 - lon1;

		double y = Math.sin(dlon) * Math.cos(lat2);
		double x = Math.cos(lat1)*Math.sin(lat2) -
			Math.sin(lat1)*Math.cos(lat2)*Math.cos(dlon);
		return Math.atan2(y, x) * 180 / Math.PI;
	}

	/**
	 * Sort lexicographically by longitude, then latitude.
	 *
	 * This ordering is used for sorting entries in NOD3.
	 */
	public int compareTo(Coord other) {
		if (longitude == other.getLongitude())
			if (latitude == other.getLatitude()) return 0;
			else return latitude > other.getLatitude() ? 1 : -1;
		else
			return longitude > other.getLongitude()? 1: -1;
	}			

	/**
	 * Returns a string representation of the object.
	 *
	 * @return a string representation of the object.
	 */
	public String toString() {
		return (latitude) + "/" + (longitude);
	}

	public String toDegreeString() {
		return String.format(Locale.ENGLISH, "%.5f/%.5f",
			Utils.toDegrees(latitude),
			Utils.toDegrees(longitude)).toString();
	}

	protected String toOSMURL(int zoom) {
		return ("http://www.openstreetmap.org/?mlat=" +
				String.format(Locale.ENGLISH, "%.5f", Utils.toDegrees(latitude)) +
				"&mlon=" +
				String.format(Locale.ENGLISH, "%.5f", Utils.toDegrees(longitude)) +
				"&zoom=" +
				zoom);
	}

	public String toOSMURL() {
		return toOSMURL(17);
	}

	/**
	 * Convert latitude or longitude to 30 bits value.
	 * This allows higher precision than the 24 bits
	 * used in map units.
	 * @param l The lat or long as decimal degrees.
	 * @return An integer value with 30 bit precision.
	 */
	private static int toBit30(double l) {
		double DELTA = 360.0D / (1 << 30) / 2; //Correct rounding
		if (l > 0)
			return (int) ((l + DELTA) * (1 << 30)/360);
		else
			return (int) ((l - DELTA) * (1 << 30)/360);
		
	}

	/* Factor for conversion to radians using 30 bits
	 * (Math.PI / 180) * (360.0 / (1 << 30)) 
	 */
	final static double BIT30_RAD_FACTOR = 2 * Math.PI / (1 << 30);
	
	/**
	 * Convert to radians using high precision 
	 * @param val30 a longitude/latitude value with 30 bit precision
	 * @return an angle in radians.
	 */
	private static double int30ToRadians(int val30){
		return (double) val30 * BIT30_RAD_FACTOR;
	}

	private static double int30ToDegrees(int val30){
		return (double) val30 * (360.0 / (1 << 30));
	}
	
	public int getHighPrecLat() {
		return (latitude << 6) - latDelta;
	}

	public int getHighPrecLon() {
		return (longitude << 6) - lonDelta;
	}
}
