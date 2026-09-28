package com.osm2xp.translators.flightgear.btg;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.osm2xp.core.parsers.btg.Btg;
import com.osm2xp.core.parsers.btg.BtgParserImpl;
import com.osm2xp.core.parsers.btg.BtgTile;
import com.osm2xp.translators.flightgear.FlightGearBucket;

/**
 * File-backed {@link BtgTilePayload}. Keeps at most one representation in
 * memory ({@code bytes} or {@code model}); setting one invalidates the other.
 *
 * @author osm2xp
 */
public final class FileBtgTilePayload implements BtgTilePayload {

	private final FlightGearBucket bucket;
	private final File file;

	private byte[] bytes;
	private BtgTile model;
	private boolean dirty;

	public FileBtgTilePayload(FlightGearBucket bucket, File file) {
		this.bucket = bucket;
		this.file = file;
	}

	@Override
	public FlightGearBucket bucket() {
		return bucket;
	}

	@Override
	public byte[] rawBytes() throws IOException {
		if (bytes == null) {
			if (model != null) {
				bytes = serialize(model);
			} else {
				bytes = readDecompressed(file);
			}
		}
		return bytes;
	}

	@Override
	public BtgTile model() throws IOException {
		if (model == null) {
			// Parse from rawBytes() so the original bytes are retained for later
			// surgical stages (calling Btg.read(file) would leave bytes unset and
			// force a re-serialization of the untouched mesh).
			byte[] raw = rawBytes();
			model = new BtgParserImpl().parse(new ByteArrayInputStream(raw));
			if (model == null) {
				throw new IOException("Unable to parse BTG tile " + file);
			}
		}
		return model;
	}

	@Override
	public void setRawBytes(byte[] newBytes) {
		this.bytes = newBytes;
		this.model = null;
		this.dirty = true;
	}

	@Override
	public void setModel(BtgTile tile) {
		this.model = tile;
		this.bytes = null;
		this.dirty = true;
	}

	@Override
	public boolean isDirty() {
		return dirty;
	}

	@Override
	public void writeTo(File target) throws IOException {
		File parent = target.getParentFile();
		if (parent != null) {
			parent.mkdirs();
		}
		if (model != null) {
			Btg.write(model, target);
		} else if (bytes != null) {
			try (OutputStream out = new GZIPOutputStream(
					new FileOutputStream(target))) {
				out.write(bytes);
			}
		}
		dirty = false;
	}

	private static byte[] serialize(BtgTile tile) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Btg.write(tile, out);
		return out.toByteArray();
	}

	private static byte[] readDecompressed(File file) throws IOException {
		try (InputStream in = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
			in.mark(2);
			int b0 = in.read();
			int b1 = in.read();
			in.reset();
			InputStream source = (b0 == 0x1F && b1 == 0x8B) ? new GZIPInputStream(in) : in;
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			int read;
			while ((read = source.read(buffer)) >= 0) {
				out.write(buffer, 0, read);
			}
			return out.toByteArray();
		}
	}
}
