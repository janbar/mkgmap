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
 * 
 * Author: Steve Ratcliffe
 * Create date: 20-Jan-2007
 */
package uk.me.parabola.mkgmap.general;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Zoom;
import uk.me.parabola.log.Logger;

import java.util.List;
import java.util.ArrayList;

/**
 * @author Steve Ratcliffe
 */
public class MapSplitter {
	private static final Logger log = Logger.getLogger(MapSplitter.class);

	private final MapDataSource mapSource;

	private static final int MAX_DIVISION_SIZE = 0x3fff;
//	private static final int MAX_DIVISION_SIZE = 0x7fff;

	// There is no good way of being sure of the absolute maximum number, we
	// choose a fairly low number for testing.
	private static final int MAX_FEATURE_NUMBER = 3000;//2000;

	// This is the zoom in terms of pixels per coordinate.  So 24 is the highest
	// zoom
	private int zoom;

	/**
	 * Creates a list of map areas and keeps splitting them down until they
	 * are small enough.  There is both a maximum size to an area and also
	 * a maximum number of things that will fit inside each division.
	 *
	 * Since these are not well defined (it all depends on how complicated the
	 * features are etc), we shall underestimate the maximum sizes and probably
	 * make them configurable.
	 *
	 * @param mapSource The input map data source.
	 * @param zoom The zoom level that we need to split for.
	 */
	public MapSplitter(MapDataSource mapSource, Zoom zoom) {
		this.mapSource = mapSource;
		this.zoom = zoom.getBitsPerCoord();
	}

	public MapSplitter(MapDataSource mapSource) {
		this.mapSource = mapSource;
	}

	public MapArea[] split() {
		Area bounds = mapSource.getBounds();
		log.debug("orig area", bounds);

		MapArea ma = initialArea(mapSource);
		MapArea[] areas = splitMaxSize(ma);

		// Now step through each area and see if any have too many map features
		// in them.  For those that do, we further split them.  This is done
		// recursively until everything fits.
		List<MapArea> alist = new ArrayList<MapArea>();
		addAreasToList(areas, alist);

		MapArea[] results = new MapArea[alist.size()];
		return alist.toArray(results);
	}

	private void addAreasToList(MapArea[] areas, List<MapArea> alist) {
		for (MapArea a : areas) {
			if (a.getPointCount() > MAX_FEATURE_NUMBER
					|| a.getLineCount() > MAX_FEATURE_NUMBER
					|| a.getShapeCount() > MAX_FEATURE_NUMBER)
			{
				log.debug("splitting area", a);
				MapArea[] sublist = a.split(2, 2);
				addAreasToList(sublist, alist);
			} else {
				log.debug("adding area unsplit");
				alist.add(a);
			}
		}
	}

	/**
	 * Split the area into portions that have the maximum size.  There is a
	 * maximum limit to the size of a subdivision (16 bits or about 1.4 degrees)
	 * we are choosing a limit smaller than the real max to allow for uncertaintly
	 * about what happens with features that extend beyond the box.
	 *
	 * If the area is already small enough then it will be returned unchanged.
	 *
	 * @param mapArea The area that needs to be split down.
	 * @return An array of map areas.  Each will be below the max size.
	 */
	private MapArea[] splitMaxSize(MapArea mapArea) {
		Area bounds = mapArea.getBounds();

		int width = bounds.getWidth();
		int height = bounds.getHeight();
		log.debug("width", width, "height", height);

		// There is an absolute maximum size that a division can be.  Make sure
		// that we are well inside that.
		int xsplit = 1;
		if (width > MAX_DIVISION_SIZE)
			xsplit = width/MAX_DIVISION_SIZE + 1;

		int ysplit = 1;
		if (height > MAX_DIVISION_SIZE)
			ysplit = height / MAX_DIVISION_SIZE + 1;

		MapArea[] areas = mapArea.split(xsplit, ysplit);
		return areas;
	}

	// divide into minimum size areas
	// allocate ways and points to each area
	// for each area
	//   if two many points or ways
	//      split into 4
	//      re-allocate to new areas
	//      continue

	/**
	 * The initial area contains all the features of the map.
	 *
	 * @param src The map data source.
	 * @return The initial map area covering the whole area and containing
	 * all the map features that are visible.
	 */
	private MapArea initialArea(MapDataSource src) {

		Area bounds = src.getBounds();
		MapArea ma = new MapArea(src);

		//ma.setPoints(src.getPoints());
		//ma.setLines(src.getLines());
		//ma.setShapes(src.getShapes());

		return ma;
	}
}
