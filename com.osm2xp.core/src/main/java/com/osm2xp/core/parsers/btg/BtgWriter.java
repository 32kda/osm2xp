package com.osm2xp.core.parsers.btg;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Writes a BTG scenery tile to a raw (uncompressed) output stream.
 * <p>
 * Implementations are stateless and reusable. Use {@link Btg} for the
 * file-oriented convenience methods that transparently handle
 * {@code .btg.gz} compression.
 */
public interface BtgWriter {

	/**
	 * Writes a BTG tile.
	 *
	 * @param tile the tile to write
	 * @param out  stream to write the uncompressed BTG file to; it is not closed
	 *             by the writer
	 * @throws IOException if the stream cannot be written to
	 */
	void write(BtgTile tile, OutputStream out) throws IOException;
}
