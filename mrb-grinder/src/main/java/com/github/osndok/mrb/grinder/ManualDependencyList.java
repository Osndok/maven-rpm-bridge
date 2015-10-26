package com.github.osndok.mrb.grinder;

import javax.module.util.ModuleKey;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Sometimes it's easier to fix broken dependencies simply by adding an exception...
 *
 * Created by robert on 2015-10-26 14:03.
 */
public
class ManualDependencyList
{
	private static final
	Map<ModuleKey, Collection<MavenInfo>> map = new HashMap<ModuleKey, Collection<MavenInfo>>();

	static
	{
		/* turned out to be unprocessed parent-pom dependencies...
		map.put(new ModuleKey("jnaerator", "0", null),
				   Arrays.asList(
									MavenInfo.parse("org.antlr:antlr-runtime:jar:3.5.2"),
									MavenInfo.parse("com.nativelibs4java:ochafik-util:jar:0.12")
				   ));
		*/
	}

	public static
	Collection<MavenInfo> given(ModuleKey moduleKey)
	{
		final
		Collection<MavenInfo> collection =map.get(moduleKey);

		if (collection==null)
		{
			return Collections.emptySet();
		}
		else
		{
			return collection;
		}
	}

	private
	ManualDependencyList()
	{
		//no-op.
	}
}
