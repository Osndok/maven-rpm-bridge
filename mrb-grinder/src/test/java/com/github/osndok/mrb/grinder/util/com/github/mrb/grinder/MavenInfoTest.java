package com.github.osndok.mrb.grinder.util.com.github.mrb.grinder;

import com.github.osndok.mrb.grinder.MavenInfo;
import junit.framework.Assert;

/**
 * Created by robert on 12/3/14.
 */
public
class MavenInfoTest extends Assert
{
	public
	void testClassifierParsing()
	{
		String example="com.github.jnr:jffi:1.2.7:jar:native";

		MavenInfo parsed = MavenInfo.parse(example);

		assertEquals("com.github.jnr", parsed.getGroupId());
		assertEquals("jffi", parsed.getArtifactId());
		assertEquals("native", parsed.getClassifier());
		assertEquals("1.2.7", parsed.getVersion());

		String backToString=parsed.toString();

		assertEquals(example, backToString);
	}
}
