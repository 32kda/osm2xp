package com.osm2xp.generation.dsf;

public final class DsfRasterHeader {

	public final int version;
	public final int bytesPerPixel;
	public final int flags;
	public final long width;
	public final long height;
	public final float scale;
	public final float offset;

	public DsfRasterHeader(int version, int bytesPerPixel, int flags, long width, long height, float scale, float offset) {
		this.version = version;
		this.bytesPerPixel = bytesPerPixel;
		this.flags = flags;
		this.width = width;
		this.height = height;
		this.scale = scale;
		this.offset = offset;
	}
}
