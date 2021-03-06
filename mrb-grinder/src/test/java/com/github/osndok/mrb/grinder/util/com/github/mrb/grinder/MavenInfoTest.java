package com.github.osndok.mrb.grinder.util.com.github.mrb.grinder;

import com.github.osndok.mrb.grinder.MavenInfo;
import junit.framework.Assert;

/**
 * Created by robert on 12/3/14.
 */
public
class MavenInfoTest extends Assert
{
	/*
	WHERE DID THIS TEST VECTOR COME FROM?
	(probably from dependency:copy plugin's "artifact" parameter)

	It disagrees with maven's docs:
	https://maven.apache.org/pom.html#Maven_Coordinates
	 */
	public
	void dont_testClassifierParsing()
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

	/*
	https://maven.apache.org/pom.html#Maven_Coordinates
	*/
	public
	void testVectorsFromMavenDocs()
	{
		MavenInfo parsed=MavenInfo.parse("groupId:artifactId:version");
		{
			assertEquals("groupId", parsed.getGroupId());
			assertEquals("artifactId", parsed.getArtifactId());
			assertEquals("version", parsed.getVersion());
		}

		//NB: changed 'packaging' to 'jar' to satisfy sanity check
		parsed=MavenInfo.parse("groupId:artifactId:jar:version");
		{
			assertEquals("groupId", parsed.getGroupId());
			assertEquals("artifactId", parsed.getArtifactId());
			assertEquals("version", parsed.getVersion());
			assertEquals("jar", parsed.getPackaging());
		}

		//NB: changed 'packaging' to 'jar' to satisfy sanity check
		parsed=MavenInfo.parse("groupId:artifactId:jar:classifier:version");
		{
			assertEquals("groupId", parsed.getGroupId());
			assertEquals("artifactId", parsed.getArtifactId());
			assertEquals("version", parsed.getVersion());
			assertEquals("jar", parsed.getPackaging());
			assertEquals("classifier", parsed.getClassifier());
		}
	}

	public
	void testVectorsFromMavenOutput()
	{
		MavenInfo parsed=MavenInfo.parse("org.scalatest:scalatest_2.11:jar:sources:2.2.4");
		{
			assertEquals("org.scalatest", parsed.getGroupId());
			assertEquals("scalatest_2.11", parsed.getArtifactId());
			assertEquals("jar", parsed.getPackaging());
			assertEquals("sources", parsed.getClassifier());
			assertEquals("2.2.4", parsed.getVersion());
		}

		assertEquals("org.scalatest:scalatest_2.11:jar:sources:2.2.4", parsed.toString());
	}
}
