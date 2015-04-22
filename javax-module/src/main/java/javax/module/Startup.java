package javax.module;

import javax.module.tools.Convert;
import javax.module.tools.FuzzyEntryPoint;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.net.URL;
import java.text.ParseException;

/**
 *
 */
public
class Startup extends ClassLoader
{
	private static final
	boolean ROOT_MODULE_DEFINES_CONTEXT = Boolean.parseBoolean(System.getProperty("ROOT_MODULE_DEFINES_CONTEXT", "true"));

	//TODO: how can we reliably detect this at runtime? is it enough to set it to true in the main() method?
	public static final boolean MODULAR_VM_STARTUP = true;

	private final
	File moduleDirectory;

	private final
	ModuleKey rootModuleKey;

	private
	ModuleContext context;

	public
	Startup(File moduleDirectory, ModuleKey rootModule, ClassLoader fallback)
	{
		super(fallback);
		this.moduleDirectory = moduleDirectory;
		this.rootModuleKey = rootModule;
	}

	private static
	String getClassProperty() throws ParseException
	{
		String classProperty = System.getProperty("class");
		if (classProperty == null)
		{
			throw new RuntimeException("'class' system property undefined");
		}
		return classProperty;
	}

	private static
	ModuleKey getModuleProperty() throws ParseException
	{
		String module = System.getProperty("module");
		if (module == null)
		{
			throw new RuntimeException("'module' system property undefined");
		}
		return ModuleKey.parseModuleKey(module);
	}

	protected
	URL findResource(String name)
	{
		if (DEBUG)
		{
			System.err.println(name + ":\tresrc  [ " + context + " ] (Startup Laoder)");
		}
		return null;
	}

	/**
	 * The modular classloader should take care of all permitted requests from modularized
	 * classes, this is only for 'bootstrapping' the *first* request for a class, which is
	 * the entry point. Therefore, this implementation of findClass will only return a non-
	 * system class *ONCE*. This is especially important for subordinate-classloaders,
	 * which will be calling this (and the bootstrap loader) for help *first*.
	 */
	@Override
	protected
	Class loadClass(String name, boolean resolve) throws ClassNotFoundException
	{
		if (DEBUG)
		{
			System.err.println("Startup.findClass(" + name + ")");
		}

		try
		{
			return super.loadClass(name, resolve);
		}
		catch (ClassNotFoundException cnfe)
		{
			try
			{
				if (context == null)
				{
					if (DEBUG)
					{
						System.err.println("Startup: creating root context for " + rootModuleKey);
					}
					context = new ModuleContext(rootModuleKey.getModuleName()+"-startup", moduleDirectory);
					//addExtraModuleDirectories(context);
					context.addModule(rootModuleKey);

					/*
					for (ModuleKey extra : getExtraModules())
					{
						context.addModule(extra);
					}
					*/

					if (ROOT_MODULE_DEFINES_CONTEXT)
					{
						try
						{
							ModuleLoader moduleLoader = context.getModuleLoaderFor(rootModuleKey);

							for (Dependency dependency : moduleLoader.getModule().getDependencies())
							{
								//System.err.println("Adding to startup context: "+dependency);
								context.addModule(dependency);
							}
						}
						catch (ModuleNotFoundException e)
						{
							throw new RuntimeException(e);
						}
					}
					else
					{
						System.err.println("NOTICE: Strict startup context (not adding initial module dependencies thereto)");
					}
				}
				else
				if (!CHECK_ALL)
				{
					throw new ClassNotFoundException(name + ": via module-startup class loader (try setting 'root.module.open' property to 'true')");
				}

				Class retval = context.findClassTopFirst(name, rootModuleKey);
				if (retval == null)
				{
					if (DEBUG)
					{
						System.err.println("Startup: not in root module: " + name);
					}
					throw cnfe;
				}
				if (resolve)
				{
					resolveClass(retval);
				}
				return retval;
			}
			catch (IOException e)
			{
				if (DEBUG)
				{
					e.printStackTrace();
				}
				throw new ClassNotFoundException("cannot load root module", e);
			}
			catch (ModuleAccessDeniedException e)
			{
				if (DEBUG)
				{
					e.printStackTrace();
				}
				throw new ClassNotFoundException("cannot access module", e);
			}
		}
		//throw new ClassNotFoundException("not in startup context: "+name);
	}

