package javax.module.tools;

import javax.module.CommandLineOption;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * Used when a java class is being called into from the command line, but it does not provide a
 * conventional and explicit static main() method. Instead, our task is to use the class definition
 * along with the provided string arguments to:
 *
 * (1) differentiate between arguments and command line options,
 * (2) invoke static methods that resemble provided command line flags (possibly with arguments),
 * (3) construct an instance of the provided class (possibly with the remaining arguments),
 * (4) invoke instance methods (usually setters) for non-static command line flags,
 * (5) call the run() or call() method, as appropriate, and finally
 * (6) convert the result to something that can be used from the command line (if from a call() method).
 *
 * Created by robert on 4/2/15.
 */
public
class FuzzyEntryPoint
{
	public static
	boolean supports(Class aClass)
	{
		return Runnable.class.isAssignableFrom(aClass)
				   || Callable.class.isAssignableFrom(aClass)
			;
	}

	public static
	boolean supportedInterfaceName(String interfaceName)
	{
		return interfaceName.equals(Runnable.class.getName())
				   || interfaceName.equals(Callable.class.getName())
			;
	}

	final
	Class aClass;

	public
	FuzzyEntryPoint(Class aClass)
	{
		this.aClass = aClass;
	}

	private
	RuntimeException _kludge_incompatibleType;

