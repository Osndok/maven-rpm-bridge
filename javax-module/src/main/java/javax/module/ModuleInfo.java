package javax.module;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by robert on 10/29/14.
 */
public
class ModuleInfo
{
	private final
	ModuleKey moduleKey;

	private final
	Set<ModuleKey> dependencies;

	private
	ModuleInfo(ModuleKey moduleKey, Set<ModuleKey> dependencies)
	{
		this.moduleKey = moduleKey;
		this.dependencies = dependencies;
	}

	public
	ModuleKey getModuleKey()
	{
		return moduleKey;
	}

	public
	Set<ModuleKey> getDependencies()
	{
		return dependencies;
	}

	static
	ModuleKey readLine(InputStream inputStream, ModuleKey requestor) throws IOException
	{
		int c=inputStream.read();

		while (isHorizontalSpace(c) || isVerticalOrEOF(c))
		{
			if (c<=0)
			{
				return null;
			}

			c=inputStream.read();
		}

		final
		StringBuilder sb=new StringBuilder();

		do
		{
			sb.append((char)c);

			c=inputStream.read();

			if (isVerticalOrEOF(c))
			{
				if (requestor==null)
				{
					return new ModuleKey(sb.toString(), null, null);
				}
				else
				{
					return new Dependency(sb.toString(), null, null, requestor);
				}
			}
		}
		while(!isHorizontalSpace(c));

		final
		String moduleName=sb.toString();

		do
		{
			c=inputStream.read();

			if (isVerticalOrEOF(c))
			{
				if (requestor==null)
				{
					return new ModuleKey(moduleName, null, null);
				}
				else
				{
					return new Dependency(moduleName, null, null, requestor);
				}
			}
		}
		while(isHorizontalSpace(c));

		sb.delete(0, sb.length());

		do
		{
			sb.append((char)c);

			c=inputStream.read();

			if (isVerticalOrEOF(c))
			{
				if (requestor==null)
				{
					return new ModuleKey(moduleName, sb.toString(), null);
				}
				else
				{
					return new Dependency(moduleName, sb.toString(), null, requestor);
				}
			}
		}
		while(!isHorizontalSpace(c));

		final
		String majorVersion=sb.toString();

		do
		{
			c=inputStream.read();

			if (isVerticalOrEOF(c))
			{
				if (requestor==null)
				{
					return new ModuleKey(moduleName, majorVersion, null);
				}
				else
				{
					return new Dependency(moduleName, majorVersion, null, requestor);
				}
			}
		}
		while(isHorizontalSpace(c));

		sb.delete(0, sb.length());

		do
		{
			sb.append((char)c);

			c=inputStream.read();

			if (isVerticalOrEOF(c))
			{
				if (requestor==null)
				{
					return new ModuleKey(moduleName, majorVersion, sb.toString());
				}
				else
				{
					return new Dependency(moduleName, majorVersion, sb.toString(), requestor);
				}
			}
		}
		while(!isHorizontalSpace(c));

		final
		String minorVersion=sb.toString();

		do
		{
			c=inputStream.read();
		}
		while (!isVerticalOrEOF(c));

		if (requestor==null)
		{
			return new ModuleKey(moduleName, majorVersion, minorVersion);
		}
		else
		{
			return new Dependency(moduleName, majorVersion, minorVersion, requestor);
		}
	}

	private static
	boolean isVerticalOrEOF(int c)
	{
		return c=='\n' || c=='\r' || c<=0;
	}

	private static
	boolean isHorizontalSpace(int c)
	{
		return c==' ' || c=='\t';
	}

	static
	ModuleInfo blank(ModuleKey self)
	{
		return new ModuleInfo(self, Collections.<ModuleKey>emptySet());
	}

	static
	ModuleInfo read(InputStream inputStream, ModuleKey originalSelf) throws IOException
	{
		final
		ModuleKey requestor;
		{
			if (originalSelf instanceof Dependency)
			{
				requestor=((Dependency)originalSelf).getRequestingModuleKey();
			}
			else
			{
				requestor=null;
			}
		}

		final
		ModuleKey self=readLine(inputStream, requestor);

		final
		Set<ModuleKey> deps=new HashSet<ModuleKey>();

		ModuleKey dep=readLine(inputStream, self);

		while (dep!=null)
		{
			deps.add(dep);
			dep=readLine(inputStream, self);
		}

		return new ModuleInfo(self, Collections.unmodifiableSet(deps));
	}
}
