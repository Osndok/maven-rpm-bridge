package com.github.osndok.mrb.runner;

import javax.module.tools.Tuple3;

/**
 * Created by robert on 4/22/15.
 */
public
class JavaReference extends Tuple3<JavaReferenceType, String, String> implements Comparable<JavaReference>
{
	public
	JavaReference(JavaReferenceType type, String packageName, String className)
	{
		super(type, packageName, className);

		if (type==null)
		{
			throw new NullPointerException("JavaReference type cannot be null");
		}

		if (packageName==null)
		{
			throw new NullPointerException("JavaReference packageName cannot be null");
		}

		if (className==null)
		{
			throw new NullPointerException("JavaReference className cannot be null");
		}
	}

	public
	JavaReference(JavaReferenceType aStatic, String packageAndClassName)
	{
		super(aStatic, getPackageName(packageAndClassName), getClassName(packageAndClassName));
	}

	public static
	String getPackageName(String packageAndClassName)
	{
		final
		int lastPeriod=packageAndClassName.lastIndexOf('.');

		if (lastPeriod>0)
		{
			return packageAndClassName.substring(0, lastPeriod);
		}
		else
		{
			return null;
		}
	}

	public static
	String getClassName(String packageAndClassName)
	{
		final
		int lastPeriod=packageAndClassName.lastIndexOf('.');

		if (lastPeriod>0)
		{
			return packageAndClassName.substring(lastPeriod + 1);
		}
		else
		{
			return packageAndClassName;
		}
	}

	public
	JavaReferenceType getReferenceType()
	{
		return first;
	}

	public
	String getPackageName()
	{
		return second;
	}

	public
	String getClassName()
	{
		return third;
	}

	@Override
	public
	String toString()
	{
		return first.toString()+':'+second+':'+third;
	}

	public static
	JavaReference fromString(String s)
	{
		final
		String[] bits=s.split(":");

		if (bits.length!=3)
		{
			throw new IllegalArgumentException("not a java reference: '"+s+"'");
		}

		return new JavaReference(JavaReferenceType.valueOf(bits[0]), bits[1], bits[2]);
	}

	@Override
	public
	int compareTo(JavaReference other)
	{
		int i=this.first.compareTo(other.first);

		if (i==0)
		{
			i=this.second.compareTo(other.second);

			if (i==0)
			{
				return this.third.compareTo(other.third);
			}
			else
			{
				return i;
			}
		}
		else
		{
			return i;
		}
	}
}
