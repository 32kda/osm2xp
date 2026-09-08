package com.osm2xp.core.parsers.btg;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Entry point for reading and writing FlightGear BTG terrain tiles.
 * <p>
 * The {@code read} methods transparently handle both plain {@code .btg} files
 * and gzip-compressed {@code .btg.gz} files, detecting the compression from the
 * stream magic bytes. The {@code write} methods produce plain or gzip-compressed
 * output depending on the file extension (or an explicit flag).
 * <p>
 * The raw parsing / writing logic lives in {@link BtgParser} and
 * {@link BtgWriter}.
 */
public final class Btg {

	private Btg() {
	}

	/**
	 * Reads a BTG tile from a file (either {@code .btg} or {@code .btg.gz}).
	 */
	public static BtgTile read(Path path) throws IOException {
		try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
			return read(input);
		}
	}

	/**
	 * Reads a BTG tile from a file (either {@code .btg} or {@code .btg.gz}).
	 */
	public static BtgTile read(File file) throws IOException {
		return read(file.toPath());
	}

	/**
	 * Reads a BTG tile from a stream, auto-detecting gzip compression. The stream
	 * is not closed by this method.
	 */
	public static BtgTile read(InputStream input) throws IOException {
		BufferedInputStream buffered = new BufferedInputStream(input);
		buffered.mark(2);
		int b0 = buffered.read();
		int b1 = buffered.read();
		buffered.reset();

		BtgParser parser = new BtgParserImpl();
		if (b0 == 0x1F && b1 == 0x8B) {
			try (GZIPInputStream gzip = new GZIPInputStream(buffered)) {
				return parser.parse(gzip);
			}
		}
		return parser.parse(buffered);
	}

	/**
	 * Writes a tile to a file, gzip-compressing it when the file name ends with
	 * {@code .gz}.
	 */
	public static void write(BtgTile tile, Path path) throws IOException {
		write(tile, path, isGzipPath(path));
	}

	/**
	 * Writes a tile to a file.
	 *
	 * @param gzip {@code true} to gzip-compress the output
	 */
	public static void write(BtgTile tile, Path path, boolean gzip) throws IOException {
		try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(path))) {
			write(tile, out, gzip);
		}
	}

	/**
	 * Writes a tile to a file, gzip-compressing it when the file name ends with
	 * {@code .gz}.
	 */
	public static void write(BtgTile tile, File file) throws IOException {
		write(tile, file.toPath());
	}

	/**
	 * Writes a tile to a file.
	 *
	 * @param gzip {@code true} to gzip-compress the output
	 */
	public static void write(BtgTile tile, File file, boolean gzip) throws IOException {
		write(tile, file.toPath(), gzip);
	}

	/**
	 * Writes a raw (uncompressed) tile to a stream. The stream is not closed by
	 * this method.
	 */
	public static void write(BtgTile tile, OutputStream out) throws IOException {
		new BtgWriterImpl().write(tile, out);
	}

	/**
	 * Writes a tile to a stream, optionally gzip-compressing it. The stream is not
	 * closed by this method.
	 */
	public static void write(BtgTile tile, OutputStream out, boolean gzip) throws IOException {
		if (!gzip) {
			write(tile, out);
			return;
		}
		GZIPOutputStream gzipOut = new GZIPOutputStream(out);
		new BtgWriterImpl().write(tile, gzipOut);
		gzipOut.finish();
		gzipOut.flush();
	}

	private static boolean isGzipPath(Path path) {
		String name = path.getFileName() == null ? "" : path.getFileName().toString();
		return name.toLowerCase(java.util.Locale.ROOT).endsWith(".gz");
	}
}
