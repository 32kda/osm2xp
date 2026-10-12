package com.osm2xp.generation.dsf;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Facade for converting between DSFTool text files and binary DSF files.
 */
public final class DsfConverter {

	private DsfConverter() {
	}

	public static DsfConversionResult textToBinary(Path textFile, Path dsfFile) {
		DsfWriter writer = DsfTextParser.read(textFile);
		try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(dsfFile))) {
			writer.writeTo(out);
		} catch (IOException e) {
			throw new DsfConversionException("Error writing DSF file " + dsfFile, e);
		}
		return new DsfConversionResult(writer.getSkippedPolygons(), writer.getSkippedObjects(),
				writer.getSkippedChains());
	}

	public static byte[] textToBinary(Path textFile) {
		DsfWriter writer = DsfTextParser.read(textFile);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.writeTo(out);
		return out.toByteArray();
	}

	public static void binaryToText(Path dsfFile, Path textFile) {
		try (InputStream in = new BufferedInputStream(Files.newInputStream(dsfFile));
				BufferedWriter writer = Files.newBufferedWriter(textFile, StandardCharsets.UTF_8)) {
			binaryToText(readAll(in), writer);
		} catch (IOException e) {
			throw new DsfConversionException("Error converting DSF file " + dsfFile, e);
		}
	}

	public static String binaryToText(byte[] dsfData) {
		StringWriter writer = new StringWriter();
		binaryToText(dsfData, writer);
		return writer.toString();
	}

	public static void binaryToText(InputStream dsfStream, Writer writer) {
		binaryToText(readAll(dsfStream), writer);
	}

	private static void binaryToText(byte[] dsfData, Writer writer) {
		DsfTextEmitter emitter = new DsfTextEmitter(writer);
		emitter.writeHeader();
		new DsfReader(dsfData).read(emitter);
		emitter.finish();
	}

	private static byte[] readAll(InputStream in) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			int read;
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
			return out.toByteArray();
		} catch (IOException e) {
			throw new DsfConversionException("Error reading DSF data", e);
		}
	}
}