	/**
	 * In a pinch, this option can help force custom-classloaders into subordination
	 * by making there parent class loader always check the "root" context for classes.
	 * This may work will a well-formed class set, but tends to lead to linkage errors
	 * if ever more than one version of a class is running.
	 */
	private static final boolean CHECK_ALL       = Boolean.getBoolean("root.module.open");
	private static final boolean DEBUG           = Boolean.getBoolean("debug.module.loader");
	//private static final File    moduleDirectory = getDefaultModuleDirectory();

	public
	File getModuleDirectory()
	{
		return moduleDirectory;
	}

	private static
	File getDefaultModuleDirectory()
	{
		String specified = System.getProperty("module.directory");
		if (specified != null)
		{
			return new File(specified);
		}
		else
		{
			boolean windows = (File.separatorChar != '/');
			if (windows)
			{
				String programFiles = System.getenv().get("PROGRAMFILES");
				return new File(programFiles, "Java Modules");
			}
			else
			{
				return new File("/usr/share/java");
			}
		}
	}

	/*
	static private
	void addExtraModuleDirectories(ModuleContext context)
	{
		int i = 1;
		int misses = 0;
		while (misses < 5)
		{
			i++;
			String propName = "module.directory" + i;
			String maybeDir = System.getProperty(propName);
			if (maybeDir == null)
			{
				misses++;
				continue;
			}
			misses = 0;
			context.addModuleDirectory(new File(maybeDir), true);
		}
	}

	List<ModuleKey> getExtraModules() throws IOException, ParseException
	{
		List<ModuleKey> retval = new ArrayList();
		int i = 1;
		int misses = 0;
		while (misses < 5)
		{
			i++;
			String propName = "module" + i;
			String moduleName = System.getProperty(propName);
			if (moduleName == null)
			{
				misses++;
				continue;
			}
			misses = 0;
			retval.add(ModuleKey.parseModuleKey(moduleName));
		}
		String filename = System.getProperty("module.reqs");
		if (filename != null)
		{
			if (DEBUG)
			{
				System.err.println("Startup.readReqs(" + filename + ")");
			}
			BufferedReader br = new BufferedReader(new FileReader(filename));
			try
			{
				String line;
				while ((line = br.readLine()) != null)
				{
					if (DEBUG)
					{
						System.err.println("Startup.add: " + line);
					}
					retval.add(Dependency.fromLine(line, rootModuleKey));
				}
			}
			finally
			{
				br.close();
			}
		}
		// check for duplicate module names (esp. 'root' module)?
		return retval;
	}
	*/

	public static
	void main(String[] args) throws ParseException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException
	{
		ModuleKey rootModule=getModuleProperty();
		String mainClassName=getClassProperty();
		File moduleDirectory=getDefaultModuleDirectory();

		Startup startup=new Startup(moduleDirectory, rootModule, null);

		Class aClass = startup.loadClass(mainClassName);

		/*
		 * This is surprisingly important in allowing abstract/subordinate classloaders to work...
		 * Yet, from a java purist perspective, would be rather arbitrary.
		 *
		 * TODO: this should arguably be the ContextLoader (not the module loader), but then we would need more support (like FALL_OPEN) therein.
		 */
		Thread.currentThread().setContextClassLoader(aClass.getClassLoader());

		try
		{
			if (aClass.getAnnotation(LegacyMainMethod.class)!=null)
			{
				throw new NoSuchMethodException();
			}

			//Always try to find a main method first...
			final
			Method method = aClass.getMethod("main", String[].class);

			if (method.getAnnotation(LegacyMainMethod.class)!=null)
			{
				throw new NoSuchMethodException();
			}

			method.invoke(null, new Object[]{args});
		}
		catch (InvocationTargetException e)
		{
			e.getCause().printStackTrace();
			System.exit(1);
		}
		catch (NoSuchMethodException e)
		{
			//TODO: BUG: the *standard* path should not include throwing & catching an exception!

			//So... they do not have a main(String[] args) method... maybe they are a new-fangled "runnable"?
			if (true || FuzzyEntryPoint.supports(aClass))
			{
				new FuzzyEntryPoint(aClass).execute(args);
			}
			else
			{
				System.err.println(aClass+" does not implement Runnable or Callable");
				throw e;
			}
		}
	}

}
