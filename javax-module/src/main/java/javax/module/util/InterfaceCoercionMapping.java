package javax.module.util;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The whole theory and construction of this class is based on the notion that we *MIGHT* be able to shave
 * a bit of time off the routine use of interface methods by caching them (thus avoiding the reflection
 * lookup time per-object-cast by front-loading *all* the possible lookup overheads into the first
 * object cast [OUCH!]).
 *
 * Created by robert on 2015-10-06 14:24.
 */
class InterfaceCoercionMapping
{
	private static final
	Map<Class, Map<Class, Retval>> cache = new WeakHashMap<Class, Map<Class, Retval>>();

	public static
	Map<Method, Method> given(Class iface, Class targetClass)
	{
		//NB: we cannot really use Tuple2 as a key with WeakHashMap... :(
		final
		Map<Class, Retval> retvalByTargetClass;
		{
			synchronized (cache)
			{
				final
				Map<Class, Retval> existing = cache.get(iface);

				if (existing == null)
				{
					retvalByTargetClass = new WeakHashMap<Class, Retval>();
					cache.put(iface, retvalByTargetClass);
				}
				else
				{
					//System.err.println("cache hit 1");
					retvalByTargetClass = existing;
				}
			}
		}

		final
		Retval retval;
		{
			synchronized (retvalByTargetClass)
			{
				final
				Retval existing = retvalByTargetClass.get(targetClass);

				if (existing==null)
				{
					retval=new Retval();
					//NB: In-lock! but... it's the much-smaller lock... and neccesary, I think.
					expensive_PopulateMapping(iface, targetClass, retval);
					retvalByTargetClass.put(targetClass, retval);
				}
				else
				{
					//System.err.println("cache hit 2");
					retval=existing;
				}
			}
		}

		return retval;
	}

	private static
	void expensive_PopulateMapping(Class iface, Class targetClass, Retval retval)
	{
		for (Method incomingMethod : iface.getMethods())
		{
			final
			Method targetMethod=locateMatchingMethod(targetClass, incomingMethod);

			retval.put(incomingMethod, targetMethod);
		}
	}

	private static
	Method locateMatchingMethod(Class aClass, Method likeThis)
	{
		try
		{
			//System.err.println("building: "+aClass+" "+likeThis);
			return aClass.getMethod(likeThis.getName(), likeThis.getParameterTypes());
		}
		catch (NoSuchMethodException e)
		{
			System.err.println("WARNING: Unable to find interface method coercion for "+likeThis+" -> "+aClass);
			e.printStackTrace();
		}

		return null;
	}

	/**
	 * This class/interface exists only to reduce the complexity of the generics, and increase
	 * the readability of the above code.
	 */
	static
	class Retval extends HashMap<Method, Method>
	{

	}
}
