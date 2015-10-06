package javax.module;

import javax.module.util.Dependency;
import javax.module.util.ModuleKey;
import javax.module.util.VersionString;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by robert on 10/30/14.
 */
public final
class ModuleContext
{

	/**
	 * Returns the context object for which the calling class is a member,
	 * or null if the calling class is not executing within a module context.
	 */
	public static
	ModuleContext getContext()
	{
		Class c = sun.reflect.Reflection.getCallerClass(2);
		return ModuleLoader.forClass(c).getContext();
	}

	/**
	 * Returns the module key (i.e. name and version) of the calling class's module,
	 * or null if operating outside of a module system execution context. If the
	 * module was first loaded as a dependency, the returned module key will actually
	 * be a Dependency object, wherefrom the requesting module can be decerned.
	 */
	public static
	ModuleKey getModuleKey()
	{
		Class c = sun.reflect.Reflection.getCallerClass(2);
		return ModuleLoader.forClass(c).getModuleKey();
	}

	/**
	 * Returns the module instance (mostly for files & resources) of the calling
	 * class's module, or null if operating outside of a module system execution
	 * context.
	 */
	public static
	Module getModule()
	{
		Class c = sun.reflect.Reflection.getCallerClass(2);
		return ModuleLoader.forClass(c).getModule();
	}

	private final
	String name;

	private
	ContextLoader contextLoader;

	public
	ModuleContext(String name, File moduleDirectory)
	{
		this.name = name;
		if (!moduleDirectory.isDirectory())
		{
			throw new IllegalArgumentException(moduleDirectory + ": is not a directory");
		}
		this.moduleDirs.add(moduleDirectory);
		//@bug: providing partially-initialized class
		this.contextLoader = new ContextLoader(this);
	}

	public
	String getName()
	{
		return name;
	}

	private final
	List<File> moduleDirs = new ArrayList<File>();

	public
	void addModuleDirectory(File moduleDirectory, boolean priority)
	{
		if (!moduleDirectory.isDirectory())
		{
			throw new IllegalArgumentException(moduleDirectory + ": is not a directory");
		}
		if (priority)
		{
			moduleDirs.add(0, moduleDirectory);
		}
		else
		{
			moduleDirs.add(moduleDirectory);
		}
	}

	public
	void addModule(ModuleKey moduleKey) throws IOException
	{
		//NB: adding module to context is not lazy, loading module deps is
		try
		{
			if (moduleKey.equals(VersionString.JAVAX_MODULE))
			{
				//log.debug...
			}
			else
			{
				contextLoader.addModuleLoader(getModuleLoaderFor(moduleKey));
			}
		}
		catch (ModuleNotFoundException e)
		{
			throw new ModuleLoadException(moduleKey, e);
		}
		catch (ModuleAccessDeniedException e)
		{
			throw new ModuleLoadException(moduleKey, e);
		}
	}

	private HashMap<String, ModuleContext> subContexts = new HashMap();

	public
	ModuleContext createSubContext(String name, File subModules)
	{
		if (subContexts.containsKey(name))
		{
			throw new UnsupportedOperationException("already have a subcontext named: " + name);
		}
		ModuleContext sub = new ModuleContext(name, subModules);
		sub.parentModuleContext = this;
		subContexts.put(name, sub);
		return sub;
	}

	public
	ModuleContext removeSubContext(String name, boolean detach)
	{
		ModuleContext c = subContexts.remove(name);
		if (c == null)
		{
			throw new IllegalArgumentException("no subcontext named: " + name);
		}
		c.parentModuleContext = null;
		if (!detach)
		{
			c.contextLoader.removeAllModules();
			c.contextLoader = null;
		}
		if (allowList != null)
		{
			allowList.remove(c);
		}
		if (denyList != null)
		{
			denyList.remove(c);
		}
		return c;
	}

	public
	ModuleContext getSubContext(String name)
	{
		return subContexts.get(name);
	}

	private Exception         denyAll;
	private SubContextTracker allowList;
	private SubContextTracker denyList;

