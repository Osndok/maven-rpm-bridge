package com.github.osndok.mrb.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;

/**
 * Created by robert on 4/22/15.
 */
public
class JavaSystemClasses extends HashSet<String>
{
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
		if (INSTANCE==null)
		{
			try
			{
				INSTANCE = readSystemClasses();
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

	private static
	JavaSystemClasses readSystemClasses() throws IOException
	{
		final
		JavaSystemClasses retval=new JavaSystemClasses();

		final
		String javaHome=System.getProperty("java.home");

		final
		File file=new File(javaHome, "lib/classlist");

		if (!file.canRead())
		{
			throw new IllegalStateException("unreadable: "+file);
		}

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
	 */
	private static
	String unsuffixedEntryNameToPackageName(String name)
	{
		return name.replaceAll("\\/", ".");
	}
}
