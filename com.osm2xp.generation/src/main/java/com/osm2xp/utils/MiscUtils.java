package com.osm2xp.utils;

import java.awt.Color;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;

import com.osm2xp.core.logging.Osm2xpLogger;

/**
 * MiscUtils.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class MiscUtils {

	/**
	 * extract numeric values from the given string
	 * 
	 * @param str
	 * @return String
	 */
	private static String getOnlyNumerics(String str) {
		
		str = StringUtils.stripToEmpty(str).trim();
		if (!Character.isDigit(str.charAt(0))) {
			return null;
		}
		int i = 1;
		while (i < str.length() && (Character.isDigit(str.charAt(i)) || str.charAt(i) == '.'))
			i++;
		while (str.charAt(i-1) == '.') 
			i--;
		if (i > 0) {
			return str.substring(0, i);
		}
		return null;
	}

	/**
	 * extract numeric values from the given string
	 * 
	 * @param value
	 * @return Integer
	 */
	public static Double extractNumber(String value) {
		value = value.replace(',', '.');
		value = getOnlyNumerics(value);
		try {
			if (value != null && !value.equals("")) {
				return Double.parseDouble(value);
			}
		} catch (Exception e) {
			Osm2xpLogger.warning(
					"Error extracting height from osm tag, input :" + value, e);
		}

		return null;
	}

	/**
	 * return a random integer beetween given min/max values
	 * 
	 * @param min
	 * @param max
	 * @return Integer
	 */
	public static Integer getRandomInt(Integer min, Integer max) {
		if (min.equals(max)) {
			return max;
		} else {
			Random randomGenerator = new Random();
			return randomGenerator.nextInt(max - min) + min;

		}

	}

	/**
	 * compute time difference beetween two dates
	 * 
	 * @param dateOne
	 * @param dateTwo
	 * @return String the time difference beetween two dates
	 */
	public static String getTimeDiff(Date dateOne, Date dateTwo) {
		long timeDiff = Math.abs(dateOne.getTime() - dateTwo.getTime());
		long hours = TimeUnit.MILLISECONDS.toHours(timeDiff);
		long minutes = TimeUnit.MILLISECONDS.toMinutes(timeDiff)
				- TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS
						.toHours(timeDiff));
		long seconds = TimeUnit.MILLISECONDS.toSeconds(timeDiff)
				- TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS
						.toMinutes(timeDiff));
		return hours + "h" + minutes + "m" + seconds + "s";
	}

	/**
	 * check if we are on a windows platform
	 * 
	 * @return true if we are on a windows platform
	 */
	public static boolean isWindows() {
		return System.getProperties().get("os.name").toString().toLowerCase()
				.contains("windows");
	}

	/**
	 * check if we are on a mac platform
	 * 
	 * @return true if we are on a windows platform
	 */
	public static boolean isMac() {
		return System.getProperties().get("os.name").toString().toLowerCase()
				.contains("mac");
	}

	/**
	 * execute an external programm
	 * 
	 * @param command
	 */
	public static void execProgram(String command) {
		try {
			Runtime rt = Runtime.getRuntime();

			Process proc = rt.exec(command);
			// any error message?
			StreamGobbler errorGobbler = new StreamGobbler(
					proc.getErrorStream(), "ERROR");

			// any output?
			StreamGobbler outputGobbler = new StreamGobbler(
					proc.getInputStream(), "OUTPUT");

			// kick them off
			errorGobbler.start();
			outputGobbler.start();

		} catch (Exception t) {
			Osm2xpLogger.log(t);
		}
	}

	/**
	 * Return the "distance" between two colors. The rgb entries are taken to be
	 * coordinates in a 3D space [0.0-1.0], and this method returnes the
	 * distance between the coordinates for the first and second color.
	 * 
	 * @param r1
	 *            , g1, b1 First color.
	 * @param r2
	 *            , g2, b2 Second color.
	 * @return Distance bwetween colors.
	 */
	public static double colorDistance(Color color1, Color color2) {
		double a = color2.getRed() - color1.getRed();
		double b = color2.getGreen() - color1.getGreen();
		double c = color2.getBlue() - color1.getBlue();

		return Math.sqrt(a * a + b * b + c * c);
	}
}