	public
	void allowAll()
	{
		denyAll = null;
		allowList = null;
		denyList = null;
	}

	public
	void denyAll()
	{
		denyAll = new Exception("denyAll() invoked");
		allowList = null;
		denyList = null;
	}

	public
	void allowAll(String subContext)
	{
		if (allowList == null)
		{
			allowList = new SubContextTracker("allowList");
		}
		ModuleContext sub = subContexts.get(subContext);
		allowList.add(sub);
		if (denyList != null)
		{
			denyList.remove(sub);
		}
	}

	public
	void allowAll(ModuleKey moduleKey)
	{
		if (allowList == null)
		{
			allowList = new SubContextTracker("allowList");
		}
		allowList.add(moduleKey);
		if (denyList != null)
		{
			denyList.remove(moduleKey);
		}
	}

	public
	void allow(String subContext, ModuleKey moduleKey)
	{
		if (allowList == null)
		{
			allowList = new SubContextTracker("allowList");
		}
		ModuleContext sub = subContexts.get(subContext);
		allowList.add(sub, moduleKey);
		if (denyList != null)
		{
			denyList.remove(sub, moduleKey);
		}
	}

	public
	void denyAll(String subContext)
	{
		if (denyList == null)
		{
			denyList = new SubContextTracker("denyList");
		}
		ModuleContext sub = subContexts.get(subContext);
		denyList.add(sub);
		if (allowList != null)
		{
			allowList.remove(sub);
		}
	}

	public
	void denyAll(ModuleKey moduleKey)
	{
		if (denyList == null)
		{
			denyList = new SubContextTracker("denyList");
		}
		denyList.add(moduleKey);
		if (allowList != null)
		{
			allowList.remove(moduleKey);
		}
	}

	public
	void deny(String subContext, ModuleKey moduleKey)
	{
		if (denyList == null)
		{
			denyList = new SubContextTracker("denyList");
		}
		ModuleContext sub = subContexts.get(subContext);
		denyList.add(sub, moduleKey);
		if (allowList != null)
		{
			allowList.remove(sub, moduleKey);
		}
	}

	private
	void checkAccess(ModuleKey key, ModuleContext subModuleContext) throws ModuleAccessDeniedException
	{
		Exception sub = null;
		if (denyList != null && (sub = denyList.matches(subModuleContext, key)) != null)
		{
			throw new ModuleAccessDeniedException(subModuleContext.name + " denied access to module: " + key, sub);
		}
		if (allowList != null && allowList.matches(subModuleContext, key) != null)
		{
			return;
		}
		if (denyAll != null)
		{
			throw new ModuleAccessDeniedException("access denied to class: " + key, denyAll);
		}
	}

	private static
	ModuleKey getModuleKey(Class c)
	{
		return ModuleLoader.forClass(c).getModuleKey();
	}

	public
	String toString()
	{
		return name;
	}

	public
	ClassLoader getClassLoader()
	{
		return contextLoader;
	}

	ContextLoader getContextLoader()
	{
		return contextLoader;
	}

	private ModuleContext parentModuleContext;

	private
	ModuleContext getParentContext()
	{
		return parentModuleContext;
	}

	Class findClassTopFirst(String name, ModuleKey requestor) throws IOException, ModuleAccessDeniedException
	{
		ModuleAccessDeniedException denied = null;
		Class retval = null;

		if (parentModuleContext != null)
		{
			try
			{
				retval = parentModuleContext.findClassFromSubContext(name, requestor, this);
			}
			catch (ModuleAccessDeniedException e)
			{
				denied = e;
			}
		}

		if (retval == null)
		{
			retval = contextLoader.findClassInThisContext(name, requestor, this);
		}

		if (retval == null && denied != null)
		{
			throw denied;
		}

		return retval;
	}

	private
	Class findClassFromSubContext(
									 String name,
									 ModuleKey requestor,
									 ModuleContext subModuleContext
	) throws IOException, ModuleAccessDeniedException
	{
		Class retval = null;
		if (parentModuleContext != null)
		{
			retval = parentModuleContext.findClassFromSubContext(name, requestor, this);
		}
		if (retval == null)
		{
			retval = contextLoader.findClassInThisContext(name, requestor, subModuleContext);
		}
		else
		{
			checkAccess(getModuleKey(retval), subModuleContext);
		}
		return retval;
	}

