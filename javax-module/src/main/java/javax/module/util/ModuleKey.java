package javax.module.util;

import java.io.Serializable;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * A ModuleKey is a three-tuple that specifies a particular module (1) by name, (2) by major version number,
 * and (3) by an optional minimum minor version number.
 *
 * ???: should we convert this to actually extend Tuple3? It might be a bit less readable :(
 */
public
class ModuleKey implements Serializable
{
	private final
	String moduleName;

	private final
	String majorVersion;

	private final
	String minorVersion;

	public
	ModuleKey(String moduleName, String majorVersion, String minorVersion)
	{
		//NB: if (moduleName == null) throw new NullPointerException();
		this.moduleName = moduleName.toLowerCase();

		if (majorVersion==null || majorVersion.length()==0)
		{
			this.majorVersion = null;
		}
		else
		{
			final
			String lower = majorVersion.toLowerCase();

			if (lower.contains("snapshot"))
			{
				this.majorVersion = null;
			}
			else
			{
				this.majorVersion = lower;
			}
		}

		if (minorVersion==null || minorVersion.length()==0)
		{
			this.minorVersion = null;
		}
		else
		{
			this.minorVersion = minorVersion.toLowerCase();
		}
	}

	public
	Dependency asDependencyOf(ModuleKey otherModule)
	{
		return new Dependency(moduleName, majorVersion, minorVersion, otherModule);
	}

	public
	String getModuleName()
	{
		return moduleName;
	}

	public
	String getMajorVersion()
	{
		return majorVersion;
	}

	public
	boolean isSnapshot()
	{
		return majorVersion==null;
	}

	/**
	 * Not strictly part of the key value, but helps ensure that everything is at parity.
	 * When coming from an available module, this indicates the minimum available compatibility,
	 * which is (almost without exception) the *actual* minor version.
	 * @return
	 */
	public
	String getMinorVersion()
	{
		return minorVersion;
	}

	private transient
	String stringValue;

	public
	String toString()
	{
		String stringValue=this.stringValue;

		if (stringValue==null)
		{
			this.stringValue=stringValue=moduleName+"-"+vMajor();
		}

		return stringValue;
	}

	private static
	boolean isDigit(char a)
	{
		return (a>='0' && a<='9');
	}

	private transient
	String vMajor;

	/**
	 * @return the major version number (possibly prefixed with a v) or 'snapshot', as appropriate.
	 */
	public
	String vMajor()
	{
		String vMajor=this.vMajor;

		if (vMajor==null)
		{
			final
			String majorVersion=this.majorVersion;

			if (majorVersion==null)
			{
				this.vMajor=vMajor="snapshot";
			}
			else
			{
				final
				char c=majorVersion.charAt(0);

				if (isDigit(c))
				{
					this.vMajor = vMajor = "v" + majorVersion;
				}
				else
				{
					this.vMajor = vMajor = majorVersion;
				}
			}
		}

		return vMajor;
	}

	@Override
	public
	boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}

		/*
		if (o == null || getClass() != o.getClass())
		{
			return false;
		}
		*/

		ModuleKey moduleKey = (ModuleKey) o;

		if (majorVersion != null ? !majorVersion.equals(moduleKey.majorVersion) : moduleKey.majorVersion != null)
		{
			return false;
		}

		if (!moduleName.equals(moduleKey.moduleName))
		{
			return false;
		}

		return true;
	}

	private transient
	Integer hashCode;

	@Override
	public
	int hashCode()
	{
		Integer retval=this.hashCode;

		if (retval==null)
		{
			int result = moduleName.hashCode();
			result = 31 * result + (majorVersion != null ? majorVersion.hashCode() : 0);

			this.hashCode=retval=result;
		}

		return retval;
	}

	private transient
	ModuleKey[] dependencyChain;

	public
	ModuleKey[] getDependencyChain()
	{
		ModuleKey[] dependencyChain = this.dependencyChain;

		if (dependencyChain==null)
		{
			dependencyChain=createDependencyChain();
		}

		return dependencyChain;
	}

	private
	ModuleKey[] createDependencyChain()
	{
		final
		List<ModuleKey> retval=new ArrayList<ModuleKey>();

		ModuleKey moduleKey=this;

		do
		{
			retval.add(moduleKey);

			if (moduleKey instanceof Dependency)
			{
				moduleKey = ((Dependency)moduleKey).getRequestingModuleKey();
			}
			else
			{
				moduleKey = null;
			}
		}
		while (moduleKey!=null);

		return retval.toArray(new ModuleKey[retval.size()]);
	}

	/**
	 * Used when specifying a class by name.
	 * @param moduleReference - roughly the output of this class's toString() method, e.g. "log4j-v1"
	 * @return a ModuleKey with the embedded module-name and version
	 */
	public static
	ModuleKey parseModuleKey(String moduleReference) throws ParseException
	{
		if (moduleReference==null || moduleReference.length()==0) throw new ParseException(moduleReference,0);

		int lastHyphen=moduleReference.lastIndexOf('-');

		if (lastHyphen>0 && lastHyphen<=moduleReference.length()-2)
		{
			final
			String moduleName=moduleReference.substring(0, lastHyphen);

			if (moduleReference.charAt(lastHyphen+1)=='v')
			{
				final
				String majorVersion=moduleReference.substring(lastHyphen+2);

				if (entirelyNumbersAndPeriods(majorVersion))
				{
					return new ModuleKey(moduleName, majorVersion, null);
				}
			}

			return new ModuleKey(moduleName, moduleReference.substring(lastHyphen+1), null);
		}
		else
		{
			return new ModuleKey(moduleReference, null, null);
		}
	}

	private static
	boolean entirelyNumbersAndPeriods(String s)
	{
		final
		int l=s.length();

		for (int i=0; i<l; i++)
		{
			final
			char c=s.charAt(i);

			if (!Character.isDigit(c) && c!='.')
			{
				return false;
			}
		}

		return (l>0);
	}
}
