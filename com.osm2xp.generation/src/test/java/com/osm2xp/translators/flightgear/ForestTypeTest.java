package com.osm2xp.translators.flightgear;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import com.osm2xp.core.model.osm.Tag;

public class ForestTypeTest {

	@Test
	public void mapsLeafTypeTags() {
		assertEquals(FlightGearForestMaterials.CONIFER,
				ForestType.fromTags(Collections.singletonList(new Tag("leaf_type", "needleleaved"))).getMaterial());
		assertEquals(FlightGearForestMaterials.BROADLEAF,
				ForestType.fromTags(Collections.singletonList(new Tag("leaf_type", "broadleaved"))).getMaterial());
		assertEquals(FlightGearForestMaterials.MIXED,
				ForestType.fromTags(Collections.singletonList(new Tag("leaf_type", "mixed"))).getMaterial());
	}

	@Test
	public void mapsWoodTags() {
		assertEquals(FlightGearForestMaterials.CONIFER,
				ForestType.fromTags(Collections.singletonList(new Tag("wood", "coniferous"))).getMaterial());
		assertEquals(FlightGearForestMaterials.BROADLEAF,
				ForestType.fromTags(Collections.singletonList(new Tag("wood", "deciduous"))).getMaterial());
	}

	@Test
	public void defaultsToMixed() {
		assertEquals(FlightGearForestMaterials.MIXED,
				ForestType.fromTags(Arrays.asList(new Tag("landuse", "forest"))).getMaterial());
		assertEquals(FlightGearForestMaterials.MIXED, ForestType.fromTags(null).getMaterial());
	}
}
