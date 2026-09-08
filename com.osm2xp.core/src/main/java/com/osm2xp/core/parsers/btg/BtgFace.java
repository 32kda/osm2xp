package com.osm2xp.core.parsers.btg;

/**
 * Immutable triangle of a parsed BTG tile.
 * <p>
 * Vertex indices reference {@link BtgTile#getVertices()}. Normal indices
 * reference {@link BtgTile#getNormals()} and texture coordinate indices
 * reference {@link BtgTile#getTexCoords()}; a value of {@code -1} means the
 * triangle has no normal / texture coordinate for that corner.
 */
public final class BtgFace {

	private final int a;
	private final int b;
	private final int c;
	private final int normalA;
	private final int normalB;
	private final int normalC;
	private final int texA;
	private final int texB;
	private final int texC;
	private final String material;

	public BtgFace(int a, int b, int c, int texA, int texB, int texC, String material) {
		this(a, b, c, -1, -1, -1, texA, texB, texC, material);
	}

	public BtgFace(int a, int b, int c, int normalA, int normalB, int normalC, int texA, int texB, int texC,
			String material) {
		this.a = a;
		this.b = b;
		this.c = c;
		this.normalA = normalA;
		this.normalB = normalB;
		this.normalC = normalC;
		this.texA = texA;
		this.texB = texB;
		this.texC = texC;
		this.material = material == null ? "" : material;
	}

	public int getA() {
		return a;
	}

	public int getB() {
		return b;
	}

	public int getC() {
		return c;
	}

	public int getNormalA() {
		return normalA;
	}

	public int getNormalB() {
		return normalB;
	}

	public int getNormalC() {
		return normalC;
	}

	public int getTexA() {
		return texA;
	}

	public int getTexB() {
		return texB;
	}

	public int getTexC() {
		return texC;
	}

	/** Material name for this face (never {@code null}, may be empty). */
	public String getMaterial() {
		return material;
	}

	/** {@code true} when every corner has a valid normal index. */
	public boolean hasNormals() {
		return normalA >= 0 && normalB >= 0 && normalC >= 0;
	}

	/** {@code true} when every corner has a valid texture coordinate index. */
	public boolean hasTexCoords() {
		return texA >= 0 && texB >= 0 && texC >= 0;
	}

	@Override
	public String toString() {
		return "Face[" + a + ", " + b + ", " + c + "] material=" + material;
	}
}
