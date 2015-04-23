package javax.module;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by robert on 10/30/14.
 */
final
class ModuleLoader extends ClassLoader
{
	public static
	ModuleLoader forClass(Class c)
	{
		ClassLoader classLoader = c.getClassLoader();

		if (classLoader instanceof ModuleLoader)
		{
			return (ModuleLoader)classLoader;
		}
		else
		{
			throw new IllegalArgumentException(c+" was not loaded from a module");
		}
	}

	private static final boolean DEBUG = Boolean.getBoolean("debug.module.loader");
	private static final boolean FALL_OPEN = Boolean.getBoolean("module.loader.fall.open");

	private final Module  module;
	private final ModuleContext context;

	public
	ModuleLoader(ModuleContext context, Module module)
	{
		this.context = context;
		this.module = module;
		maybeLoadModuleInterface();
	}

	Module getModule()
	{
		return module;
	}

	ModuleContext getContext()
	{
		return context;
	}

	/**
	 * Returns the module-key identification for this module loader. If this
	 * module was first loaded as a dependency, the returned module key will
	 * actually be a Dependency oject, wherefrom the requesting module can be
	 * decerned.
	 */
	public
	ModuleKey getModuleKey()
	{
		return module.getModuleKey();
	}

	public
	Class loadClass(String name, boolean resolve) throws ClassNotFoundException
	{
		if (DEBUG)
		{
			System.err.println(name + ":\tload-c [ " + context + " :: " + getModuleKey() + " ] resolve=" + resolve);
		}
		return super.loadClass(name, resolve);
	}

	/**
	 * Searches for a class to be defined by this module. More specifically, this
	 * mechanism does not search any of the modules dependencies.
	 */
	Class findClassInThisModule(String name) throws IOException
	{
		Class retval = findLoadedClass(name);
		if (retval != null)
		{
			if (DEBUG)
			{
				System.err.println(name + ":\tprevis [ " + context + " :: " + getModuleKey() + " ]");
			}
			return retval;
		}

		String className = name.replace('.', '/') + ".class";
		//@bug: surely this is far from optimal?
		InputStream is = module.getClassAsStream(className);
		if (is == null)
		{
			if (DEBUG)
			{
				System.err.println(name + ":\tnot in [ " + context + " :: " + getModuleKey() + " ]");
			}
			return null;
		}
		if (DEBUG)
		{
			System.err.println(name + ":\tCREATE [ " + context + " :: " + getModuleKey() + " ]");
		}
		byte[] bs = getBytes(is);
		return defineClass(name, bs, 0, bs.length);
	}

	/**
	 * The default class-finding mechanism for all classes which are executing within this
	 * module. Searches both the module and its immediate dependencies.
	 */
	public
	Class findClass(String name) throws ClassNotFoundException
	{
		Class retval;

		//we are a module-in-context loader, only check our jars & 1st-level dependencies
		if (DEBUG)
		{
			System.err.println(name + ":\tneeded [ " + context + " :: " + getModuleKey() + " ]");
		}

		if (name.indexOf(':') > 0)
		{
			if (DEBUG)
			{
				System.err.println(name + ":\tDIRECT [ " + context + " ] (request from " + getModuleKey() + ")");
			}
			try
			{
				retval = context.findClassTopFirst(name, getModuleKey());
			}
			catch (IOException e)
			{
				throw new ClassNotFoundException(name + ": could not load absolute-module-class reference", e);
			}
			catch (ModuleAccessDeniedException e)
			{
				throw new ClassNotFoundException(name + ": denied absolute-module-class", e);
			}
			if (retval == null)
			{
				throw new ClassNotFoundException("absolute-module-class reference failed");
			}
			if (DEBUG)
			{
				System.err.println(name + ":\tFOUND [ " + context + " :: " + getModuleKey() + " ] : " + retval);
			}
			return retval;
		}

		try
		{
			retval = findClassInThisModule(name);
			if (retval != null)
			{
				if (DEBUG)
				{
					System.err.println(name + ":\tlocal  [ " + context + " :: " + getModuleKey() + " ]");
				}
				return retval;
			}
		}
		catch (IOException e)
		{
			throw new ClassNotFoundException("cannot load data from module: " + getModuleKey(), e);
		}

		ClassNotFoundException err = null;

		//it is not, check all our dependencies (**only** one-level deep; otherwise it would be a dependency!)
		Iterator<Dependency> i = module.getDependencies().iterator();

		while (i.hasNext())
		{
			Dependency dep = i.next();
			if (DEBUG)
			{
				System.err.println(name + ":\tcheck  [ " + context + " :: " + getModuleKey() + " / " + dep + " ]");
			}
			try
			{
				ModuleLoader m2 = context.getModuleLoaderFor(dep);
				retval = m2.findClassInThisModule(name);
				if (retval != null)
				{
					if (DEBUG)
					{
						System.err.println(name + ":\tremote [ " + context + " :: " + getModuleKey() + " / " + dep + " ]");
					}
					m2.checkAccess(retval, getModuleKey(), context);
					usingDependency(m2);
					return retval;
				}
			}
			catch (IOException e)
			{
				throw new ClassNotFoundException("cannot load data from module: " + dep, e);
			}
			catch (ModuleAccessDeniedException e)
			{
				if (err == null)
				{
					err = new ClassNotFoundException("cannot access module", e);
				}
			}
			catch (ModuleNotFoundException e)
			{
				if (err == null)
				{
					err = new ClassNotFoundException("cannot find dependency: " + dep, e);
				}
			}
		}
		/*
		 it is not:
		  - a system class
		  - one of our classes
		  - a class we linked against

		 Therefore it must be a Class.forName() (or similar) call. This search will only succeed if
		 a module containing the class in question has been added to one of our available contexts.
		 */
		try
		{
			retval = context.findClassTopFirst(name, getModuleKey());
		}
		catch (IOException e)
		{
			throw new ClassNotFoundException("io exception in class loading", e);
		}
		catch (ModuleAccessDeniedException e)
		{
			if (err == null)
			{
				err = new ClassNotFoundException("cannot access module", e);
			}
		}

		if (retval != null)
		{
			if (DEBUG)
			{
				System.err.println(name + ":\tCONTXT [ " + context + " ]");
			}
			return retval;
		}

		if (err != null)
		{
			throw err;
		}

		if (DEBUG)
		{
			System.err.println(name + ":\t*DNE*  [ " + context + " :: " + getModuleKey() + " ]");
		}

		if (FALL_OPEN)
		{
			Set<ModuleKey> alreadyChecked=new HashSet<ModuleKey>(module.getDependencies());
			alreadyChecked.add(getModuleKey());

			retval=context.findClassInEntireModuleDependencyTree(name, alreadyChecked);

			if (retval==null)
			{
				throw new ClassNotFoundException(name + " is not in " + getModuleKey() + ", its direct dependencies, or any module transitively-linked from the startup context.");
			}
			else
			{
				String considerAddingMessage=getConsiderAddingMessage(retval);
				System.err.println("\nWARN: "+getModuleKey()+" usually cannot access the class: '"+name+"'."+considerAddingMessage);
				return retval;
			}
		}

		throw new ClassNotFoundException(name + " is not in " + getModuleKey() + " or its direct dependencies.");
	}

