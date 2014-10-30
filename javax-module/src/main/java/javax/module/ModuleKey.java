package javax.module;

import java.io.Serializable;

/**
 * Created by robert on 10/29/14.
 */
public final
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
		if (moduleName == null) throw new NullPointerException();
		this.moduleName = moduleName.toLowerCase();

		if (majorVersion==null)
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

		if (minorVersion==null)
		{
			this.minorVersion = null;
		}
		else
		{
			this.minorVersion = minorVersion.toLowerCase();
		}
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

}
