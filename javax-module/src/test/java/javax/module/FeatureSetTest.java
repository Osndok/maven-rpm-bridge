package javax.module;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Created by robert on 4/7/15.
 */
@Test
public
class FeatureSetTest extends Assert
{
	@Test
	public
	void testUnaryOperations()
	{
		final
		FeatureSet fs=new FeatureSet();

		assertFalse(fs.containsKey("java"));

		fs.add("java", "7");

		assertTrue(fs.containsKey("java"));
		assertTrue(fs.providesAtLeast("java", "7"));
		assertTrue(fs.providesAtLeast("java", "7.0"));
		assertTrue(fs.providesAtLeast("java", "6.99"));
		assertTrue(fs.providesAtLeast("java", "1"));
		assertTrue(fs.providesAtLeast("java", (Version)null));
		assertTrue(fs.providesAtLeast("java", (String)null));

		assertFalse(fs.providesAtLeast("blather", (Version)null));
		assertFalse(fs.providesAtLeast("blather", "1.2.3"));
		assertFalse(fs.providesAtLeast("java", "100"));
		assertFalse(fs.providesAtLeast("java", "10"));
		assertFalse(fs.providesAtLeast("java", "8"));
		assertFalse(fs.providesAtLeast("java", "7.1"));
		assertFalse(fs.providesAtLeast("java", "7.0.0.0.1"));

		fs.add("java", "8");
		assertTrue(fs.providesAtLeast("java", "8"));
	}

	@Test
	public
	void testToString()
	{
		assertEquals(fs().add("java"     )                    .toString(), "java"              );
		assertEquals(fs().add("java"     ).add("java", "7")   .toString(), "java(7)"           );
		assertEquals(fs().add("java", "7").add("java", "6")   .toString(), "java(7)"           );
		assertEquals(fs().add("java", "6").add("java", "7")   .toString(), "java(7)"           );
		assertEquals(fs().add("java", "7")                    .toString(), "java(7)"           );
		assertEquals(fs().add("java"     ).add("blather"     ).toString(), "java,blather"      );
		assertEquals(fs().add("java", "7").add("blather"     ).toString(), "java(7),blather"   );
		assertEquals(fs().add("java"     ).add("blather", "1").toString(), "java,blather(1)"   );
		assertEquals(fs().add("java", "7").add("blather", "1").toString(), "java(7),blather(1)");
	}

	private
	FeatureSet fs()
	{
		return new FeatureSet();
	}

	@Test
	public
	void testParserAndComparator()
	{
		final
		FeatureSet linux=FeatureSet.fromString("java(7),maven(3.2),rhel,centos(6.5),gcc(4.7),rpmbuild(4.9),capillary-ppw(32.11),linux,unix");

		final
		FeatureSet windows=FeatureSet.fromString("java(8),maven(3),microsoft,c#,visualbasic,capillary-streamer(12.34)");

		assertTrue(linux.containsKey("java"));
		assertTrue(linux.containsKey("maven"));
		assertTrue(linux.containsKey("rhel"));
		assertTrue(linux.containsKey("unix"));
		assertTrue(linux.providesAtLeast("java", "7"));
		assertTrue(linux.providesAtLeast("java", "7.0"));
		assertTrue(linux.providesAtLeast("java", "6"));
		assertTrue(linux.providesAtLeast("java", "6.999"));
		assertTrue(linux.providesAtLeast("rpmbuild", (Version)null));

		assertTrue(windows.containsKey("c#"));

		final
		FeatureSet buildableOnLinux=FeatureSet.fromString("java(6),maven,rhel,rpmbuild(3)");

		assertTrue(linux.satisfies(buildableOnLinux));
		assertTrue(buildableOnLinux.isSatisfiedBy(linux));
		assertFalse(buildableOnLinux.satisfies(linux));
		assertFalse(linux.isSatisfiedBy(buildableOnLinux));
		assertFalse(windows.satisfies(buildableOnLinux));
		assertFalse(buildableOnLinux.isSatisfiedBy(windows));
		assertFalse(buildableOnLinux.satisfies(windows));
		assertFalse(windows.isSatisfiedBy(buildableOnLinux));

		final
		FeatureSet buildableOnWindows=FeatureSet.fromString("java(8.0),microsoft,c#");

		assertTrue(windows.satisfies(buildableOnWindows));
		assertTrue(buildableOnWindows.isSatisfiedBy(windows));
		assertFalse(buildableOnWindows.satisfies(windows));
		assertFalse(windows.isSatisfiedBy(buildableOnWindows));
		assertFalse(linux.satisfies(buildableOnWindows));
		assertFalse(buildableOnWindows.isSatisfiedBy(linux));
		assertFalse(buildableOnWindows.satisfies(linux));
		assertFalse(linux.isSatisfiedBy(buildableOnWindows));
	}
}
