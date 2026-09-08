package com.osm2xp.core.parsers.btg;

import java.io.IOException;
import java.io.InputStream;

/**
 * Parses a BTG scenery tile from a raw (decompressed) input stream.
 * <p>
 * Implementations are stateless and reusable. Use {@link Btg} for the
 * file-oriented convenience methods that transparently handle
 * {@code .btg.gz} compression.
 */
public interface BtgParser {

	/**
	 * Parses a raw BTG tile.
	 *
	 * @param input stream positioned at the start of an uncompressed BTG file;
	 *            the stream is read to the end but not closed by the parser
	 * @return the parsed tile
	 * @throws IOException      if the stream cannot be read
	 * @throws BtgException     if the data is not a valid BTG tile
	 */
	BtgTile parse(InputStream input) throws IOException;
}
