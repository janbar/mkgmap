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
 * Create date: 10-Dec-2008
 */
package uk.me.parabola.mkgmap.osmstyle;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.general.LineAdder;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.scan.SyntaxException;
import uk.me.parabola.mkgmap.scan.TokenScanner;

/**
 * Reads the overlay file.
 * There are not many line types available in the version of the .img
 * format that we are using, but you can simulate more by clever use
 * of TYP files and overlaying lines on top of each other.
 *
 * The format of the file is just a series of lines that start with
 * the fake type and followed by a list of types that will actually
 * be created.
 *
 * Eg.
 * 0x123: 0x12, 0x14, 0x15
 *
 * If a rule results in the type 0x123 (which wouldn't normally show up)
 * it is replaced by three lines with the type 0x12, 0x14, 0x15.
 *
 * @author Steve Ratcliffe
 *
 */
public class OverlayReader {
	private final Map<Integer, List<Integer>> overlays = new HashMap<>();
	private final Reader reader;
	private final String filename;

	public OverlayReader(Reader r, String filename) {
		reader = r;
		this.filename = filename;
	}

	public void readOverlays() {
		TokenScanner ts = new TokenScanner(filename, reader);
		while (!ts.isEndOfFile()) {
			String line = ts.readLine();

			// Remove comments before parsing
			int commentstart = line.indexOf('#');
			if (commentstart != -1)
				line = line.substring(0, commentstart);

			String[] fields = line.split(":", 2);
			if (fields.length == 2) {
				try {
					overlays.put(Integer.decode(fields[0]), readReplacements(ts, fields[1]));
				} catch (NumberFormatException e) {
					throw new SyntaxException(ts, "Expecting a number");
				}
			}
		}
	}

	/**
	 * Read the line of replacements.
	 */
	private static List<Integer> readReplacements(TokenScanner ts, String line) {
		List<Integer> l = new ArrayList<>();

		String[] nums = line.split("[ ,]");
		for (String n : nums) {
			if (n == null || n.length() == 0) 
				continue;

			try {
				l.add(Integer.decode(n));
			} catch (NumberFormatException e) {
				throw new SyntaxException(ts, "List of numbers expected");
			}
		}

		return l;
	}

	public void addLine(MapLine line, LineAdder adder) {
		int origType = line.getType();
		List<Integer> integerList = overlays.get(origType);
		if (integerList != null) {
			MapLine newline = line.copy();
			newline.setType(integerList.get(0));
			List<Coord> points = line.getPoints();
			newline.setPoints(points);
			adder.add(newline);

			// Force all following types to be added as lines rather than roads.
			for (ListIterator<Integer> t=integerList.listIterator(1); t.hasNext(); ) {
				newline = new MapLine(line);
				newline.setType(t.next());
				newline.setPoints(new ArrayList<>(points));
				adder.add(newline);
			}
		} else {
			adder.add(line);
		}
	}

	public Map<Integer, List<Integer>> getOverlays() {
		return overlays;
	}
	

}
