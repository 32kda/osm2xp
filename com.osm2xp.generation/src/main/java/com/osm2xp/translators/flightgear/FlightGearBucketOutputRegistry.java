package com.osm2xp.translators.flightgear;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Run-scoped registry of {@link FlightGearBucketOutput} instances.
 * <p>
 * Both the per-tile {@code FlightGearTranslatorImpl} and the airfield BTG
 * writer need to write into the same set of {@code Objects/<bucket>/<index>.stg}
 * files. Sharing this registry guarantees a single buffered writer per STG file
 * (avoiding the truncation that happens when two components open the same file
 * independently).
 */
public class FlightGearBucketOutputRegistry {

	private final File sceneryRoot;
	private final boolean generateBuildings;
	private final Map<Long, FlightGearBucketOutput> outputs = new HashMap<>();
	private boolean closed;

	public FlightGearBucketOutputRegistry(File sceneryRoot, boolean generateBuildings) {
		this.sceneryRoot = sceneryRoot;
		this.generateBuildings = generateBuildings;
	}

	public FlightGearBucketOutput getBucketOutput(double lon, double lat) {
		return getBucketOutput(FlightGearBucket.bucketFor(lon, lat));
	}

	public FlightGearBucketOutput getBucketOutput(FlightGearBucket bucket) {
		return outputs.computeIfAbsent(bucket.getIndex(),
				index -> new FlightGearBucketOutput(sceneryRoot, bucket, generateBuildings));
	}

	public Collection<FlightGearBucketOutput> getOutputs() {
		return outputs.values();
	}

	public Map<Long, FlightGearBucketOutput> getBucketOutputs() {
		return outputs;
	}

	/** Closes all outputs. Safe to call more than once. */
	public void closeAll() {
		if (closed) {
			return;
		}
		closed = true;
		for (FlightGearBucketOutput output : outputs.values()) {
			output.close();
		}
		outputs.clear();
	}
}