	private
	ModuleLoader getModuleLoaderFromSubContext(
												  ModuleKey moduleKey,
												  ModuleContext subModuleContext
	) throws IOException, ModuleAccessDeniedException, ModuleNotFoundException
	{
		ModuleAccessDeniedException denied = null;
		ModuleLoader retval = null;
		if (parentModuleContext != null)
		{
			try
			{
				retval = parentModuleContext.getModuleLoaderFromSubContext(moduleKey, this);
			}
			catch (ModuleAccessDeniedException e)
			{
				denied = e;
			}
		}
		if (retval == null)
		{
			retval = loadedModules.get(moduleKey);
		}
		if (retval == null)
		{
			try
			{
				retval = makeLoader(moduleKey);
			}
			catch (ModuleNotFoundException e)
			{
				if (denied != null)
				{
					throw denied;
				}
				throw e;
			}
		}
		else
		{
			checkAccess(retval.getModuleKey(), subModuleContext);
		}
		return retval;
	}

	ModuleLoader getModuleLoaderFor(ModuleKey moduleKey) throws IOException, ModuleNotFoundException, ModuleAccessDeniedException
	{
		ModuleLoader retval = loadedModules.get(moduleKey);
		ModuleAccessDeniedException denied = null;

		if (retval == null && parentModuleContext != null)
		{
			try
			{
				retval = parentModuleContext.getModuleLoaderFromSubContext(moduleKey, this);
			}
			catch (ModuleAccessDeniedException e)
			{
				denied = e;
			}
		}

		if (retval == null)
		{
			try
			{
				retval = makeLoader(moduleKey);
			}
			catch (ModuleNotFoundException e)
			{
				if (denied != null)
				{
					throw denied;
				}
				throw e;
			}
		}

		/*
		if (retval != null)
		{
			checkBugfix(retval.getModule(), moduleKey);
		}
		*/

		return retval;
	}

	public
	Properties getModulePropertiesFor(ModuleKey moduleKey) throws IOException
	{
		try
		{
			ModuleLoader ml = getModuleLoaderFor(moduleKey);
			return ml.getModule().getProperties();
		}
		catch (ModuleNotFoundException e)
		{
			throw new ModuleLoadException(moduleKey, e);
		}
		catch (ModuleAccessDeniedException e)
		{
			throw new ModuleLoadException(moduleKey, e);
		}
	}

	private synchronized
	ModuleLoader makeLoader(ModuleKey moduleKey) throws IOException, ModuleNotFoundException
	{
		synchronized (loadedModules)
		{
			ModuleLoader retval = loadedModules.get(moduleKey);

			if (retval==null)
			{
				Module module = locateModule(moduleKey);
				retval = new ModuleLoader(this, module);
				loadedModules.put(moduleKey, retval);
			}

			return retval;
		}
	}

	private
	Module locateModule(ModuleKey moduleKey) throws IOException, ModuleNotFoundException
	{
		ModuleNotFoundException notfound=null;

		for (File moduleDirectory : moduleDirs)
		{
				Module retval=new Module(moduleDirectory, moduleKey);
				if (retval.getJarFile().exists())
				{
					return retval;
				}
				else
				if (notfound==null)
				{
					notfound=new ModuleNotFoundException("dne: "+retval.getJarFile());
				}
		}

		if (notfound==null)
		{
			notfound=new ModuleNotFoundException("no directories to search in");
		}

		throw notfound;
	}

