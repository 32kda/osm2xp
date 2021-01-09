package com.osm2xp.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NameUtilsTest {

	@Test
	void test00() {
		assertEquals("a_b", NameUtils.toIdentifier("a:b"));
	}
	@Test
	void test01() {
		assertEquals("a", NameUtils.toIdentifier("a"));
	}
	@Test
	void test02() {
		assertEquals("_wd_only", NameUtils.toIdentifier("4wd-only"));
	}
	@Test
	void test03() {
		assertEquals("a17", NameUtils.toIdentifier("a17"));
	}

}
