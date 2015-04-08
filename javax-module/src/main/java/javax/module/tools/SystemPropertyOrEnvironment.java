package javax.module.tools;

/**
 * Created by robert on 4/8/15.
 */
public
class SystemPropertyOrEnvironment
{
	public static
	String get(String key)
	{
		final
		String prop=System.getProperty(key);

		if (prop==null)
		{
			return System.getenv(key);
		}
		else
		{
			return prop;
		}
	}

	public static
	String get(String key, String _default)
	{
		final
		String prop=System.getProperty(key);

		if (prop==null)
		{
			final
			String env=System.getenv(key);

			if (env==null)
			{
				return _default;
			}
			else
			{
				return env;
			}
		}
		else
		{
			return prop;
		}
	}

	public static
	boolean getBoolean(String key, boolean _default)
	{
		final
		String s=get(key);

		if (s==null)
		{
			return _default;
		}
		else
		{
			try
			{
				return Convert.stringToBooleanPrimitive(s);
			}
			catch (IllegalArgumentException e)
			{
				e.printStackTrace();
				return _default;
			}
		}
	}

}
