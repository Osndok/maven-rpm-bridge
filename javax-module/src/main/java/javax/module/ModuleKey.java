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
	String minimumMinorVersion;

	public
	ModuleKey(String moduleName, String majorVersion, String minimumMinorVersion)
	{
		if (moduleName==null) throw new NullPointerException();
		this.moduleName = moduleName;
		this.majorVersion = majorVersion;
		this.minimumMinorVersion = minimumMinorVersion;
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
	String getMinimumMinorVersion()
	{
		return minimumMinorVersion;
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

	private transient
	String vMajor;

	/**
	 * @return the major version number (prefixed with a v) or 'snapshot', as appropriate.
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
				this.vMajor=vMajor="v"+majorVersion;
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
