package javax.module.meta;

import javax.module.util.ModuleKey;

/**
 * Created by robert on 2015-10-06 13:16.
 */
public
class LoaderModule
{
	//TODO: convert this to be populated by Maven at build time
	//TODO: have this cause the same module to *not* be loaded at runtime (as it's in the ole... 'classpath' mechanism)
	private static final
	ModuleKey JAVAX_MODULE = new ModuleKey("javax-module", "1", null);

	public static
	ModuleKey getModuleKey()
	{
		return JAVAX_MODULE;
	}
}
