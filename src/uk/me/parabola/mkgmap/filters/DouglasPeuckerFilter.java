/*
 * Copyright (C) 2007 Steve Ratcliffe
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
 */
package uk.me.parabola.mkgmap.filters;

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.general.MapElement;
import uk.me.parabola.mkgmap.general.MapLine;

/**
 * This is a filter that smooths out lines at low resolutions. If the element
 * has no size at all at the given resolution, then it is not passed on down
 * the chain at all is excluded from the map at that resolution.
 */
public class DouglasPeuckerFilter implements MapFilter {

	//private static final double ERROR_DISTANCE = 5.4 / 2;	//One unit is 5.4 m, so error dist is 2.6m
															//Can be increased more, but may lead to artifacts on T-crossings
	private final double filterDistance;
	private double maxErrorDistance;
	private int resolution;

	public DouglasPeuckerFilter(double filterDistance) {
		this.filterDistance = filterDistance;
	}

	@Override
	public void init(FilterConfig config) {
		this.resolution = config.getResolution();
		this.maxErrorDistance = filterDistance * (1<< config.getShift());
	}

	/**
	 * This applies to both lines and polygons.  We are going to smooth out
	 * the points in the line so that you do not get jaggies. 
	 *
	 * @param element A map element that will be a line or a polygon.
	 * @param next This is used to pass the possibly transformed element onward.
	 */
	@Override
	public void doFilter(MapElement element, MapFilterChain next) {
		// First off we don't touch things if at the highest level of detail
		if (resolution == 24) {
			// XXX 24 is not necessarily the highest level.
			next.doFilter(element);
			return;
		}

		MapLine line = (MapLine) element;

		List<Coord> points = line.getPoints();

		// Create a new list to rewrite the points into. Don't alter the original one
		List<Coord> coords = new ArrayList<>(points.size());
		coords.addAll(points);
		// Loop runs downwards, as the list length gets modified while running
		int endIndex = coords.size()-1;
		for(int i = endIndex-1; i > 0; i--) {
			Coord p = coords.get(i);
			if (p.preserved()) {
				// point is "preserved", don't remove it
				douglasPeucker(coords, i, endIndex, maxErrorDistance);
				endIndex = i;
			}
		}
		// Simplify the rest
		douglasPeucker(coords, 0, endIndex, maxErrorDistance);
		if (coords.size() == points.size())
			next.doFilter(line); // nothing changed, no need to copy 
		else {
			MapLine newline = line.copy();
			newline.setPoints(coords);
			next.doFilter(newline);
		}
	}

	/**
	 * Reduces point density by Douglas-Peucker algorithm
	 *
	 * @param points The list of points to simplify.
	 * @param startIndex First index of segment. The point with this index will not be changed
	 * @param endIndex Last index of segment. The point with this index will not be changed
	 * @param allowedError Maximal allowed error to be introduced by simplification. 
	 * returns number of removed points.
	 */
	public static void douglasPeucker(List<Coord> points, int startIndex, int endIndex, double allowedError)
	{
		if (endIndex - startIndex <= 1) {
			return;
		}
		
		double maxDistance = 0;		//Highest distance	
		int maxIndex = endIndex;	//Index of highest distance

		Coord a = points.get(startIndex);
		Coord b = points.get(endIndex);

		// Find point with highest distance to line between start- and end-point.
		for(int i = endIndex-1; i > startIndex; i--) {
			Coord p = points.get(i);
			double distance = p.shortestDistToLineSegment(a, b);
			if (distance > maxDistance) {
				maxDistance = distance;
				maxIndex = i;
			}
		}
		if (maxDistance > allowedError) {
			// Call recursive for both parts
			douglasPeucker(points, maxIndex, endIndex, allowedError);		
			douglasPeucker(points, startIndex, maxIndex, allowedError);		
		}
		else {
			// All points in tolerance, delete all of them.
			if (endIndex - startIndex > 4) {
				// faster than many repeated remove actions
				points.subList(startIndex + 1, endIndex).clear();
			} else {
				for (int i = endIndex - 1; i > startIndex; i--) {
					points.remove(i);
				}
			}
		}
	}
}
