package javax.module;

import java.io.*;
import java.lang.reflect.Constructor;
import java.util.*;

/**
 * Created by robert on 11/9/14.
 */
public
class Plugins
{
	private static final
	String DOT_PLUGIN=".plugin";

	private static final
	FilenameFilter endingInDotPlugin = new FilenameFilter()
	{
		@Override
		public
		boolean accept(File file, String s)
		{
			return s.endsWith(DOT_PLUGIN);
		}
	};

	/**
	 * Limitations: cannot use primitives (or null-values) in the constructor args.
	 *
	 * @param anInterface
	 * @param constructorArgs
	 * @param <T>
	 * @return
	 */
	public static <T>
	Collection<T> load(Class<T> anInterface, Object... constructorArgs)
	{
		final
		int l=constructorArgs.length;

		final
		Class[] constructorArgClasses=new Class[l];

		for (int i=0; i<l; i++)
		{
			constructorArgClasses[i]=constructorArgs[i].getClass();
		}

		final
		Collection<T> retval=new ArrayList<T>(l);

		for (Class<T> tClass : classes(anInterface))
		{
			try
			{
				final
				Constructor<T> constructor = tClass.getDeclaredConstructor(constructorArgClasses);

				final
				T t=constructor.newInstance(constructorArgs);

				retval.add(t);
			}
			catch (Throwable t)
			{
				System.err.println("cannot load plugin: "+tClass);
				t.printStackTrace();
			}
		}

		return retval;
	}

	public static <T>
	Set<Class<T>> classes(Class<T> anInterface)
	{
		final
		ModuleLoader moduleLoader = ModuleLoader.forClass(anInterface);

		final
		ModuleContext context = moduleLoader.getContext();

		final
		File pluginDir = moduleLoader.getModule().getPluginDirectory();

		if (!pluginDir.isDirectory())
		{
			System.err.println(pluginDir+": is not a directory");
			return Collections.emptySet();
		}

		if (pluginDir.canRead())
		{
			System.err.println(pluginDir+": is not readable");
			return Collections.emptySet();
		}

		final
		Set<Class<T>> retval=new HashSet<Class<T>>();

		for (File pluginFile : notNull(pluginDir.listFiles(endingInDotPlugin)))
		{
			try
			{
				final
				String moduleReference = removeDotPlugin(pluginFile.getName());

				final
				ModuleKey moduleKey = ModuleKey.parseModuleKey(moduleReference);

				final
				ModuleLoader pluginModuleLoader = context.getModuleLoaderFor(moduleKey);

				for (String className : classNamesFromPluginFile(anInterface, pluginFile))
				{
					try
					{
						retval.add(pluginModuleLoader.findClassInThisModule(className));
					}
					catch (Throwable t)
					{
						System.err.println(className+": could not be loaded");
						t.printStackTrace();
					}
				}
			}
			catch (Throwable t)
			{
				System.err.println(pluginFile+": could not be processed");
				t.printStackTrace();
			}
		}

		return retval;
	}

	private static <T>
	Set<String> classNamesFromPluginFile(Class<T> anInterface, File pluginFile) throws IOException
	{
		final
		String interfaceName=anInterface.getName();

		final
		BufferedReader br=new BufferedReader(new FileReader(pluginFile));

		try
		{
			final
			Set<String> retval=new HashSet<String>();

			String line;

			while ((line=br.readLine())!=null)
			{
				if (line.startsWith(interfaceName))
				{
					String className=line.substring(interfaceName.length()+1);
					retval.add(className);
				}
			}

			return retval;
		}
		finally
		{
			br.close();
		}
	}

	private static
	String removeDotPlugin(String fileName)
	{
		return fileName.substring(0, fileName.length()-DOT_PLUGIN.length());
	}

	private static
	File[] notNull(File[] files)
	{
		if (files==null)
		{
			return new File[0];
		}
		else
		{
			return files;
		}
	}

	private
	Plugins()
	{
		//tbd.
	}
}
