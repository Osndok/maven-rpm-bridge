package javax.module;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A FeatureSet is a utility class that conveys a set of string-representable requirements
 * usually for determining processing adequacy. Furthermore, an easily human-readable and
 * machine-parsable string representation is provided so that feature sets can be easily
 * saved & restored.
 *
 * The "arguments" imply backwards compatibility, and the current implementation cannot
 * represent or test "specific" version requirements. Therefore, if you have a requirement
 * for "any java version 7 or above", then use "java(7)", but if you have a requirement
 * that it "must be java 7" (e.g. not 8), then use "java7" (no parenthesis, 7 is in the key).
 *
 * Similarly, if you wish to apply this to a versioning scheme such as symver where the
 * first version block indicates backwards compatibility, then you may have to insert the
 * major version into the key (e.g. "symver4(4.3.2)") to get the desired effect.
 *
 * By convention, only lowercase keys are used in a featureset, as it *is* case sensitive.
 *
 * e.g. "java(7),maven(3.2),centos(6.5),gcc(4.7),rpmbuild(4.9),capillary-ppw(32.11)"
 *
 * TODO: it would be nice to support an exact-match mechanism, such as "java(=7)" not matching java 6 or java 8.
 */
public final
class FeatureSet
{
	private final
	Map<String, Version> versionRequirements = new LinkedHashMap<String, Version>();

	public
	FeatureSet add(String key)
	{
		return add(key, (Version) null);
	}

	public
	FeatureSet add(String key, String version)
	{
		if (version == null)
		{
			return add(key, (Version) null);
		}
		else
		{
			return add(key, new Version(version));
		}
	}

	public
	FeatureSet add(String key, Version version)
	{
		mustNotContainAnyCommasOrParanthesis("key", key);

		if (version != null)
		{
			mustNotContainAnyCommasOrParanthesis("version", version.toString());
		}

		_add(key, version);

		toStringCache = null;
		return this;
	}

	private
	void mustNotContainAnyCommasOrParanthesis(String identifier, String value)
	{
		throwIfFound(',', "commas", identifier, value);
		throwIfFound('(', "parenthesis", identifier, value);
		throwIfFound(')', "parenthesis", identifier, value);
	}

	private
	void _add(String key, Version version)
	{
		Version existing = versionRequirements.get(key);

		if (existing == null)
		{
			//NB: version could be null too
			versionRequirements.put(key, version);
		}
		else if (version != null && existing.isOlderThan(version))
		{
			versionRequirements.put(key, version);
		}
	}

	private
	void throwIfFound(char c, String commas, String identifier, String value)
	{
		final
		int position = value.indexOf(c);

		if (position >= 0)
		{
			throw new IllegalArgumentException(identifier + " may not contain any "+commas+": '"+value+"' @ "+position);
		}
	}

	public
	FeatureSet addAll(FeatureSet other)
	{
		for (Map.Entry<String, Version> me : other.versionRequirements.entrySet())
		{
			String key=me.getKey();
			Version version=me.getValue();
			_add(key, version);
		}

		return this;
	}

	public
	boolean containsKey(String key)
	{
		return versionRequirements.containsKey(key);
	}

	public
	boolean providesAtLeast(String key, String version)
	{
		if (version==null)
		{
			return providesAtLeast(key, (Version)null);
		}
		else
		{
			return providesAtLeast(key, new Version(version));
		}
	}

	public
	boolean providesAtLeast(String key, Version minimum)
	{
		if (minimum==null)
		{
			return versionRequirements.containsKey(key);
		}

		Version provision=versionRequirements.get(key);

		return (provision != null && !provision.isOlderThan(minimum));
	}

	private transient
	String toStringCache;

	@Override
	public
	String toString()
	{
		if (toStringCache==null)
		{
			final
			StringBuilder sb = new StringBuilder();

			for (Map.Entry<String, Version> me : versionRequirements.entrySet())
			{
				String key=me.getKey();
				Version version=me.getValue();

				if (sb.length()!=0)
				{
					sb.append(',');
				}

				sb.append(key);

				if (version!=null)
				{
					sb.append('(');
					sb.append(version);
					sb.append(')');
				}
			}

			toStringCache=sb.toString();
		}

		return toStringCache;
	}

	public static
	FeatureSet fromString(String s)
	{
		final
		FeatureSet retval=new FeatureSet();

		final
		String[] bits=s.split(",");

		for (String bit : bits)
		{
			final
			int leftParanthesis=bit.indexOf('(');

			if  (leftParanthesis==0)
			{
				throw new IllegalArgumentException("not a valid feature set string: '"+bit+"' / '"+s+"'");
			}
			else
			if (leftParanthesis<0)
			{
				retval.add(bit, (Version)null);
			}
			else
			if (bit.charAt(bit.length()-1)==')')
			{
				retval.add(bit.substring(0, leftParanthesis), bit.substring(leftParanthesis+1, bit.length()-1));
			}
			else
			{
				throw new IllegalArgumentException("not a valid feature set string: '"+bit+"' / '"+s+"'");
			}
		}

		return retval;
	}

	/**
	 * @param smaller
	 * @return true if (and only if) this feature set encompasses all the versions of the given smaller set
	 */
	public
	boolean isSuperSetOf(FeatureSet smaller)
	{
		for (Map.Entry<String, Version> me : smaller.versionRequirements.entrySet())
		{
			String key=me.getKey();
			Version version=me.getValue();

			if (version==null)
			{
				if (!this.versionRequirements.containsKey(key))
				{
					return false;
				}
			}
			else
			{
				Version shouldBeNewer=this.versionRequirements.get(key);

				if (shouldBeNewer==null || shouldBeNewer.isOlderThan(version))
				{
					return false;
				}
			}
		}

		return true;
	}

	public
	boolean isSubSetOf(FeatureSet larger)
	{
		for (Map.Entry<String, Version> me : this.versionRequirements.entrySet())
		{
			String key=me.getKey();
			Version version=me.getValue();

			if (version==null)
			{
				if (!larger.versionRequirements.containsKey(key))
				{
					return false;
				}
			}
			else
			{
				Version shouldBeNewer=larger.versionRequirements.get(key);

				if (shouldBeNewer==null || shouldBeNewer.isOlderThan(version))
				{
					return false;
				}
			}
		}

		return true;
	}

	public
	boolean satisfies(FeatureSet smaller)
	{
		return this.isSuperSetOf(smaller);
	}

	public
	boolean isSatisfiedBy(FeatureSet larger)
	{
		return this.isSubSetOf(larger);
	}
}
