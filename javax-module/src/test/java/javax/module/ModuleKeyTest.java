package javax.module;

import org.testng.Assert;
import org.testng.annotations.Test;

import javax.module.util.ModuleKey;
import java.text.ParseException;

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

	@Test
	public
	void testToStringKeyCompat()
	{
		assertEquals(m("a").toString(), "a-snapshot");
		assertEquals(m("a", "b").toString(), "a-b");
		assertEquals(m("a", "1").toString(), "a-v1");
		assertEquals(m("a", "b", "c").toString(), "a-b");
		assertEquals(m("a", "1", "c").toString(), "a-v1");
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

	@Test
	public
	void testParsing() throws ParseException
	{
		p("a-v1", "a", 1);
		p("a-1", "a", 1);
		p("alpha-v1234", "alpha", 1234);
		p("alpha-1234", "alpha", 1234);
		p("alpha-rc3", "alpha", "rc3");
		p("alpha-snapshot", "alpha", null);
		p("alpha-beta", "alpha", "beta");
		p("alpha-beta-delta-gamma", "alpha-beta-delta", "gamma");
		p("alpha", "alpha", null);
		p("a", "a", null);

		//Edge case... a 'v' that does not indicate a numeric version number...
		p("uvula-vixen", "uvula", "vixen");

		//These don't conform, and so are less important... just checking the edge cases.
		//Maybe *these* should throw a parse exception?
		p("a-", "a-", null);
		p("-a", "-a", null);

		//Yeah... nothing to go on...
		x("");
		x(null);

		//This one had some trouble in testing...
		p("org.apache.bcel-v6.0", "org.apache.bcel", "6.0");
	}

	private
	void x(String raw)
	{
		try
		{
			ModuleKey m=ModuleKey.parseModuleKey(raw);
			throw new AssertionError("should not be able to parse '"+raw+"', but got: "+m);
		}
		catch (ParseException e)
		{
			//good...
		}
	}

	private
	void p(String raw, String moduleName, Object majorVersion) throws ParseException
	{
		ModuleKey m=ModuleKey.parseModuleKey(raw);

		assertEquals(m.getModuleName(), moduleName);

		if (majorVersion==null)
		{
			assertNull(m.getMajorVersion());
		}
		else
		{
			assertEquals(m.getMajorVersion(), majorVersion.toString());
		}
	}
}
