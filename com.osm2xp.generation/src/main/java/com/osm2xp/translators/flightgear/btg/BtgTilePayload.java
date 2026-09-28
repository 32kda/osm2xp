package com.osm2xp.translators.flightgear.btg;

import java.io.File;
import java.io.IOException;

import com.osm2xp.core.parsers.btg.BtgTile;
import com.osm2xp.translators.flightgear.FlightGearBucket;

/**
 * Mutable view of a single FlightGear terrain tile that flows through the
 * {@link BtgPatchPipeline}.
 * <p>
 * Raw (decompressed) bytes are the authoritative representation; the parsed
 * {@link BtgTile} model is an optional, lazily built view. This lets stages with
 * very different needs coexist:
 * <ul>
 * <li>surgical stages (e.g. vegetation) edit raw bytes and leave the triangle
 * mesh byte-for-byte untouched;</li>
 * <li>model stages (e.g. airfields) parse, mutate and replace the model, which
 * is re-serialized on demand.</li>
 * </ul>
 * Because stages are chained through the same payload and the pipeline writes
 * once, composition is guaranteed and there is never more than one mesh per
 * bucket.
 *
 * @author osm2xp
 */
public interface BtgTilePayload {

	/** The bucket (terrain tile) this payload belongs to. */
	FlightGearBucket bucket();

	/** Current decompressed tile bytes, reading them from disk on first use. */
	byte[] rawBytes() throws IOException;

	/** Current parsed tile model, parsing from the bytes on first use. */
	BtgTile model() throws IOException;

	/** Replaces the payload with the given raw bytes (surgical stages). */
	void setRawBytes(byte[] bytes);

	/** Replaces the payload with the given model (model stages). */
	void setModel(BtgTile tile);

	/** Whether the payload has been modified and needs to be written back. */
	boolean isDirty();

	/** Writes the current payload to the given file, gzip-compressing it. */
	void writeTo(File file) throws IOException;
}
