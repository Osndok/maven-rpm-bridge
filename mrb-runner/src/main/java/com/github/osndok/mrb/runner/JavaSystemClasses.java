package com.github.osndok.mrb.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created by robert on 4/22/15.
 */
public
class JavaSystemClasses extends HashSet<String>
{
	private static final
	boolean INCLUDE_SUN_PACKAGES = false;

	private
	JavaSystemClasses()
	{
		//singleton
	}

	private static
	JavaSystemClasses INSTANCE;

	public static
	JavaSystemClasses getInstance()
	{
		if (INSTANCE == null)
		{
			try
			{
				//INSTANCE = readSystemClassesFromClasslistFile();
				INSTANCE = readSystemClassesFromRuntimeJar();
			}
			catch (IOException e)
			{
				throw new RuntimeException(e);
			}
		}

		return INSTANCE;
	}

	private static final
	Logger log = LoggerFactory.getLogger(JavaSystemClasses.class);

	/*
	 * Wouldn't it be nice, straight forward, and more easily debuggable...
	 * too bad it's missing THREE QUARTERS of the desired entries (such as... "Callable"!).
	 *
	 * @return
	 * @throws IOException
	 * /
	private static
	JavaSystemClasses readSystemClassesFromClasslistFile() throws IOException
	{
		final
		String javaHome=System.getProperty("java.home");

		final
		File file=new File(javaHome, "lib/classlist");

		if (!file.canRead())
		{
			throw new IllegalStateException("unreadable: "+file);
		}

		final
		JavaSystemClasses retval=new JavaSystemClasses();

		final
		BufferedReader br=new BufferedReader(new FileReader(file));

		try
		{
			int count=0;

			String line;

			while ((line=br.readLine())!=null)
			{
				if (line.length()==0 || line.charAt(0)=='#')
				{
					continue;
				}

				final
				String name=unsuffixedEntryNameToPackageName(line);

				count++;

				if (count<5)
				{
					log.debug("system class: '{}'", name);
				}

				retval.add(name);
			}

			if (count < 1000)
			{
				log.warn("read only {} system class names", count);
			}
			else
			{
				log.debug("read {} system class names", count);
			}
		}
		finally
		{
			br.close();
		}

		return retval;
	}

	/**
	 * @param name - "java/lang/Object"
	 * @return  "java.lang.Object"
	 * /
	private static
	String unsuffixedEntryNameToPackageName(String name)
	{
		return name.replaceAll("\\/", ".");
	}
	*/

	/**
	 * @param name - "java/lang/Object.class"
	 * @return  "java.lang.Object"
	 */
	private static
	String suffixedEntryNameToPackageName(String name)
	{
		return name.substring(0, name.length()-CLASS_SUFFIX.length()).replaceAll("\\/", ".");
	}

	private static final
	String CLASS_SUFFIX=".class";

	/**
	 * Not too much worse... only that we must now rely on the ZipFile class.
	 *
	 * @return
	 * @throws IOException
	 */
	private static
	JavaSystemClasses readSystemClassesFromRuntimeJar() throws IOException
	{
		final
		String javaHome = System.getProperty("java.home");

		final
		File file = new File(javaHome, "lib/rt.jar");

		if (!file.canRead())
		{
			throw new IllegalStateException("unreadable: " + file);
		}

		final
		JavaSystemClasses retval = new JavaSystemClasses();

		int count = 0;

		final
		ZipFile zipFile = new ZipFile(file);

		try
		{
			final
			Enumeration<? extends ZipEntry> entries = zipFile.entries();

			while (entries.hasMoreElements())
			{
				final
				ZipEntry zipEntry = entries.nextElement();

				final
				String entryName = zipEntry.getName();

				if (entryName.length() == 0 || entryName.charAt(0) == '#' || !entryName.endsWith(CLASS_SUFFIX))
				{
					continue;
				}

				if (!INCLUDE_SUN_PACKAGES && looksLikeSunPackage(entryName))
				{
					continue;
				}

				final
				String name = suffixedEntryNameToPackageName(entryName);

				count++;

				if (count < 5)
				{
					log.debug("system class: '{}'", name);
				}

				retval.add(name);
			}
		}
		finally
		{
			zipFile.close();
		}

		if (count < 1000)
		{
			log.warn("read only {} system class names", count);
		}
		else
		{
			log.debug("read {} system class names", count);
		}

		return retval;
	}

	private static
	boolean looksLikeSunPackage(String entryName)
	{
		return entryName.startsWith("com/sun/")
			|| entryName.startsWith("sun/");
	}
}