	public
	void execute(String[] argumentsAndCommandLineOptions)
	{
		final
		int argc = argumentsAndCommandLineOptions.length;

		Object instance = null;

		if (argc > 0)
		{
			List<Invocation> staticOptions = new ArrayList<>(argc);
			List<Invocation> instanceOptions = new ArrayList<>(argc);
			List<String> constructorArguments = new ArrayList<>(argc);

			boolean noMoreOptions = false;

			for (int i = 0; i < argc; i++)
			{
				String argOrOption = argumentsAndCommandLineOptions[i];

				if (noMoreOptions || argOrOption.isEmpty())
				{
					constructorArguments.add(argOrOption);
				}
				else if (argOrOption.charAt(0) == '-')
				{
					if (argOrOption.length() == 1)
					{
						//A single hypen usually refers to stdin or stdout; will catch in Convert
						constructorArguments.add(argOrOption);
						continue;
					}
					else
					if (argOrOption.length()==2 && argOrOption.charAt(1)=='-')
					{
						noMoreOptions=true;
						continue;
					}
					else
					if (argOrOption.length()>2 && argOrOption.charAt(1)=='-')
					{
						//Long option processing...
						final
						Method method;

						final
						List<String> methodArguments;

						final
						int equals=argOrOption.indexOf('=');

						if (equals>0)
						{
							//Often, one might expect to supply the argument to a long option like so:
							//> mytool --option=argument
							String methodName=argOrOption.substring(2, equals);
							String methodArg=argOrOption.substring(equals+1);

							method=onlyMatchingOneArgumentMethod(methodName);
							methodArguments=Collections.singletonList(methodArg);
						}
						else
						{
							String methodName=argOrOption.substring(2);
							method=onlyMatchingMethod(methodName);

							//Maybe consume some extra arguments...
							//TODO: support varargs in command line options
							int numMethodArguments=method.getParameterTypes().length;

							methodArguments=new ArrayList<>(numMethodArguments);

							for (int k=0; k<numMethodArguments; k++)
							{
								methodArguments.add(argumentsAndCommandLineOptions[++i]);
							}
						}

						if (Modifier.isStatic(method.getModifiers()))
						{
							staticOptions.add(new Invocation(method, methodArguments));
						}
						else
						{
							instanceOptions.add(new Invocation(method, methodArguments));
						}
					}
					else
					{
						//Short option processing...
						final
						int shortsPlusOne=argOrOption.length();

						for (int j=0; j<shortsPlusOne; j++)
						{
							final
							char c=argOrOption.charAt(j);

							final
							Method method=onlyMatchingOrZeroArgumentMethod(c, i, argOrOption);

							//We will consume ahead as many arguments as needed for this function
							int numMethodArguments=method.getParameterTypes().length;

							final
							List<String> methodArguments=new ArrayList<>(numMethodArguments);

							for (int k=0; k<numMethodArguments; k++)
							{
								methodArguments.add(argumentsAndCommandLineOptions[++i]);
							}

							if (Modifier.isStatic(method.getModifiers()))
							{
								staticOptions.add(new Invocation(method, methodArguments));
							}
							else
							{
								instanceOptions.add(new Invocation(method, methodArguments));
							}
						}
					}
				}
				else
				{
					constructorArguments.add(argOrOption);
				}
			}

			//TODO: support varargs in constructors
			//Locate first constructor matching number (and type?) of *convertable* arguments.
			Constructor constructor=null;
			{
				int numConstructorArgs = constructorArguments.size();

				RuntimeException mostPlausibleError=null;

				for (Constructor possibleMatch : aClass.getConstructors())
				{
					final
					Class[] parameterTypes = possibleMatch.getParameterTypes();

					if (parameterTypes.length==numConstructorArgs)
					{
						_kludge_incompatibleType=null;

						if (containsOnlyPrimitiveAndConvertableTypes(parameterTypes))
						{
							constructor=possibleMatch;
							break;
						}
						else
						if (mostPlausibleError==null)
						{
							mostPlausibleError=_kludge_incompatibleType;
						}
					}
				}

				if (constructor==null)
				{
					if (mostPlausibleError==null)
					{
						System.err.println(String.format("ERROR: Unable to find a constructor that accepts %d parameters (of the given type)",
															numConstructorArgs));
					}
					else
					{
						mostPlausibleError.printStackTrace();
					}

					doUsageAndExit();
					return;
				}
			}

			//Get the constructor parameters ready (before any methods are called)
			final
			Object[] constructorParameters;
			{
				final
				Class[] parameterTypes = constructor.getParameterTypes();

				final
				int l=parameterTypes.length;

				constructorParameters=new Object[l];

				for (int i=0; i<l; i++)
				{
					//TODO: it would be nice if we did not have to construct strings that were never used...
					final
					String context=aClass.getSimpleName()+" constructor argument #"+i+": ";

					constructorParameters[i] = Convert.stringToBasicObject(constructorArguments.get(i), parameterTypes[i], context);
				}
			}

			//Run the invokable static methods
			for (Invocation staticOption : staticOptions)
			{
				try
				{
					staticOption.invoke(null);
				}
				catch (InvocationTargetException e)
				{
					e.getCause().printStackTrace();
					System.exit(1);
				}
				catch (IllegalAccessException e)
				{
					e.printStackTrace();
					System.exit(1);
				}
			}

			//Run the constructor, to get an instance...
			try
			{
				instance=constructor.newInstance(constructorParameters);
			}
			catch (InvocationTargetException e)
			{
				e.getCause().printStackTrace();
				System.exit(1);
			}
			catch (Exception e)
			{
				e.printStackTrace();
				System.exit(1);
			}

			//Run the invokable instance methods (from the option flags acquired long ago)...
			for (Invocation instanceOption : instanceOptions)
			{
				try
				{
					instanceOption.invoke(instance);
				}
				catch (InvocationTargetException e)
				{
					e.getCause().printStackTrace();
					System.exit(1);
				}
				catch (IllegalAccessException e)
				{
					e.printStackTrace();
					System.exit(1);
				}
			}

			//We should now have a Runnable or Callable instance configured how the operator desires it.
			dumpMethodMappings();
		}
		else
		{
			//locate a no-args constructor, or print usage information.
			for (Constructor constructor : aClass.getConstructors())
			{
				if (constructor.getParameterTypes().length==0)
				{
					try
					{
						instance=constructor.newInstance();
					}
					catch (InvocationTargetException e)
					{
						Throwable t=e.getCause();
						t.printStackTrace();
						System.exit(1);
					}
					catch (RuntimeException e)
					{
						throw e;
					}
					catch (Exception e)
					{
						throw new RuntimeException(e);
					}
				}
			}

			if (instance==null)
			{
				doUsageAndExit();
			}
		}

		final
		long preRunTime=System.currentTimeMillis();

		if (instance instanceof Callable)
		{
			try
			{
				Object o=((Callable) instance).call();

				if (o!=null)
				{
					//TODO: we can probably do a better output job if the return type is a byte array, or whatnot.
					System.out.println(o.toString());
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
				System.exit(1);
			}
		}
		else
		if (instance instanceof Runnable)
		{
			((Runnable) instance).run();
		}
		else
		{
			throw new UnsupportedOperationException("unsupported executable interface: "+instance);
		}

		final
		long postConstructionRuntime=System.currentTimeMillis()-preRunTime;

		System.err.println(String.format("internal execution time: %dms (not including initialization overhead)", postConstructionRuntime));

		//NB: we don't System.exit(0), as some applications might have background threads, etc.
		//It is best to mirror the JVM's exit policy in these non-error cases.
	}

	private
	boolean containsOnlyPrimitiveAndConvertableTypes(Class[] parameterTypes)
	{
		for (Class parameterType : parameterTypes)
		{
			if (!parameterType.isPrimitive() && !Convert.isSupportedType(parameterType))
			{
				_kludge_incompatibleType=new IllegalArgumentException(parameterType+" is not a supported parameter type");
				return false;
			}
		}

		return true;
	}

	private
	Method onlyMatchingOneArgumentMethod(String methodName)
	{
		final
		Method method=onlyMatchingMethod(methodName);

		final
		Class[] parameterTypes = method.getParameterTypes();

		if (parameterTypes.length==0)
		{
			System.err.println("ERROR: the '"+methodName+"' option does not accept any parameters.\n");

			System.err.println("\nFor additional help, try running the tool again with only the '--help' option.");
			System.exit(1);
		}
		else
		if (parameterTypes.length > 1)
		{
			System.err.println("ERROR: the '" + methodName + "' option requires " + parameterTypes.length + " parameters, and cannot use the '--a=b' format.\n\nParameter types:\n");

			for (Class aClass : parameterTypes)
			{
				System.err.print('\t');
				System.err.println(maybeRemoveCommonPackageName(aClass));
			}

			System.err.println("\nFor additional help, try running the tool again with only the '--help' option.");
			System.exit(1);
		}

		return null;
	}

	private
	String maybeRemoveCommonPackageName(Class aClass)
	{
		final
		String name=aClass.getName();

		if (name.startsWith("java.lang") || name.startsWith("java.util") || name.startsWith("java.io"))
		{
			return aClass.getSimpleName();
		}
		else
		{
			return name;
		}
	}

	private
	Method onlyMatchingMethod(String methodName)
	{
		if (explicitShortOptionsByCode ==null) doLazyMethodReflectionAnalysis();

		final
		String matchable=methodName.toLowerCase();

		Method method=primaryOptions.get(matchable);

		if (method==null)
		{
			method=backupOptions.get(matchable);

			if (method==null)
			{
				if (!matchable.equals("help"))
				{
					System.err.println("ERROR: unrecognized long option: '" + methodName + "'");
				}
				doUsageAndExit();
			}
		}

		return method;
	}

	private
	void doUsageAndExit()
	{
		xxx();
	}

	//TODO: we *could* expand this logic to deeply inspect argument types, and thus pick out which method to use.
	private
	Method onlyMatchingOrZeroArgumentMethod(char shortOption, int argNumber, String argValue)
	{
		if (explicitShortOptionsByCode ==null) doLazyMethodReflectionAnalysis();

		final
		Method retval= explicitShortOptionsByCode.get(shortOption);

		if (retval==null)
		{
			//NB: it would be acceptable (and maybe even expected) to just print the usage information and exit.
			final
			PrintStream e = System.err;

			e.println("ERROR: unrecognized short command line option ('"+shortOption+"') in argument "+argNumber+" ('"+argValue+"')\n");

			if (explicitShortOptionsByCode.isEmpty())
			{
				e.println("\nThis class has no available short options; you may be missing a preceding hypen.");
			}
			else
			{
				e.println("\nAvailable short options:\n");

				for (Map.Entry<Character, Method> me : explicitShortOptionsByCode.entrySet())
				{
					e.println(String.format("\t'%c'\t%s", me.getKey(), getDescription(me.getValue())));
				}
			}

			e.println("\nFor additional usage information, try running the tool again with only the '--help' flag");

			System.exit(1);
			return null;
		}
		else
		{
			return retval;
		}
	}

	private
	String getDescription(Method method)
	{
		CommandLineOption annotation = method.getAnnotation(CommandLineOption.class);

		if (annotation==null || annotation.description().isEmpty())
		{
			return method.getName();
		}
		else
		{
			return annotation.description();
		}
	}

	/**
	 * Unlike long options, short options are very much case sensitive.
	 */
	private
	Map<Character, Method> explicitShortOptionsByCode;

	/**
	 * Unlike long options, short options are very much case sensitive.
	 */
	private
	Map<Character, Method> implicitShortOptionsByCode;

	/**
	 * A list of preferred lowercase user-presentable option names, and the methods they would
	 * invoke. Used both for aligning supplied options with method calls, but also to build the
	 * usage() information. For example, if a method name is 'setSomeTime', this map will include
	 * only a mapping of 'some-time' to that method.
	 */
	private
	Map<String, Method> primaryOptions;

	/**
	 * This is a list of lowercase option names that would be accepted, but not preferred or
	 * user-presentable.
	 *
	 * For example, if a method name is 'setSomeTime', this would include 'sometime' and
	 * 'setsometime'.
	 */
	private
	Map<String, Method> backupOptions;

	private
	void dumpMethodMappings()
	{
		explicitShortOptionsByCode=null;
		implicitShortOptionsByCode=null;
		primaryOptions=null;
		backupOptions=null;
	}

	/**
	 * NB: rather than waiting until someone stumbles upon an ambiguous flag, we will simply check
	 * them all once, and fail hard if any conflict.
	 *
	 * NB: this means that the tool will totally break if there are any ambiguous options.
	 */
	private
	void doLazyMethodReflectionAnalysis()
	{
		explicitShortOptionsByCode = new HashMap<>();
		implicitShortOptionsByCode = new HashMap<>();
		primaryOptions = new HashMap<>();
		backupOptions = new HashMap<>();

		for (Method method : aClass.getMethods())
		{
			final
			CommandLineOption spec=method.getAnnotation(CommandLineOption.class);

			final
			String specLong;
			{
				if ( spec == null || spec._long().isEmpty() )
				{
					specLong=null;
				}
				else
				{
					specLong=spec._long();
				}
			}

			final
			Character specShort;
			{
				if ( spec == null || spec._short()=='\0')
				{
					specShort = null;
				}
				else
				{
					specShort = spec._short();

					if (explicitShortOptionsByCode.put(specShort, method)!=null)
					{
						throw new IllegalAccessError("multiple @CommandLineOptions for short-option: '"+specShort+"'");
					}
				}
			}

			xxx();

			final
			String methodName = method.getName();

			final
			String methodId = stripSetPrefix(methodName);

			final
			String bits[] = explode(methodId);

			if (bits.length == 1)
			{
				String onlyBit = bits[0];

				if (onlyBit.length() == 1)
				{
					final
					Character onlyCharacter = onlyBit.charAt(0);

					/*
					if (implicitShortOptionsByCode.containsKey(onlyCharacter))
					{
						throw new UnsupportedOperationException("")
					}
					*/

					implicitShortOptionsByCode.put(onlyBit.charAt(0), method);
				}
				else
				{

				}
			}
			else
			{

			}

		}
	}

	private static
	String stripSetPrefix(String methodName)
	{
		if (methodName.startsWith("set"))
		{
			return methodName.substring(3);
		}
		else
		{
			return methodName;
		}
	}

	private
	String[] explode(String methodId)
	{

	}

	/**
	 * Represents an actionable command line option; both the method to be called
	 * and all of it's arguments.
	 */
	private
	class Invocation
	{
		private final
		Method method;

		private final
		Object[] parameters;

		public
		Invocation(Method method, List<String> args)
		{
			if (method == null) throw new NullPointerException("method is null");
			if (args == null) throw new NullPointerException("method arguments list is null");

			this.method = method;

			final
			Class[] parameterTypes = method.getParameterTypes();

			final
			int l = args.size();

			parameters = new Object[l];

			for (int i = 0; i < l; i++)
			{
				//TODO: it would be nice if we did not have to construct strings that were never used...
				final
				String context = method.getName() + " argument #" + i + ": ";

				parameters[i] = Convert.stringToBasicObject(args.get(i), parameterTypes[i], context);
			}
		}

		public
		void invoke(Object target) throws InvocationTargetException, IllegalAccessException
		{
			method.invoke(target, parameters);
		}
	}
}
