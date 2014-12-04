package javax.module;

import javax.print.DocFlavor;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Created by robert on 10/29/14.
 */
public
class Module
{
	private final
	File directory;

	private final
	File jarFile;

	private final
	File depsFile;

	private final
	File propsFile;

	private
	ModuleKey moduleKey;

	public
	Module(File modulesDirectory, ModuleKey moduleKey)
	{
		this.moduleKey = moduleKey;

		this.directory = new File(modulesDirectory, moduleKey.toString());

		final
		String moduleName=moduleKey.getModuleName();

		this.jarFile   = new File(directory, moduleName+".jar"  );
		this.depsFile  = new File(directory, moduleName+".deps" );
		this.propsFile = new File(directory, moduleName+".props");
	}

	private
	ModuleInfo moduleInfo;

	public
	File getDependenciesFile()
	{
		return depsFile;
	}

	public
	ModuleInfo getModuleInfo()
	{
		if (moduleInfo==null)
		{
			if (depsFile.exists())
			{
				try
				{
					InputStream in=new FileInputStream(depsFile);
					try
					{
						moduleInfo = ModuleInfo.read(in, moduleKey);
						moduleKey = moduleInfo.getModuleKey();
					}
					finally
					{
						in.close();
					}
				}
				catch (IOException e)
				{
					//File is present, but not readable?
					//Catch problems early, rather than obscure ClassNotFound exceptions
					throw new RuntimeException("unable to read: "+depsFile, e);
				}
			}
			else
			{
				//I would log a warning here, if we had a logger.
				System.err.println("WARNING: does not exist: "+depsFile);

				//Even for a module with no deps, we want to get the actual minor version number. :(
				//More likely, the file was erased, and we will never see it again.
				moduleInfo=ModuleInfo.blank(moduleKey);
			}
		}
		return moduleInfo;
	}

	public
	ModuleKey getModuleKey()
	{
		return getModuleInfo().getModuleKey();
	}

	public
	String getModuleName()
	{
		return getModuleInfo().getModuleKey().getModuleName();
	}

	public
	String getMajorVersion()
	{
		return getModuleInfo().getModuleKey().getMajorVersion();
	}

	public
	String getMinorVersion()
	{
		return getModuleInfo().getModuleKey().getMinorVersion();
	}

	public
	File getDirectory()
	{
		return directory;
	}

	public
	File getJarFile()
	{
		return jarFile;
	}

	private
	JarFile jar;

	public
	JarFile getJar() throws IOException
	{
		if (jar==null)
		{
			jar=new JarFile(jarFile);
		}
		return jar;
	}

	public
	File getPropsFile()
	{
		return propsFile;
	}

	/**
	 * Intended for loading raw class data, this locates a resource with the
	 * given name without consulting "ad-hoc"/loose files.
	 */
	InputStream getClassAsStream(String name) throws IOException
	{
		JarFile jar=getJar();
		{
			JarEntry e = jar.getJarEntry(name);

			if (e != null)
			{
				return jar.getInputStream(e);
			}
		}

		return null;
	}

	/**
	 * Locates a resource (file, image, sound) within this module, giving preference to
	 * resources located in the module directory (as opposed to within a jar file).
	 *
	 * Resources within the module directory are given priority so that engineers can
	 * override those built-into the jars without performing "jar-surgery" (and nullifing
	 * the signatures thereof). The full path must exist, which often requires subdirectories.
	 *
	 * e.g. one can configure log4j for the entire system by placing the config file in
	 *      log4j's module directory, yet it will be overridden if a context-level module
	 *      also has a log4j config file (or really, any parent context will be preferred).
	 */
	URL getResourceAsURL(String name)
	{
		try
		{
			File f=new File(directory, name);
			if (f.exists())
			{
				return f.toURL();
			}

			JarFile jar=getJar();
			JarEntry e=jar.getJarEntry(name);

			if (e!=null)
			{
				return new URL("jar:file:"+jar.getName()+"!/"+name);
			}

			return null;
		}
		catch (MalformedURLException e)
		{
			throw new RuntimeException("bad url: "+name, e);
		}
		catch (IOException e)
		{
			throw new RuntimeException("error reading jar file: "+jarFile, e);
		}
	}

	private Properties properties;

	//TODO: this could probably use some attention.
	public
	Properties getProperties()
	{
		if (properties == null)
		{
			properties = new Properties();
			InputStream closeMe = null;
			try
			{
				/*
				if (singleFileMode)
				{
					JarFile jar=getJar();
					{
						JarEntry e = jar.getJarEntry("META-INF/props");
						if (e != null)
						{
							properties.load(closeMe = jar.getInputStream(e));
						}
					}
				}
				else
				*/
				{
					File props = propsFile;
					if (props.exists())
					{
						properties.load(closeMe = new FileInputStream(props));
					}
				}
				if (closeMe != null)
				{
					closeMe.close();
				}
			}
			catch (IOException io)
			{
				if (closeMe != null)
				{
					try
					{
						closeMe.close();
					}
					catch (IOException e)
					{
					}
				}

				properties = null;
				throw new RuntimeException("cannot load property file", io);
			}
		}
		return properties;
	}

	public
	Set<Dependency> getDependencies()
	{
		return getModuleInfo().getDependencies();
	}

	public
	File getPluginDirectory()
	{
		return new File(directory, "plugins.d");
	}
}
