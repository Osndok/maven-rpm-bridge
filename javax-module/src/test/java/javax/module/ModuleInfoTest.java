package javax.module;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by robert on 10/29/14.
 */
@Test
public
class ModuleInfoTest extends Assert
{
	@Test
	public
	void testModuleInfoLoading() throws IOException
	{
		final
		InputStream in=getClass().getClassLoader().getResourceAsStream("module.info");

		assertNotNull(in);

		final
		ModuleInfo mi=ModuleInfo.read(in);

		ModuleKey m=mi.getModuleKey();

		assertEquals(m.getModuleName(), "self");
		majorMinor(m);

		for (ModuleKey moduleKey : mi.getDependencies())
		{
			System.err.println("dep: "+moduleKey);

			String s=moduleKey.getModuleName();
			int i=Integer.parseInt(s);

			switch (i)
			{
				case 1:
				case 2:
				case 3:
					majorMinor(moduleKey);
					break;

				case 4:
				case 5:
					snapshot(moduleKey);
					break;

				case 6:
				case 7:
					major(moduleKey);
					break;

				case 8:
				case 9:
					majorMinor(moduleKey);
					break;

				default:
					throw new AssertionError(i);
			}
		}

		assertEquals(mi.getDependencies().size(), 9);
	}

	private
	void snapshot(ModuleKey m)
	{
		assertNull(m.getMajorVersion());
		assertNull(m.getMinorVersion());
	}

	private
	void major(ModuleKey m)
	{
		assertEquals(m.getMajorVersion(), "majorversion");
		assertNull(m.getMinorVersion());
	}

	private
	void majorMinor(ModuleKey m)
	{
		assertEquals(m.getMajorVersion(), "majorversion");
		assertEquals(m.getMinorVersion(), "minorversion");
	}
}
