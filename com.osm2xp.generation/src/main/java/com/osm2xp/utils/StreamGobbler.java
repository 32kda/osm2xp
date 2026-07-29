package com.osm2xp.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.osm2xp.core.logging.Osm2xpLogger;

/**
 * StreamGobbler.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class StreamGobbler extends Thread {
	InputStream is;
	String type;

	public StreamGobbler(InputStream is, String type) {
		this.is = is;
		this.type = type;
	}

	public void run() {
		try {
			InputStreamReader isr = new InputStreamReader(is);
			BufferedReader br = new BufferedReader(isr);
			String line = null;
			while ((line = br.readLine()) != null)
				System.out.println(type + ">" + line);
		} catch (IOException ioe) {
			Osm2xpLogger.log(ioe);
		}
	}
}
