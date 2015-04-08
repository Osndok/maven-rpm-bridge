package javax.module;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * A simple mechanism to list and read @ReactorEntry properties from a given directory.
 */
public
class ReactorClients
{
	public static final
	String FILE_SUFFIX=".props";

	private final
	File directory;

	public
	ReactorClients(File directory)
	{
		this.directory = directory;
	}

	public
	ReactorClients(String directory)
	{
		this.directory = new File(directory);
	}

	public
	List<String> names()
	{
		final
		List<String> retval=new ArrayList<>();

		for (String name : notNull(directory.list()))
		{
			if (name.endsWith(FILE_SUFFIX))
			{
				retval.add(stripSuffix(name));
			}
		}

		return retval;
	}

	private
	String[] notNull(String[] list)
	{
		if (list==null)
		{
			return new String[0];
		}
		else
		{
			return list;
		}
	}

	private
	String stripSuffix(String s)
	{
		return s.substring(0, s.length()-FILE_SUFFIX.length());
	}

	public
	Properties get(String clientName) throws IOException
	{
		final
		File file=new File(directory, clientName+FILE_SUFFIX);

		if (!file.exists())
		{
			return null;
		}

		final
		Properties properties=new Properties();

		final
		FileInputStream fileInputStream=new FileInputStream(file);

		try
		{
			properties.load(fileInputStream);
		}
		finally
		{
			fileInputStream.close();
		}

		return properties;
	}
}
