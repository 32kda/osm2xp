package com.osm2xp.generation.dsf;

/**
 * Unchecked exception thrown on any error during DSF/DSFTool conversion.
 */
public class DsfConversionException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public DsfConversionException(String message) {
		super(message);
	}

	public DsfConversionException(String message, Throwable cause) {
		super(message, cause);
	}
}
