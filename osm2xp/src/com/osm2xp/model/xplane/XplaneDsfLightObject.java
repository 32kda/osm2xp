package com.osm2xp.model.xplane;

import math.geom2d.Point2D;
import math.geom2d.polygon.LinearRing2D;

import com.osm2xp.exceptions.Osm2xpBusinessException;
import com.osm2xp.model.options.XplaneLightTagRule;
import com.osm2xp.model.osm.OsmPolygon;

/**
 * XplaneDsfObject.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class XplaneDsfLightObject extends XplaneDsfObject {

	private XplaneLightTagRule rule;

	public XplaneDsfLightObject(OsmPolygon poly, XplaneLightTagRule rule) {
		super(poly);
		this.rule = rule;
	}

	public String asObjDsfText() throws Osm2xpBusinessException {

		StringBuffer sb = new StringBuffer();
		Point2D center;
		if (this.getRule().getOffset() > 0) {
			center = getOffsetPoint();
		} else {
			center = getOsmPolygon().getCenter();
		}
		sb.append("OBJECT " + this.dsfIndex + " "
				+ this.osmPolygon.getCenter().x + " " + center.y + " 0 "
				+ this.rule.getHeight());
		sb.append(System.getProperty("line.separator"));
		return sb.toString();
	}

	private Point2D getOffsetPoint() {
		float offset = (float) this.getRule().getOffset() / 100000;
		Point2D center = this.getOsmPolygon().getCenter();
		LinearRing2D poly = this.getOsmPolygon().getPolygon();
		while (poly.isInside(center)) {
			center = center.translate(offset, offset);
		}
		return center;

	}

	public XplaneLightTagRule getRule() {
		return rule;
	}

	public void setRule(XplaneLightTagRule rule) {
		this.rule = rule;
	}

}
