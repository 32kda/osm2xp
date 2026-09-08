package com.osm2xp.core.parsers.btg;

import java.io.IOException;

/**
 * Signals a malformed or unsupported BTG file.
 * <p>
 * This package is intentionally free of any dependency on the rest of OSM2XP so
 * that it can be extracted and reused on its own.
 */
public class BtgException extends IOException {

	private static final long serialVersionUID = 1L;

	public BtgException(String message) {
		super(message);
	}

	public BtgException(String message, Throwable cause) {
		super(message, cause);
	}
}
