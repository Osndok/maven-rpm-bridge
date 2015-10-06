package javax.module;

import org.testng.Assert;
import org.testng.annotations.Test;

import javax.module.util.VersionString;

/**
 * Created by robert on 10/29/14.
 */
@Test
public
class VersionStringTest extends Assert
{
	@Test
	public
	void testVersionSplitting()
	{
		assertEquals(split("1"                   ), new Object[]{1});
		assertEquals(split("1.2"                 ), new Object[]{1, 2 });
		assertEquals(split("1.2.0"               ), new Object[]{1, 2, 0 });
		assertEquals(split("1.0.2"               ), new Object[]{1, 0, 2 });
		assertEquals(split("1.00000.2"           ), new Object[]{1, 0, 2 });
		assertEquals(split("1.0.0.0.0.2"         ), new Object[]{1, 0, 0, 0, 0, 2 });
		assertEquals(split("1.2.3-4-rc5"         ), new Object[]{1, 2, 3, 4, "rc", 5 });
		assertEquals(split("1.2.3-4rc-5"         ), new Object[]{1, 2, 3, 4, "rc", 5 });
		assertEquals(split("1_2_3--4---rc---5"   ), new Object[]{1, 2, 3, 4, "rc", 5 });
		assertEquals(split("01.02:03/04rc005670" ), new Object[]{1, 2, 3, 4, "rc", 5670 });
		assertEquals(split("1.2-SNAPSHOT-rc3"    ), new Object[]{1, 2, "snapshot", "rc", 3});
	}

	private
	Object[] split(String s)
	{
		final
		Object[] retval= VersionString.split(s);

		System.err.print("split('"+s+"'\t) -> {");
		for (int i=0; i<retval.length; i++)
		{
			final
			Object o=retval[i];

			if (o instanceof String)
			{
				System.err.print("'");
				System.err.print(o);
				System.err.print("', ");
			}
			else
			{
				System.err.print(o);
				System.err.print(", ");
				//Convert the Longs to Integers for easy testing/readability...
				retval[i]=((Long)o).intValue();
			}
		}
		System.err.println("}");

		return retval;
	}

	@Test
	public
	void testMatchesIntegerComparison()
	{
		assertTrue(Integer.compare(1,2) < 0);
		assertTrue(Integer.compare(2,1) > 0);
		assertTrue(v("1").compareTo(v("2")) < 0);
		assertTrue(v("2").compareTo(v("1")) > 0);
	}

	@Test
	public
	void testRelativeComparisons()
	{
		VersionString oneDotTwo=new VersionString("1.2");

		assertTrue(oneDotTwo.isNewerThan(v("1.2-SNAPSHOT")));
		assertTrue(oneDotTwo.isNewerThan(v("1.2-rc7")));
		assertTrue(oneDotTwo.isNewerThan(v("1.1.99")));
		assertTrue(oneDotTwo.isNewerThan(v("1.1")));
		assertTrue(oneDotTwo.equals(v("1.2")));
		assertTrue(oneDotTwo.equals(v("1.2.0")));
		assertTrue(oneDotTwo.equals(v("1.2.0.0")));
		assertTrue(oneDotTwo.equals(v("1.2.0.0.0")));
		assertTrue(oneDotTwo.isOlderThan(v("1.2.0.0.0.1")));
		assertTrue(oneDotTwo.isOlderThan(v("1.3")));
		assertTrue(oneDotTwo.isOlderThan(v("2")));
		assertTrue(oneDotTwo.isOlderThan(v("1.2.1")));

		//And the converses...
		assertTrue(v("1.2-SNAPSHOT").isOlderThan(oneDotTwo));
		assertTrue(v("1.2-rc7"     ).isOlderThan(oneDotTwo));
		assertTrue(v("1.1.99").isOlderThan(oneDotTwo));
		assertTrue(v("1.1").isOlderThan(oneDotTwo));
		assertTrue(v("1.2").equals(oneDotTwo));
		assertTrue(v("1.2.0").equals(oneDotTwo));
		assertTrue(v("1.2.0.0").equals(oneDotTwo));
		assertTrue(v("1.2.0.0.0").equals(oneDotTwo));
		assertTrue(v("1.2.0.0.0.1" ).isNewerThan(oneDotTwo));
		assertTrue(v("1.3").isNewerThan(oneDotTwo));
		assertTrue(v("2").isNewerThan(oneDotTwo));
		assertTrue(v("1.2.1").isNewerThan(oneDotTwo));
	}

	//For shorthand & readability
	private
	VersionString v(String s)
	{
		return new VersionString(s);
	}

	/*
	 * Not super-useful, but one could have Version keys (e.g. to a hashmap), and retrieve
	 * using just a string (so long as the string is an exact match). Can't catch the string
	 * side anyway...
	 * /
	@Test
	public
	void testStringEquivalence()
	{
		for (String s : new String[]{"1", "1.0.3", "a", "1-snapshot"})
		{
			assertEquals(v(s), s);
			assertEquals(v(s).hashCode(), s.hashCode());
		}
	}
	*/

}
