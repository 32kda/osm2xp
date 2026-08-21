package com.osm2xp.translators.airfield;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.commons.lang.StringUtils;
import org.json.simple.JSONObject;

import com.osm2xp.generation.options.FlightGearOptions;
import com.osm2xp.generation.options.FlightGearOptionsProvider;
import com.osm2xp.translators.flightgear.FlightGearElevProber;

import math.geom2d.Point2D;

public class ElevationProvidingService extends GeoMetaProvidingService<Double> {
	
	private static final int MAX_QUERIED_POINTS = 10;

	private static final String ELEVATIONS_ARR_PROP = "elevations";

	private static final String ELEVATION_PROP = "elevation";

	private static ElevationProvidingService instance;
	
	private List<Point2D> toGet = new ArrayList<Point2D>();

	private FlightGearElevProber prober;
	
	public static synchronized ElevationProvidingService getInstance() {
		if (instance == null) {
			instance = new ElevationProvidingService();
		}
		return instance;
	}
		
	public Double getElevation(Point2D point, boolean queryIfAbsent) {
		Double elevation = getLocalElevation(point);
		if (elevation != null) {
			return elevation;
		}
		return getMeta(point, queryIfAbsent);
	}

	private Double getLocalElevation(Point2D point) {
		if (prober == null) {
			prober = createProber();
		}
		if (prober == null || prober.isDisabled()) {
			return null;
		}
		double x = Math.floor(point.x() * 1000000) / 1000000.0;
		double y = Math.floor(point.y() * 1000000) / 1000000.0;
		Point2D roundedPoint = new Point2D(x, y);
		Double cached = metaMap.get(roundedPoint);
		if (cached != null) {
			return cached;
		}
		double elev = prober.probe(point.x(), point.y());
		if (elev == FlightGearElevProber.NO_ELEV) {
			return null;
		}
		metaMap.put(roundedPoint, elev);
		return elev;
	}

	private FlightGearElevProber createProber() {
		FlightGearOptions options = FlightGearOptionsProvider.getOptions();
		String fgelevPath = options.getFgelevPath();
		File fgelevBinary = StringUtils.isNotBlank(fgelevPath) ? new File(fgelevPath) : null;
		return new FlightGearElevProber(fgelevBinary, options.getFlightGearSceneryPath());
	}

	protected GetElevationCallable scheduleGetElevations() {
		GetElevationCallable elevationCallable = new GetElevationCallable(toGet);
		toGet = new ArrayList<>();
		return elevationCallable;
	}
	
	public void finish() {
		if (toGet.size() > 0) {
			submit(scheduleGetElevations());
		}
		super.finish();
	}

	@Override
	protected Double getMetaValue(JSONObject current) {
		return (Double) current.get(ELEVATION_PROP);
	}

	@Override
	protected String getInstancePropName() {
		return ELEVATIONS_ARR_PROP;
	}

	@Override
	protected Callable<?> queryOnline(Point2D roundedPoint) {
		GetElevationCallable job = null;
		if (toGet.size() == MAX_QUERIED_POINTS) {
			job = scheduleGetElevations();
		}
		toGet.add(roundedPoint);
		return job;
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void putMetaValue(JSONObject object, Double value) {
		object.put(ELEVATION_PROP, value);
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void putObtainedToMap(Object result) {
		metaMap.putAll((Map<? extends Point2D, ? extends Double>) result);
	}

}
