package javax.module;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Created by robert on 10/29/14.
 */
@Test
public
class ModuleKeyTest extends Assert
{
	@Test
	public
	void testEntity()
	{
		final String a="a";
		final String b="b";
		final String c="c";

		assertEquals(m(a), m(a));
		assertNotEquals(m(a), m(b));

		assertEquals(m(a, b), m(a, b));
		assertEquals(m(a, b), m(a, b, c));
		assertEquals(m(a, b, c), m(a, b));
		assertNotEquals(m(a, b), m(a, c));
	}

	private
	ModuleKey m(String s)
	{
		return new ModuleKey(s, null, null);
	}

	private
	ModuleKey m(String s, String m)
	{
		return new ModuleKey(s, m, null);
	}

	private
	ModuleKey m(String s, String m, String mn)
	{
		return new ModuleKey(s, m, mn);
	}

}
