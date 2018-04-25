package com.osm2xp.translators.xplane;

import com.osm2xp.model.facades.BarrierType;
import com.osm2xp.model.osm.OsmPolygon;
import com.osm2xp.utils.DsfObjectsProvider;
import com.osm2xp.utils.GeomUtils;
import com.osm2xp.utils.helpers.XplaneOptionsHelper;
import com.osm2xp.writers.IWriter;

import math.geom2d.Point2D;

import static com.osm2xp.translators.impl.XPlaneTranslatorImpl.LINE_SEP;

public class XPBarrierTranslator extends XPWritingTranslator {

	private static final Double MIN_BARRIER_PERIMETER = 200.0; //TODO make configurable from UI
	private DsfObjectsProvider dsfObjectsProvider;

	public XPBarrierTranslator(DsfObjectsProvider dsfObjectsProvider, IWriter writer) {
		super(writer);
		this.dsfObjectsProvider = dsfObjectsProvider;
	}

	@Override
	public boolean handlePoly(OsmPolygon osmPolygon) {
		if (!XplaneOptionsHelper.getOptions().isGenerateFence() || osmPolygon.isPartial()) {
			return false;
		}
		String barrierType = osmPolygon.getTagValue("barrier");
		if (barrierType != null && GeomUtils.computePerimeter(osmPolygon.getPolygon()) > MIN_BARRIER_PERIMETER) {
			Integer facade = dsfObjectsProvider.getRandomBarrierFacade(getBarrierType(barrierType),osmPolygon);
			if (facade != null && facade >= 0) {
				StringBuffer sb = new StringBuffer();
				sb.append("#Barrier " + barrierType + " facade " + facade);
				sb.append(LINE_SEP);
				osmPolygon.setPolygon(GeomUtils.setClockwise(osmPolygon
						.getPolygon()));
				
				sb.append("BEGIN_POLYGON " + facade + " 2 2"); //TODO need actual wall height here, using "2" for now
				sb.append(LINE_SEP);
				sb.append("BEGIN_WINDING");
				sb.append(LINE_SEP);
	
//				osmPolygon.getPolygon().removePoint(
//						osmPolygon.getPolygon().getLastPoint());
				for (Point2D loc : osmPolygon.getPolygon().getVertices()) {
					sb.append("POLYGON_POINT " + loc.x + " " + loc.y);
					sb.append(LINE_SEP);
				}
				sb.append("END_WINDING");
				sb.append(LINE_SEP);
				sb.append("END_POLYGON");
				sb.append(LINE_SEP);
				writer.write(sb.toString(), GeomUtils
						.cleanCoordinatePoint(osmPolygon.getPolygon()
								.getFirstPoint()));
			}
			return true;
		}
		return false;
	}

	private BarrierType getBarrierType(String barrierTypeStr) {
		if ("wall".equalsIgnoreCase(barrierTypeStr)) {
			return BarrierType.WALL;
		}
		return BarrierType.FENCE;
	}

	@Override
	public void translationComplete() {
		// Do nothing
	}

}
