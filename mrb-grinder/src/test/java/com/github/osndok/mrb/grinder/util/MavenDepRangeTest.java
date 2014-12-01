package com.github.osndok.mrb.grinder.util;

import junit.framework.Assert;

/**
 * Created by robert on 12/1/14.
 */
//@Test
public
class MavenDepRangeTest extends Assert
{
	public
	void testParsing() throws Exception
	{
		assertTrue(MavenDepRange.isLikely("(1.0,2.0]"));
		assertTrue(!MavenDepRange.isLikely("1.2.3-rc4-snapshot"));

		assertEquals("2.0", MavenDepRange.getAnyValidVersionFromRange("(1.0,2.0]"));
		assertEquals("1.0", MavenDepRange.getAnyValidVersionFromRange("[1.0,2.0)"));
		assertEquals("1.2", MavenDepRange.getAnyValidVersionFromRange("(,1.0],[1.2,)"));
	}
}
