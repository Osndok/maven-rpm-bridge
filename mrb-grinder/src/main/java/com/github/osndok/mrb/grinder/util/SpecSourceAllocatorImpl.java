package com.github.osndok.mrb.grinder.util;

import com.github.osndok.mrb.grinder.api.SpecShard;
import com.github.osndok.mrb.grinder.api.SpecSourceAllocator;

import javax.module.util.ModuleKey;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by robert on 12/5/14.
 */
public
class SpecSourceAllocatorImpl implements SpecSourceAllocator
{
	private
	int counter = 100;

	private
	Map<Integer, File> filesBySourceNumber = new HashMap<Integer, File>();

	@Override
	public
	String getJarFile()
	{
		return "%{SOURCE0}";
	}

	@Override
	public
	String getUntouchedWarFile()
	{
		return "%{SOURCE5}";
	}

	public
	void addWarFileEntry(File file)
	{
		filesBySourceNumber.put(5, file);
	}

	@Override
	public
	String allocateFile(File file)
	{
		final
		int i = (counter++);

		filesBySourceNumber.put(i, file);

		return String.format("%%{source%d}", i);
	}

	@Override
	public
	String getActualModularJarFile(ModuleKey moduleKey)
	{
		return "/usr/share/java/"+moduleKey+"/"+moduleKey.getModuleName()+".jar";
	}

	public
	boolean hasAnyEntries()
	{
		return !filesBySourceNumber.isEmpty();
	}

	public
	SpecShard asSpecShard()
	{
		final
		List<String> sourceLines=new ArrayList<String>(filesBySourceNumber.size());

		for (Map.Entry<Integer, File> me : filesBySourceNumber.entrySet())
		{
			int i=me.getKey();
			File file=me.getValue();

			sourceLines.add(String.format("Source%d: %s", i, file.getName()));
		}

		return new SpecShard()
		{
			@Override
			public
			String getSubPackageName()
			{
				return null;
			}

			@Override
			public
			String getSubPackageDescription()
			{
				return null;
			}

			@Override
			public
			Collection<String> getRpmRequiresLines()
			{
				return sourceLines;
			}

			@Override
			public
			Collection<String> getRpmBuildRequiresLines()
			{
				return null;
			}

			@Override
			public
			Collection<String> getFilePathsToPackage()
			{
				return null;
			}

			@Override
			public
			Map<String, String> getFileContentsByPath()
			{
				return null;
			}

			@Override
			public
			Map<String, String> getScriptletBodiesByType()
			{
				return null;
			}
		};
	}
}
