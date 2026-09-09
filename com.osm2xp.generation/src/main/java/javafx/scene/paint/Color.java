package javafx.scene.paint;

/**
 * Minimal headless stub of {@code javafx.scene.paint.Color}.
 * <p>
 * The bundled JCSG library ({@code eu.mihosoft.jcsg}) references this class only
 * for a cosmetic {@code "material:color"} property (a static colour array and
 * {@code getRed/getGreen/getBlue} in {@code PropertyStorage}). The actual CSG
 * boolean operations (union/difference/intersect) do not depend on JavaFX, so
 * this tiny stub lets JCSG run without the JavaFX runtime on the classpath.
 */
public class Color {

	public static final Color RED = new Color(1.0, 0.0, 0.0);
	public static final Color YELLOW = new Color(1.0, 1.0, 0.0);
	public static final Color GREEN = new Color(0.0, 1.0, 0.0);
	public static final Color BLUE = new Color(0.0, 0.0, 1.0);
	public static final Color MAGENTA = new Color(1.0, 0.0, 1.0);
	public static final Color WHITE = new Color(1.0, 1.0, 1.0);
	public static final Color BLACK = new Color(0.0, 0.0, 0.0);
	public static final Color GRAY = new Color(0.5, 0.5, 0.5);
	public static final Color ORANGE = new Color(1.0, 0.5, 0.0);

	private final double red;
	private final double green;
	private final double blue;

	public Color(double red, double green, double blue) {
		this.red = red;
		this.green = green;
		this.blue = blue;
	}

	public double getRed() {
		return red;
	}

	public double getGreen() {
		return green;
	}

	public double getBlue() {
		return blue;
	}
}
