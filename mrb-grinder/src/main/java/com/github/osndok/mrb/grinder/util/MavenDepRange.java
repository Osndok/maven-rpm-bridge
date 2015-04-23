package com.github.osndok.mrb.grinder.util;

import org.sonatype.aether.util.version.GenericVersionScheme;
import org.sonatype.aether.version.VersionConstraint;
import org.sonatype.aether.version.VersionRange;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Surprise!!! Maven supports dependency ranges! Did you know that?
 *
 * http://docs.codehaus.org/display/MAVEN/Dependency+Mediation+and+Conflict+Resolution
 */
public
class MavenDepRange
{
	public static
	boolean isLikely(String s)
	{
		return s.indexOf(',')>0;
	}

	public static
	String getAnyValidVersionFromRange(String s) throws Exception
	{
		if (genericVersionScheme==null)
		{
			genericVersionScheme=new GenericVersionScheme();
		}

		VersionConstraint versionConstraint = genericVersionScheme.parseVersionConstraint(s);

		final
		List<VersionRange> ranges=new ArrayList<VersionRange>();
		{
			ranges.addAll(versionConstraint.getRanges());
			Collections.reverse(ranges);
		}

		for (VersionRange range : ranges)
		{
			final
			String rangeString = range.toString();

			if (rangeString.indexOf(']')>0 || rangeString.indexOf('[')>=0)
			{
				return upperOrLowerInclusiveBound(range);
			}
		}

		throw new Exception("version range does not have any inclusive bounds: "+s);
	}

	private static
	String upperOrLowerInclusiveBound(VersionRange versionRange) throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException
	{
		//Thanks for making it easy, folks!!! :-P
		//Seriously... there has got to be a better library for this!

		if (isUpperBoundInclusive==null)
		{
			Class aClass=versionRange.getClass();

			getLowerBound=canAccess(aClass.getMethod("getLowerBound"));
			isLowerBoundInclusive=canAccess(aClass.getMethod("isLowerBoundInclusive"));
			getUpperBound=canAccess(aClass.getMethod("getUpperBound"));
			isUpperBoundInclusive=canAccess(aClass.getMethod("isUpperBoundInclusive"));
		}

		if (bool(isUpperBoundInclusive.invoke(versionRange)))
		{
			return getUpperBound.invoke(versionRange).toString();
		}
		else
		if (bool(isLowerBoundInclusive.invoke(versionRange)))
		{
			return getLowerBound.invoke(versionRange).toString();
		}
		else
		{
			throw new IllegalStateException("expecting upper or lower bound to be inclusive");
		}
	}

	private static
	Method canAccess(Method method)
	{
		method.setAccessible(true);
		return method;
	}

	private static
	boolean bool(Object o)
	{
		return (Boolean)o;
	}

	private static GenericVersionScheme genericVersionScheme;

	private static Method getLowerBound;
	private static Method isLowerBoundInclusive;
	private static Method getUpperBound;
	private static Method isUpperBoundInclusive;

}
