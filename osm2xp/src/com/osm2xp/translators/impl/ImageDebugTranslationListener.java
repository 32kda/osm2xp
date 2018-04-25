package com.osm2xp.translators.impl;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import com.osm2xp.gui.Activator;
import com.osm2xp.model.osm.OsmPolygon;
import com.osm2xp.translators.IPolyHandler;
import com.osm2xp.translators.ITranslationListener;
import com.osm2xp.translators.xplane.XPBarrierTranslator;
import com.osm2xp.translators.xplane.XPPowerlineTranslator;
import com.osm2xp.translators.xplane.XPRailTranslator;
import com.osm2xp.translators.xplane.XPRoadTranslator;
import com.osm2xp.utils.helpers.GuiOptionsHelper;

import math.geom2d.Box2D;
import math.geom2d.Point2D;

/**
 * 
 * @author 32kda
 * 
 * <b>Important</b> x and y should be switched while obtaining, since y should be latitude, x - longtitude. 
 * Otherwise map appears rotated and squished (because of bad longtitude scaling)
 * Seems to be a bug in osm2xp code
 */
public class ImageDebugTranslationListener implements ITranslationListener {
	
	private static final int IMGSIZE_X = 2048;
	private static final int IMGSIZE_Y = 2048;
	BufferedImage image = new BufferedImage(IMGSIZE_X,IMGSIZE_Y, BufferedImage.TYPE_INT_ARGB);
	Graphics2D g2d = (Graphics2D) image.getGraphics();
	double baseX = Double.MIN_VALUE;
	double baseY = Double.MIN_VALUE;
	double latScale = 111000; //1 degree ~ 111km, but need recals for lat/long
	double longScale = latScale;
	
	public ImageDebugTranslationListener() {
		g2d.setColor(Color.WHITE);
		g2d.fillRect(0,0,IMGSIZE_X, IMGSIZE_Y);
	}

	@Override
	public void processBuilding(OsmPolygon polygon, Integer facade) {
		processPoly(polygon, Color.RED);
	}
	
	protected void processPoly(OsmPolygon polygon, Color color) {
		checkSetBase(polygon);
		g2d.setColor(color);
		Polygon drawPoly = calculateDrawPoly(polygon);
		if (drawPoly != null) {
			g2d.fillPolygon(drawPoly);
		}
	}

	protected Polygon calculateDrawPoly(OsmPolygon polygon) {
		Polygon drawPoly = null;
		Point2D center = polygon.getCenter();
		int drawCenterX = (int)(longScale * (center.x - baseX));
		int drawCenterY = (int)(latScale * (center.y - baseY));
		if (drawCenterX >= 0 && drawCenterX < IMGSIZE_X && drawCenterY >= 0 && drawCenterY < IMGSIZE_Y) {
			Shape awtShape = polygon.getPolygon().getAsAWTShape();
			Point2D[] pointArray = polygon.getPolygon().getPointArray();
			drawPoly = new Polygon();
			for (int i = 0; i < pointArray.length; i++) {
				drawPoly.addPoint( (int)(longScale * (pointArray[i].x - baseX)), IMGSIZE_Y - (int) (latScale * (pointArray[i].y - baseY)));
			}
		}
		return drawPoly;
	}
	
	protected Path2D calculateDrawPath(OsmPolygon polygon) {
		Point2D[] pointArray = polygon.getPolygon().getPointArray();
		Path2D.Double drawPath = new Path2D.Double();
		if (pointArray.length > 0) {
			drawPath.moveTo((int)(longScale * (pointArray[0].x - baseX)), IMGSIZE_Y - (int) (latScale * (pointArray[0].y - baseY)));
			for (int i = 1; i < pointArray.length; i++) {
				drawPath.lineTo( (int)(longScale * (pointArray[i].x - baseX)), IMGSIZE_Y - (int) (latScale * (pointArray[i].y - baseY)));
			}
		}
		if (drawPath.intersects(0,0,IMGSIZE_X,IMGSIZE_Y)) {
			return drawPath;
		}
		return null;
	}

	protected void checkSetBase(OsmPolygon polygon) {
		if (baseX == Double.MIN_VALUE) {
			Box2D boundingBox = polygon.getPolygon().getBoundingBox();
			baseX =  boundingBox.getMinX();
			baseY =  boundingBox.getMinY();
			longScale = latScale * Math.cos(Math.toRadians(baseY));
		}
	}

	@Override
	public void process3dObject(OsmPolygon polygon) {
		checkSetBase(polygon);

	}

	@Override
	public void processStreetLights(OsmPolygon polygon) {
		checkSetBase(polygon);

	}

	@Override
	public void processForest(OsmPolygon polygon) {
		processPoly(polygon, Color.GREEN);
	}

	@Override
	public void complete() {
		g2d.dispose();
		try {
			File inputFile = new File(GuiOptionsHelper.getOptions()
					.getCurrentFilePath());
			ImageIO.write(image, "png", getTestPngName(inputFile));
		} catch (IOException e) {
			Activator.log(e);
		}
	}

	protected File getTestPngName(File inputFile) {
		int i = 0;
		File curFile;
		while((curFile = new File(inputFile.getParentFile(), GuiOptionsHelper.getSceneName() + i + ".png")).exists()) {
			i++;
		}
		return curFile;
	}

	@Override
	public void polyProcessed(OsmPolygon poly, IPolyHandler handler) {
		checkSetBase(poly);
		setDrawingType(handler);
		Shape drawPath = calculateDrawPath(poly);
		if (drawPath != null) {
			g2d.draw(drawPath);
		}
		g2d.setStroke(new BasicStroke(1));
	}

	protected void setDrawingType(IPolyHandler handler) {
		Color color = Color.BLACK;
		if (handler instanceof XPRailTranslator) {
			color = Color.GRAY;
			g2d.setStroke(new BasicStroke(4));
		}
		if (handler instanceof XPBarrierTranslator) {
			color = Color.CYAN;
			g2d.setStroke(new BasicStroke(1));
		}
		if (handler instanceof XPPowerlineTranslator) {
			color = Color.BLUE;
			g2d.setStroke(new BasicStroke(2));
		}
		if (handler instanceof XPRoadTranslator) {
			color = Color.BLACK;
			g2d.setStroke(new BasicStroke(4));
		}
		g2d.setColor(color);
	}

}