	/*
	 * TODO: this is just a show-stopper, and might be really slow or even wrong (verison comparison), maybe don't support it at all?
	 * @param m
	 * @param key
	 * @throws IOException
	 * /
	private
	void checkBugfix(Module m, ModuleKey key) throws IOException
	{
		if (key instanceof Dependency)
		{
			Dependency dep = (Dependency) key;
			String bugNeed = dep.getMinorVersion();
			if (bugNeed != null)
			{
				String bugHave = m.getMinorVersion();
				if (Version.compare(bugHave, bugNeed) < 0)
				{
					throw new UnsupportedOperationException(this + " module '" + key + "@" + key.getMinorVersion() + "' is too old. bugfix @ " + bugHave + ", but minimum required is " + bugNeed);
				}
			}
		}
	}
	*/

	private ConcurrentHashMap<ModuleKey, ModuleLoader> loadedModules = new ConcurrentHashMap();

	URL findResourceTopFirst(String name)
	{
		URL retval = null;
		if (parentModuleContext != null)
		{
			retval = parentModuleContext.findResourceTopFirst(name);
			//@todo: check access?
		}
		if (retval == null)
		{
			retval = contextLoader.findResourceInThisContext(name);
		}
		return retval;
	}

	/**
	 * This function is intended to provide a work around for mechanisms that adhere to the
	 * original java "flat class namespace" (aka classpath), by recursively loading every known
	 * module and checking to see if it is available. This can be very expensive, depending
	 * on the size of the dependency tree; but is roughly equivalent to checking the whole
	 * classpath anyway...
	 */
	Class findClassInEntireModuleDependencyTree(String name, Set<ModuleKey> shallowChecks)
	{
		if (parentModuleContext==null)
		{
			Set<ModuleKey> deepChecksComplete=new HashSet<ModuleKey>();
			return findClassInAnyModuleOrSubContext(name, shallowChecks, deepChecksComplete);
		}
		else
		{
			return parentModuleContext.findClassInEntireModuleDependencyTree(name, shallowChecks);
		}
	}

	private
	Class findClassInAnyModuleOrSubContext(String name, Set<ModuleKey> shallowChecks, Set<ModuleKey> deepChecksComplete)
	{
		/*
		By making this a *linked* hash set, this algorithim becomes (roughly) a breadth-first-search
		from the root module/context (unless we have sub-contexts). If we used a simple HashSet, the
		traversal order would be a mostly unpredictable.
		 */
		final
		Set<ModuleKey> deepChecksNeeded=new LinkedHashSet<ModuleKey>(loadedModules.size());

		for (Map.Entry<ModuleKey, ModuleLoader> me : loadedModules.entrySet())
		{
			final
			ModuleKey key=me.getKey();

			if (!deepChecksComplete.contains(key))
			{
				deepChecksNeeded.add(key);
			}
		}

		while(!deepChecksNeeded.isEmpty())
		{
			final
			ModuleKey moduleKey;
			{
				Iterator<ModuleKey> i=deepChecksNeeded.iterator();
				moduleKey=i.next();
				i.remove();
			}

			final
			ModuleLoader moduleLoader;
			{
				try
				{
					moduleLoader=getModuleLoaderFor(moduleKey);
				}
				catch (IOException e)
				{
					e.printStackTrace();
					continue;
				}
				catch (ModuleNotFoundException e)
				{
					e.printStackTrace();
					continue;
				}
				catch (ModuleAccessDeniedException e)
				{
					e.printStackTrace();
					continue;
				}
			}

			if (!shallowChecks.contains(moduleKey))
			{
				shallowChecks.add(moduleKey);

				try
				{
					Class retval=moduleLoader.findClassInThisModule(name);

					if (retval!=null)
					{
						return retval;
					}
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
			}

			deepChecksComplete.add(moduleKey);

			for (Dependency dependency : moduleLoader.getModule().getDependencies())
			{
				if (!deepChecksComplete.contains(dependency))
				{
					deepChecksNeeded.add(dependency);
				}
			}
		}

		for (Map.Entry<String, ModuleContext> me : subContexts.entrySet())
		{
			//String subContextName=me.getKey();
			ModuleContext subContextLoader=me.getValue();
			Class retval=subContextLoader.findClassInAnyModuleOrSubContext(name, shallowChecks, deepChecksComplete);

			if (retval!=null)
			{
				return retval;
			}
		}

		return null;
	}
}
