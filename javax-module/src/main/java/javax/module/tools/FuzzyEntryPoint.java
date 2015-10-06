package javax.module.tools;

import javax.module.CommandLineOption;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
 * Also known as: "let's shoehorn all the nasty command line parameter and flag handling into one place
 * so we never have to duplicate it in another main() method again."
 *
 * Created by robert on 4/2/15.
 */
public
class FuzzyEntryPoint
{
	/**
	 * Since the return value is so much easier to check (e.g. for shell scripts), this will default
	 * to true unless an environment variable or system property indicates otherwise.
	 */
	private static final boolean INTERPRETIVE_EXIT_STATUS = SystemPropertyOrEnvironment.getBoolean("INTERPRETIVE_EXIT_STATUS",
																														 true);
	private static
	Comparator<? super Method> byMethodName = new Comparator<Method>()
	{
		@Override
		public
		int compare(Method method, Method method2)
		{
			return method.getName().compareTo(method2.getName());
		}
	};

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
			final
			List<Invocation> staticOptions = new ArrayList<Invocation>(argc);

			final
			List<Invocation> instanceOptions = new ArrayList<Invocation>(argc);

			final
			List<String> constructorArguments = new ArrayList<String>(argc);

			boolean noMoreOptions = false;

			for (int i = 0; i < argc; i++)
			{
				String argOrOption = argumentsAndCommandLineOptions[i];

				//System.err.println("argOrOption: "+argOrOption);

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
					else if (argOrOption.length() == 2 && argOrOption.charAt(1) == '-')
					{
						noMoreOptions = true;
						continue;
					}
					else
					if (argOrOption.length()>2 && argOrOption.charAt(1)=='-')
					{
						//Long option processing...
						final
						Method method;

						final
						List<Object> methodArguments;

						final
						int equals=argOrOption.indexOf('=');

						if (equals>0)
						{
							//Often, one might expect to supply the argument to a long option like so:
							//> mytool --option=argument
							String methodName=argOrOption.substring(2, equals);
							String methodArg=argOrOption.substring(equals+1);

							method=onlyMatchingOneArgumentMethod(methodName);
							methodArguments=(List)Collections.singletonList(methodArg);
						}
						else
						{
							final
							String methodName=argOrOption.substring(2);

							method=onlyMatchingMethod(methodName);

							//Maybe consume some extra arguments...

							final
							Class[] parameterTypes = method.getParameterTypes();

							methodArguments=new ArrayList<Object>(parameterTypes.length);

							for (Class parameterType : parameterTypes)
							{
								//NB: CONSUMING additional arguments (e.g. if we wanted to support '=' separation...)
								final
								String arg=argumentsAndCommandLineOptions[++i];

								final
								Object[] context = new Object[]{methodName," argument #",i,": "};

								if (parameterType.isArray())
								{
									methodArguments.add(Convert.stringToArray(arg, parameterType, context));
								}
								else
								{
									methodArguments.add(Convert.stringToBasicObject(arg, parameterType, context));
								}
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

							final
							String methodName=method.getName();

							final
							Class[] parameterTypes = method.getParameterTypes();

							final
							List<Object> methodArguments = new ArrayList<Object>(parameterTypes.length);

							for (Class parameterType : parameterTypes)
							{
								//NB: CONSUMING additional arguments (e.g. if we wanted to support '=' separation...)
								final
								String arg=argumentsAndCommandLineOptions[++i];

								final
								Object[] context = new Object[]{methodName," argument #",i,": "};

								if (parameterType.isArray())
								{
									methodArguments.add(Convert.stringToArray(arg, parameterType, context));
								}
								else
								{
									methodArguments.add(Convert.stringToBasicObject(arg, parameterType, context));
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
					}
				}
				else
				{
					constructorArguments.add(argOrOption);
				}
			}

			boolean hasPublicConstructor=false;

			//Locate first constructor matching number (and type?) of *convertable* arguments.
			Constructor constructor=null;
			{
				int numConstructorArgs = constructorArguments.size();

				RuntimeException mostPlausibleError=null;

				for (Constructor possibleMatch : aClass.getConstructors())
				{
					if (!Modifier.isPublic(possibleMatch.getModifiers()))
					{
						continue;
					}

					hasPublicConstructor=true;

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

				final
				boolean exactConstructorMatch=(constructor!=null);

				if (constructor==null)
				{
					if (!hasPublicConstructor)
					{
						System.err.println(aClass.toString()+" has no public constructors");
						System.exit(1);
					}

					//No luck? Now let's see if there is a varargs constructor that matches.
					for (Constructor possibleMatch : aClass.getConstructors())
					{
						if (!Modifier.isPublic(possibleMatch.getModifiers()))
						{
							continue;
						}

						final
						Class[] parameterTypes = possibleMatch.getParameterTypes();

						if (possibleMatch.isVarArgs())
						{
							_kludge_incompatibleType=null;

							if (containsOnlyPrimitiveAndConvertableArrayedTypes(parameterTypes))
							{
								if (constructor==null)
								{
									constructor = possibleMatch;
								}
								else
								{
									System.err.println("WARNING: this version of FuzzyEntryPoint cannot descriminate between multiple varargs constructors");
								}
							}
							else
							if (mostPlausibleError==null)
							{
								mostPlausibleError=_kludge_incompatibleType;
							}
						}
					}
				}

				final
				boolean varargsMatch=(!exactConstructorMatch && constructor!=null);

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
					final
					Object[] context=new Object[]{aClass.getSimpleName()," constructor argument #",i,": "};

					if (parameterTypes[i].isArray())
					{
						if (constructor.isVarArgs() && i==l-1)
						{
							//If we are on the 'last parameter' and it is an array type, then we are probably dealing with a varargs
							//In which case, just push all the remaining parameters into a single array
							constructorParameters[i] = Convert.dumpRemainingVarargs(constructorArguments, i,
																				parameterTypes[i], context);

						}
						else
						{
							constructorParameters[i] = Convert.stringToArray(constructorArguments.get(i),
																				parameterTypes[i], context);
						}
					}
					else
					{
						constructorParameters[i] = Convert.stringToBasicObject(constructorArguments.get(i),
																				  parameterTypes[i], context);
					}
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
				//If there is no "no-args" constructor, maybe we can settle for any "empty" varargs constructor?
				for (Constructor constructor : aClass.getConstructors())
				{
					if (constructor.isVarArgs() && constructor.getParameterTypes().length==1)
					{
						try
						{
							final
							Class componentType = constructor.getParameterTypes()[0].getComponentType();

							final
							Object emptyArray = Array.newInstance(componentType, 0);

							instance=constructor.newInstance(emptyArray);
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

				printResult(o);

				if (INTERPRETIVE_EXIT_STATUS && seemsToIndicateNegativeResult(o))
				{
					System.exit(1);
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
		if (invocationsWithResults>0)
		{
			if (INTERPRETIVE_EXIT_STATUS && invocationsWithNegativeResults>0)
			{
				System.exit(1);
			}
		}
		else
		{
			throw new UnsupportedOperationException(aClass+" does not implement a supported executable interface, and no data-yielding instance functions were called: "+instance);
		}

		if (false)
		{
			final
			long postConstructionRuntime=System.currentTimeMillis()-preRunTime;

			System.err.println(String.format("internal execution time: %dms (not including initialization overhead)",
												postConstructionRuntime));
		}

		//NB: we don't System.exit(0), as some applications might have background threads, etc.
		//It is best to mirror the JVM's exit policy in these non-error cases.
	}

	private
	void printResult(Object o)
	{
		if (o==null)
		{
			System.out.println("null");
		}
		else
		if (o instanceof Iterable)
		{
			//NB: Collection extends Iterable, and all the good utility types (List, Set, Map) extend Collection.
			for (Object o1 : ((Iterable) o))
			{
				printResult(o1);
			}
		}
		else
		if (o instanceof Map)
		{
			//NB: Map is not a Collection, nor do the common utilities extend/implement a collection
			for (Map.Entry me : ((Map<?,?>) o).entrySet())
			{
				System.out.print(me.getKey());
				//In unix, fields are often separated by tabs, whitespace, or commas.
				//We will pick the the separator deemed least likely to be in an actual key/value... TAB.
				System.out.print('\t');
				System.out.println(me.getValue());
			}
		}
		else
		if (o.getClass().isArray())
		{
			final
			int l=Array.getLength(o);

			for (int i=0; i<l; i++)
			{
				printResult(Array.get(o, i));
			}
		}
		else
		{
			System.out.println(String.valueOf(o));
		}
		/*
		TODO: what are some other output types that might be useful?
		----------------
		OutputStream - seems a bit odd, but straight forward.
		Runnable/Callable - composite execution???
		 */
	}

	public static
	boolean seemsToIndicateNegativeResult(Object o)
	{
		if (o == null)
		{
			return true;
		}

		if (o instanceof Boolean)
		{
			return !((Boolean)o).booleanValue();
		}

		if (o instanceof Double)
		{
			final
			Double d=(Double)o;

			return d.isNaN() || d.isInfinite() || d.doubleValue()==0.0d;
		}

		if (o instanceof Float)
		{
			final
			Float f=(Float)o;

			return f.isNaN() || f.isInfinite() || f.floatValue()==0.0f;
		}

		//Integer, Long, Byte, Short, etc...
		if (o instanceof Number)
		{
			final
			Number number=(Number)o;

			return number.longValue()==0;
		}

		if (o instanceof Collection)
		{
			return ((Collection)o).isEmpty();
		}

		return false;
	}

	/**
	 * @param parameterTypes
	 * @return true if the given array of java types contains only supported types that can be interpreted from the command line
	 */
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
	boolean containsOnlyPrimitiveAndConvertableArrayedTypes(Class[] parameterTypes)
	{
		for (Class parameterType : parameterTypes)
		{
			if (parameterType.isArray())
			{
				//System.err.println("testing "+parameterType+" falls into a test for "+parameterType.getComponentType());
				parameterType=parameterType.getComponentType();
			}

			if (!parameterType.isPrimitive() && !Convert.isSupportedType(parameterType))
			{
				//System.err.println("not primitive or convertible: "+parameterType);
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

		return method;
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
				if (!matchable.equals("help") || !matchable.equals("usage"))
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
		Method method=null;
		boolean withPrintStream=false;

		try
		{
			method=aClass.getMethod("usage", PrintStream.class);
			withPrintStream=true;
		}
		catch (NoSuchMethodException e)
		{
			//no-op...
		}

		if (method==null || !Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers()))
		{
			try
			{
				method=aClass.getMethod("usage");
				withPrintStream=false;
			}
			catch (NoSuchMethodException e1)
			{
				//no-op...
			}
		}

		if (method==null || !Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers()))
		{
			try
			{
				method=aClass.getMethod("help", PrintStream.class);
				withPrintStream=true;
			}
			catch (NoSuchMethodException e)
			{
				//no-op...
			}
		}

		if (method==null || !Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers()))
		{
			try
			{
				method=aClass.getMethod("help");
				withPrintStream=false;
			}
			catch (NoSuchMethodException e1)
			{
				//no-op...
			}
		}

		if (method!=null && Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers()))
		{
			try
			{
				if (withPrintStream)
				{
					method.invoke(null, System.err);
				}
				else
				{
					method.invoke(null);
				}

				System.exit(1);
			}
			catch (IllegalAccessException e)
			{
				e.printStackTrace();
				//fall-though... probably will get a more meaningful message.
			}
			catch (InvocationTargetException e)
			{
				e.getCause().printStackTrace();
				//fall-through...
			}
		}

		fabricateUsageMessage(System.err);
		System.exit(1);
	}

	public
	void fabricateUsageMessage(PrintStream e)
	{
		boolean hasPublicConstructor=false;

		for (Constructor constructor : aClass.getConstructors())
		{
			if (Modifier.isPublic(constructor.getModifiers()))
			{
				hasPublicConstructor=true;

				final
				Class[] parameterTypes = constructor.getParameterTypes();

				if (containsOnlyPrimitiveAndConvertableArrayedTypes(parameterTypes))
				{
					e.print("usage: ");
					e.print(aClass.getSimpleName());

					for (Class parameterType : parameterTypes)
					{
						e.print(' ');
						e.print('<');

						if (parameterType.isArray())
						{
							e.print(parameterType.getComponentType().getSimpleName());
							e.print(",[...]");
						}
						else
						{
							e.print(parameterType.getSimpleName());
						}
						e.print('>');
					}

					/*
					if (constructor.isVarArgs())
					{
						e.print(" [...]");
					}
					*/

					e.println();
				}
			}
		}

		if (!hasPublicConstructor)
		{
			System.err.println(aClass.toString() + " has no public constructors; static options only.");
		}

		//NB: may throw if options are not consistent.
		if (explicitShortOptionsByCode==null) doLazyMethodReflectionAnalysis();

		e.println("options:");

		for (Map.Entry<Character, Method> me : implicitShortOptionsByCode.entrySet())
		{
			if (!explicitShortOptionsByCode.containsKey(me.getKey()))
			{
				e.print('\t');
				e.print('-');
				e.print(me.getKey());
				e.print('\t');
				e.print(me.getValue().getName());

				for (Class parameterType : me.getValue().getParameterTypes())
				{
					e.print(' ');
					e.print('<');
					e.print(parameterType.getSimpleName());
					e.print('>');
				}

				e.println();
			}
		}

		for (Map.Entry<Character, Method> me : explicitShortOptionsByCode.entrySet())
		{
			e.print('\t');
			e.print('-');
			e.print(me.getKey());

			for (Class parameterType : me.getValue().getParameterTypes())
			{
				e.print(' ');
				e.print('<');
				e.print(parameterType.getSimpleName());
				e.print('>');
			}

			e.print('\t');
			e.println(getDescription(me.getValue()));
		}

		for (Map.Entry<String, Method> me : primaryOptions.entrySet())
		{
			if (wellKnownMethodIsExpectedToBeUseless(me.getKey()))
			{
				continue;
			}

			e.print("\t--");
			e.print(me.getKey());

			for (Class parameterType : me.getValue().getParameterTypes())
			{
				e.print(' ');
				e.print('<');
				e.print(parameterType.getSimpleName());
				e.print('>');
			}

			e.print('\t');
			e.println(getDescription(me.getValue()));
		}
	}

	private
	boolean wellKnownMethodIsExpectedToBeUseless(String name)
	{
		//I find it highly unlikely that someone would find use for the synchronization functions
		//so long as we are launching a new vm.
		return name.equals("notify") || name.equals("notify-all");

		//NB: "wait()", on the other-hand, *could* be potentially useful... I think...
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
		explicitShortOptionsByCode = new HashMap<Character, Method>();
		implicitShortOptionsByCode = new HashMap<Character, Method>();
		primaryOptions = new TreeMap<String, Method>();
		backupOptions = new HashMap<String, Method>();

		for (Method method : getAllPublicMethods())
		{
			if (!containsOnlyPrimitiveAndConvertableArrayedTypes(method.getParameterTypes()))
			{
				continue;
			}

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

					if (primaryOptions.put(specLong, method)!=null)
					{
						//throw new IllegalAccessError("multiple @CommandLineOptions or method names for long-option: '"+specLong+"'");
					}
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

			final
			String methodName = method.getName();

			final
			String methodId = stripSetPrefix(methodName);

			final
			List<String> bits = explodeIdentifier(methodId);

			if (bits.size() == 1)
			{
				final
				String onlyBit = bits.get(0);

				final
				String key=onlyBit.toLowerCase();

				if (onlyBit.length() == 1)
				{
					final
					Character onlyCharacter = onlyBit.charAt(0);

					if (implicitShortOptionsByCode.containsKey(onlyCharacter))
					{
						implicitShortOptionsByCode.put(onlyCharacter, null);
					}
					else
					{
						implicitShortOptionsByCode.put(onlyBit.charAt(0), method);
					}
				}
				else
				if (specLong==null && !primaryOptions.containsKey(key))
				{
					//Stick it in the primary options to (hopefully) include it in the usage statement
					primaryOptions.put(key, method);
				}
				else
				{
					backupOptions.put(key, method);
				}
			}
			else
			{
				final
				String primaryKey=derivePrimaryKey(bits);

				final
				String secondaryKey=deriveSecondaryKey(bits);

				if (specLong==null && !primaryOptions.containsKey(primaryKey))
				{
					primaryOptions.put(primaryKey, method);
				}
				else
				{
					backupOptions.put(primaryKey, method);
				}

				backupOptions.put(secondaryKey, method);
			}

			backupOptions.put(methodName.toLowerCase(), method);
			backupOptions.put(methodId.toLowerCase(), method);
		}

		if (false)
		{
			System.err.println("\nPrinting usage for method detection summary:");
			fabricateUsageMessage(System.err);

			for (Map.Entry<String, Method> me : backupOptions.entrySet())
			{
				System.err.println(String.format("backup: %s -> %s", me.getKey(), me.getValue().getName()));
			}
		}
	}

	private
	List<Method> allPublicMethods;

	private
	List<Method> getAllPublicMethods()
	{
		if (allPublicMethods==null)
		{
			allPublicMethods = new ArrayList<Method>();
			collectPublicMethods(allPublicMethods, aClass);
			Collections.sort(allPublicMethods, byMethodName);
		}
		return allPublicMethods;
	}

	private
	void collectPublicMethods(List<Method> methods, Class aClass)
	{
		for (Method method : aClass.getDeclaredMethods())
		{
			if (Modifier.isPublic(method.getModifiers()))
			{
				methods.add(method);
			}
		}

		final
		Class superclass = aClass.getSuperclass();

		if (superclass!=null)
		{
			collectPublicMethods(methods, superclass);
		}
	}

	private
	String derivePrimaryKey(List<String> bits)
	{
		final
		StringBuilder sb=new StringBuilder();

		for (String s : bits)
		{
			if (sb.length()!=0)
			{
				sb.append('-');
			}

			sb.append(s.toLowerCase());
		}

		return sb.toString();
	}

	private
	String deriveSecondaryKey(List<String> bits)
	{
		final
		StringBuilder sb=new StringBuilder();

		for (String s : bits)
		{
			sb.append(s.toLowerCase());
		}

		return sb.toString();
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

	/**
	 * Loosely based on TapestryInternalUtils.toUserPresentable()
	 *
	 * @param methodId
	 * @return
	 */
	public static
	List<String> explodeIdentifier(String methodId)
	{
		final
		List<String> list=new ArrayList<String>(methodId.length()/2);

		final
		char[] characters=methodId.toCharArray();

		final
		StringBuilder sb=new StringBuilder();

		boolean lastWasLowerCase=false;
		boolean lastWasDigit=false;

		for (char c : characters)
		{
			if (Character.isUpperCase(c))
			{
				if (lastWasLowerCase || lastWasDigit)
				{
					lastWasLowerCase=false;
					list.add(sb.toString());
					sb.setLength(0);
				}
			}
			else
			{
				lastWasLowerCase=true;
			}

			if (Character.isDigit(c))
			{
				if (!lastWasDigit)
				{
					lastWasDigit=true;
					list.add(sb.toString());
					sb.setLength(0);
				}
			}
			else
			{
				lastWasDigit=false;
			}

			if (c!='_')
			{
				sb.append(c);
			}
		}

		if (sb.length()!=0)
		{
			list.add(sb.toString());
		}

		if (false)
		{
			System.err.println("EXPLODE: '"+methodId+"'");

			for (String s : list)
			{
				System.err.print("\t");
				System.err.println(s);
			}
		}

		return list;
	}

	private
	int invocationsWithResults;

	private
	int invocationsWithNegativeResults=0;

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
		Invocation(Method method, List<Object> args)
		{
			if (method == null) throw new NullPointerException("method is null");
			if (args == null) throw new NullPointerException("method arguments list is null");

			this.method = method;

			final
			Class[] parameterTypes = method.getParameterTypes();

			parameters = args.toArray(new Object[args.size()]);
		}

		public
		void invoke(Object target) throws InvocationTargetException, IllegalAccessException
		{
			Object result = method.invoke(target, parameters);

			if (result!=null)
			{
				invocationsWithResults++;

				if (seemsToIndicateNegativeResult(result))
				{
					invocationsWithNegativeResults++;
				}

				printResult(result);
			}
		}
	}
}
