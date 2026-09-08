package com.osm2xp.core.parsers.btg;

/**
 * Internal BTG binary format constants shared by the parser and the writer.
 */
final class BtgConstants {

	static final int MAGIC = 0x5347;

	static final int VERSION_7 = 7;
	static final int VERSION_10 = 10;

	/** Geometry index type bits (bit position = component kind). */
	static final int INDEX_VERTEX = 0x01;
	static final int INDEX_NORMAL = 0x02;
	static final int INDEX_COLOR = 0x04;
	static final int INDEX_TEXCOORD = 0x08;

	private BtgConstants() {
	}
}