	private
	String getConsiderAddingMessage(Class aClass)
	{
		ClassLoader classLoader = aClass.getClassLoader();

		if (classLoader instanceof ModuleLoader)
		{
			ModuleLoader moduleLoader=(ModuleLoader)classLoader;

			ModuleKey moduleKey=moduleLoader.getModuleKey();

			File depsFile=module.getDependenciesFile();

			if (moduleKey instanceof Dependency)
			{
				Dependency dependency=(Dependency)moduleKey;
				return String.format(" Consider adding '%s' (from '%s') to this module's dependency list: %s", moduleKey, dependency.getRequestingModuleKey(), depsFile);
			}
			else
			{
				return String.format(" Consider adding '%s' to dependency list: %s", moduleKey, depsFile);
			}
		}
		else
		{
			return null;
		}
	}

	private static
	byte[] getBytes(InputStream is) throws IOException
	{
		byte[] buf = new byte[4096];
		ByteArrayOutputStream bos = new ByteArrayOutputStream(4096);
		int count;
		while ((count = is.read(buf)) > 0)
		{
			bos.write(buf, 0, count);
		}
		return bos.toByteArray();
	}

	private String          moduleInterfaceName;
	private ModuleInterface moduleInterface;

	private
	void maybeLoadModuleInterface()
	{
		moduleInterfaceName = module.getProperties().getProperty(ModuleInterface.PROPERTY_KEY);
	}

	private
	ModuleInterface getModuleInterface()
	{
		String className = moduleInterfaceName;
		if (className != null && moduleInterface == null)
		{
			try
			{
				Class c = findClassInThisModule(className);
				if (c == null)
				{
					throw new IllegalArgumentException("module interface cannot be found: " + className);
				}
				Object o = c.newInstance();
				if (o instanceof ModuleInterface)
				{
					moduleInterface = (ModuleInterface) o;
				}
				else
				{
					throw new IllegalArgumentException("module interface does not implement ModuleInterface: " + className);
				}
			}
			catch (IOException e)
			{
				throw new RuntimeException("cannot load module interface class", e);
			}
			catch (InstantiationException e)
			{
				throw new RuntimeException("cannot create module interface", e);
			}
			catch (IllegalAccessException e)
			{
				throw new RuntimeException("cannot access module interface", e);
			}
			moduleInterface.moduleLoaded();
		}
		return moduleInterface;
	}

	void checkAccess(Class c, ModuleKey requestor, ModuleContext ctx) throws ModuleAccessDeniedException
	{
		if (moduleInterfaceName != null)
		{
			getModuleInterface().checkAccess(c, requestor, ctx == context);
		}
	}

	/**
	 * This reference count is only used to send the moduleUnloaded() signal to modules.
	 */
	private int refCount;

	public synchronized
	void incRefCount()
	{
		refCount++;
	}

	public synchronized
	void decRefCount()
	{
		refCount--;
		if (refCount == 0)
		{
			signalModuleUnload();
		}
	}

