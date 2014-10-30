package javax.module;

import java.util.HashMap;

/**
 * Created by robert on 10/30/14.
 * TODO: this does not seem thread safe.
 */
class SubContextTracker
{
	private final
	String name;

	SubContextTracker(String name)
	{
		this.name = name;
	}

	private final HashMap<ModuleContext, Exception> contextMatches = new HashMap();

	void add(ModuleContext c)
	{
		contextMatches.put(c, new Exception(c + " added to " + name));
	}

	void remove(ModuleContext c)
	{
		contextMatches.remove(c);
	}

	private final HashMap<ModuleKey, Exception> moduleKeyMatches = new HashMap();

	void add(ModuleKey moduleKey)
	{
		moduleKeyMatches.put(moduleKey, new Exception(moduleKey + " added to " + name));
	}

	void remove(ModuleKey moduleKey)
	{
		moduleKeyMatches.remove(moduleKey);
	}

	private final HashMap<Combined, Exception> combinedMatches = new HashMap();

	void add(ModuleContext c, ModuleKey spec)
	{
		combinedMatches.put(new Combined(c, spec), new Exception(spec + " added to " + name));
	}

	void remove(ModuleContext c, ModuleKey spec)
	{
		combinedMatches.remove(new Combined(c, spec));
	}

	Exception matches(ModuleContext c, ModuleKey spec)
	{
		Exception retval = contextMatches.get(c);
		if (retval == null)
		{
			retval = moduleKeyMatches.get(c);
		}
		if (retval == null)
		{
			retval = combinedMatches.get(new Combined(c, spec));
		}
		return retval;
	}

	private static
	class Combined
	{
		private final
		ModuleContext   c;

		private final
		ModuleKey k;

		private final
		int hashCode;

		Combined(ModuleContext c, ModuleKey k)
		{
			this.c = c;
			this.k = k;
			this.hashCode = (c.hashCode() * 31) + (k.hashCode());
		}

		public
		int hashCode()
		{
			return hashCode;
		}

		public
		boolean equals(Object o)
		{
			Combined c2 = (Combined) o;
			if (hashCode != c2.hashCode)
			{
				return false;
			}
			return (c.equals(c2.c) && k.equals(c2.k));
		}
	}
}

