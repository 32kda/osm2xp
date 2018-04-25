package com.osm2xp.parsers.tilesLister;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import math.geom2d.Point2D;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import com.osm2xp.exceptions.Osm2xpBusinessException;

/**
 * Permet de lister les tuiles existantes dans le fichier osm
 * 
 * @author Benjamin Blanchet
 * 
 */
public class XmlTilesLister implements ContentHandler, TilesLister {

	private Set<Point2D> tilesList = new HashSet<Point2D>();
	private File file;

	public XmlTilesLister(File file) {
		this.file = file;
	}

	/**
	 * @return the tilesList
	 * @throws Osm2xpBusinessException
	 */
	public Set<Point2D> getTilesList() {
		return tilesList;
	}

	/**
	 * 
	 * @param uri
	 * @throws Osm2xpBusinessException
	 * @throws Exception
	 */
	public void parseDocument() throws Osm2xpBusinessException {
		XMLReader saxReader = null;

		try {
			saxReader = XMLReaderFactory.createXMLReader();
			saxReader.setContentHandler(this);
			saxReader.parse(file.getAbsolutePath());
		} catch (SAXException e) {
			throw new Osm2xpBusinessException(e.getMessage());
		} catch (IOException e) {
			throw new Osm2xpBusinessException(e.getMessage());
		}

	}

	public void startElement(String nameSpaceURI, String localName,
			String rawName, Attributes attributs) throws SAXException {

		if (localName.equalsIgnoreCase("node")
				&& attributs.getValue("lat") != null
				&& attributs.getValue("lon") != null) {
			Point2D loc = new Point2D(Double.parseDouble(attributs
					.getValue("lon")), Double.parseDouble(attributs
					.getValue("lat")));
			int lat = (int) Math.floor(loc.y);
			int lon = (int) Math.floor(loc.x);
			Point2D cleanedLoc = new Point2D(lon, lat);
			tilesList.add(cleanedLoc);
		}
	}

	public void setDocumentLocator(Locator locator) {

	}

	public void startDocument() throws SAXException {

	}

	public void endDocument() throws SAXException {

	}

	public void startPrefixMapping(String prefix, String uri)
			throws SAXException {

	}

	public void endPrefixMapping(String prefix) throws SAXException {

	}

	public void endElement(String uri, String localName, String qName)
			throws SAXException {

	}

	public void characters(char[] ch, int start, int length)
			throws SAXException {

	}

	public void ignorableWhitespace(char[] ch, int start, int length)
			throws SAXException {

	}

	public void processingInstruction(String target, String data)
			throws SAXException {

	}

	public void skippedEntity(String name) throws SAXException {

	}

	public void process() throws Osm2xpBusinessException {
		this.parseDocument();

	}

	public void complete() {

	}

}
