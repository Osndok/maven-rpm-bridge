package javax.module;

import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by robert on 10/30/14.
 */
final
class ContextLoader extends ClassLoader
{

	private final
	ModuleContext context;

	private final
	List<ModuleLoader> moduleLoaders;

	public
	ContextLoader(ModuleContext context)
	{
		this.context = context;
		this.moduleLoaders = new ArrayList();
	}

	public
	void addModuleLoader(ModuleLoader moduleLoader)
	{
		if (moduleLoaders.add(moduleLoader))
		{
			moduleLoader.incRefCount();
		}
	}

	public
	void removeModuleLoader(ModuleLoader moduleLoader)
	{
		if (moduleLoaders.remove(moduleLoaders))
		{
			moduleLoader.decRefCount();
		}
	}

	public
	void removeAllModules()
	{
		for (ModuleLoader m : moduleLoaders)
		{
			m.decRefCount();
		}
		moduleLoaders.clear();
	}

	protected
	Class findClassInThisContext(
									String name,
									ModuleKey requestor,
									ModuleContext requestContext
	) throws IOException, ModuleAccessDeniedException
	{
		//we are a context loader, query all our available module loaders
		if (DEBUG)
		{
			System.err.println(name + ": find-in-context '" + context + "' (from " + requestor + ")");
		}

		//direct-module addressing to solve the Class.forName() "runtime-module" delimma...
		{
			int colon = name.indexOf(':');
			if (colon > 0)
			{
				//TODO: factor out the string math, if ever we expect many nested contexts.
				String moduleName = name.substring(0, colon);
				String className = name.substring(colon + 1);

				if (DEBUG)
				{
					System.err.println(className + ":\tDIRECT [ " + context + " ] (request into " + moduleName + ")");
				}

				try
				{
					ModuleKey moduleKey = ModuleKey.parseModuleKey(moduleName);

					if (moduleKey.equals(Version.JAVAX_MODULE))
					{
						return null;
					}

					ModuleLoader adhocLoader = context.getModuleLoaderFor(moduleKey);
					Class retval = adhocLoader.findClassInThisModule(className);
					if (retval == null)
					{
						System.err.println("WARN: bad module-name in absolute class reference: " + name);
						if (DEBUG)
						{
							new RuntimeException("bad abosulte class reference: " + name).printStackTrace();
						}
						return null;
					}
					else
					{
						if (DEBUG)
						{
							System.err.println(className + ":\tFOUND " + retval);
						}
						if (requestor != null)
						{
							adhocLoader.checkAccess(retval, requestor, requestContext);
						}
						addModuleLoader(adhocLoader);
						return adhocLoader.getDirectClassProxy(name, retval);
					}
				}
				catch (ParseException e)
				{
					System.err.println("WARN: bad module-name in absolute class reference: " + name);
					if (DEBUG)
					{
						e.printStackTrace();
					}
				}
				catch (ModuleNotFoundException e)
				{
					if (DEBUG)
					{
						System.err.println(name + ": DNE in " + context);
					}
				}
				catch (IOException e)
				{
					if (DEBUG)
					{
						System.err.println("DEBUG: cannot load absolute class reference: " + name + " from context " + context);
						e.printStackTrace();
					}
				}
				return null;
			}
		}

		for (ModuleLoader m : moduleLoaders)
		{
			Class retval = m.findClassInThisModule(name);
			if (retval != null)
			{
				if (DEBUG)
				{
					System.err.println(name + ": loaded via " + m.getModuleKey() + " in " + context);
				}
				m.checkAccess(retval, requestor, requestContext);
				return retval;
			}
		}
		return null;
	}

	/**
	 * This find-class will primarily be used by containers and the Startup context.
	 */
	protected
	Class findClass(String name) throws ClassNotFoundException
	{
		Class retval;

		if (DEBUG)
		{
			System.err.println(name + ": context-loader findClass(): " + context);
		}

		ModuleKey requestingModule = null;

		{
			//@bug very hackish & does not appear to work
			for (int i = 2; i < 10; i++)
			{
				try
				{
					Class c = sun.reflect.Reflection.getCallerClass(i);
					ClassLoader cl = c.getClassLoader();
					if (cl instanceof ModuleLoader)
					{
						requestingModule = ((ModuleLoader) cl).getModuleKey();
						if (DEBUG)
						{
							System.err.println(name + ": found requesting module @ stack-trace level " + i);
						}
						break;
					}
				}
				catch (Throwable t)
				{
				}
			}
		}

		if (DEBUG)
		{
			System.err.println(name + ": requested by '" + requestingModule + "' in context '" + context + "'");
		}

		try
		{
			retval = context.findClassTopFirst(name, requestingModule);
		}
		catch (IOException e)
		{
			throw new ClassNotFoundException("io exception in class loading", e);
		}
		catch (ModuleAccessDeniedException e)
		{
			throw new ClassNotFoundException("cannot access module", e);
		}

		if (retval != null)
		{
			if (DEBUG)
			{
				System.err.println(name + ": top-first " + context);
			}
			return retval;
		}

		//NB: the above call includes a search of this context

		if (DEBUG)
		{
			System.err.println(name + ": no place left to look in " + context);
		}
		throw new ClassNotFoundException(name + " in context " + context);
	}

	private static final boolean DEBUG = Boolean.getBoolean("debug.module.loader");

	protected
	void finalize()
	{
		if (!moduleLoaders.isEmpty())
		{
			removeAllModules();
		}
	}

	protected
	URL findResource(String name)
	{
		return context.findResourceTopFirst(name);
	}

	URL findResourceInThisContext(String name)
	{
		URL retval = null;
		for (ModuleLoader m : moduleLoaders)
		{
			retval = m.findResourceInThisModule(name);
			if (retval != null)
			{
				if (DEBUG)
				{
					System.err.println(name + ": loaded via " + m.getModuleKey() + " in " + context);
				}
				return retval;
			}
		}
		return null;
	}
}