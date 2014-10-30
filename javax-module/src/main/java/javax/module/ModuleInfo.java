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
	ModuleKey readLine(InputStream inputStream) throws IOException
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
				return new ModuleKey(sb.toString(), null, null);
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
				return new ModuleKey(moduleName, null, null);
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
				return new ModuleKey(moduleName, sb.toString(), null);
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
				return new ModuleKey(moduleName, majorVersion, null);
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
				return new ModuleKey(moduleName, majorVersion, sb.toString());
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

		return new ModuleKey(moduleName, majorVersion, minorVersion);
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
	ModuleInfo read(InputStream inputStream) throws IOException
	{
		final
		ModuleKey self=readLine(inputStream);

		final
		Set<ModuleKey> deps=new HashSet<ModuleKey>();

		ModuleKey dep=readLine(inputStream);

		while (dep!=null)
		{
			deps.add(dep);
			dep=readLine(inputStream);
		}

		return new ModuleInfo(self, Collections.unmodifiableSet(deps));
	}
}