	protected
	void finalize()
	{
		signalModuleUnload();
	}

	private
	void signalModuleUnload()
	{
		if (moduleInterface != null)
		{
			try
			{
				moduleInterface.moduleUnloaded();
				//@todo: research more "forceful" termination (like search & destroy w/ custom classes)
			}
			catch (Throwable t)
			{
				t.printStackTrace();
			}
			moduleInterface = null;
		}
		for (ModuleLoader m : referencedDependencies)
		{
			m.decRefCount();
		}
		referencedDependencies.clear();
		referencedDependencies = null;
	}

	private HashSet<ModuleLoader> referencedDependencies = new HashSet();

	private
	void usingDependency(ModuleLoader dep)
	{
		if (referencedDependencies != null &&
				referencedDependencies.add(dep))
		{
			dep.incRefCount();
		}
	}

	/**
	 * Unlike classes, which must have the "right" class data at load time,
	 * access to resources are quite override-centric.
	 * <p/>
	 * e.g. one can change all the images in an interface by adding a "my-images"
	 * module to the root context.
	 * <p/>
	 * (i.e. always having newer/higher versions replace those in the given jar/module).
	 * In this way, one can change the images of a deep jar file by placing a
	 * "image-mods" module in it's context/super-context.
	 */
	protected
	URL findResource(String name)
	{
		if (DEBUG)
		{
			System.err.println(name + ":\tresrc  [ " + context + " :: " + getModuleKey() + " ]");
		}

		//TODO: revisit the decision to locate resources top-first... for some applications (like locating class files) it may be totally wrong.
		URL retval = context.findResourceTopFirst(name);

		if (retval != null)
		{
			if (DEBUG)
			{
				System.err.println(name + ":\tcontxt [ " + context + " :: " + getModuleKey() + " ]");
			}
			return retval;
		}

		//find in our jars
		retval = findResourceInThisModule(name);

		if (retval != null)
		{
			if (DEBUG)
			{
				System.err.println(name + ":\tlocal  [ " + context + " :: " + getModuleKey() + " ]");
			}
			return retval;
		}

		IOException err = null;

		//find in our deps jars
		for (Dependency dep : module.getDependencies())
		{
			try
			{
				ModuleLoader m2 = context.getModuleLoaderFor(dep);
				retval = m2.findResourceInThisModule(name);
				if (retval != null)
				{
					if (DEBUG)
					{
						System.err.println(name + ":\tremote [ " + context + " :: " + getModuleKey() + " / " + dep + " ]");
					}
					//@todo m2.checkResourceAccess(retval, getModuleKey(), context);
					usingDependency(m2);
					return retval;
				}
			}
			catch (IOException e)
			{
				if (err == null)
				{
					err = e;
				}
			}
			catch (ModuleNotFoundException e)
			{
				if (err == null)
				{
					err = new ModuleLoadException(dep, e);
				}
			}
			catch (ModuleAccessDeniedException e)
			{
				if (err == null)
				{
					err = new ModuleLoadException(dep, e);
				}
			}
		}
		//NB: inaccesible resource calls for 'null' return
		if (err != null)
		{
			if (DEBUG)
			{
				err.printStackTrace();
			}
			else
			{
				System.err.println(err.toString());
			}
		}
		return null;
	}

	public
	URL findResourceInThisModule(String name)
	{
		return module.getResourceAsURL(name);
	}

	Class getDirectClassProxy(String directName, Class orig) throws IOException
	{
		synchronized (this)
		{
			Class c = findLoadedClass(directName);
			if (c != null)
			{
				return c;
			}
			ModuleNameStrippingByteCodeClassExtender se = new ModuleNameStrippingByteCodeClassExtender(orig.getName(), directName);
			byte[] bs = se.getClassData();
			c = defineClass(directName, bs, 0, bs.length);
			return c;
		}
	}

	@Override
	public
	Enumeration<URL> getResources(String name) throws IOException
	{
		final
		Set<URL> retval = new LinkedHashSet<URL>();

		retval.addAll(Collections.list(getContext().getClassLoader().getResources(name)));

		{
			final
			URL url = findResourceInThisModule(name);

			if (url==null)
			{
				//System.err.println("no such resource: "+this+" (this)");
			}
			else
			{
				retval.add(url);
			}
		}

		for (Dependency dep : module.getDependencies())
		{
			try
			{
				final
				ModuleLoader m2 = context.getModuleLoaderFor(dep);

				final
				URL url = m2.findResourceInThisModule(name);

				if (url==null)
				{
					//System.err.println("no such resource: "+dep+" (dep)");
				}
				else
				{
					retval.add(url);
					//???: usingDependency(m2);
				}
			}
			catch (IOException e)
			{
				//no-op... maybe emit a warning?
			}
			catch (ModuleNotFoundException e)
			{
				//no-op... maybe emit a warning?
			}
			catch (ModuleAccessDeniedException e)
			{
				//no-op... maybe emit a warning?
			}
		}

		return Collections.enumeration(retval);
	}

	@Override
	public
	String toString()
	{
		return super.toString()+":"+getModuleKey();
	}
}

