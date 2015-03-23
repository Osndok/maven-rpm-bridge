package com.github.osndok.mrb.grinder.util;

import com.github.osndok.mrb.grinder.api.SpecShard;
import com.github.osndok.mrb.grinder.api.SpecSourceAllocator;

import java.io.File;
import java.util.*;

/**
 * Created by robert on 12/5/14.
 */
public
class SpecSourceAllocatorImpl implements SpecSourceAllocator
{
	private
	int counter = 100;

	private
	Map<Integer, File> filesBySourceNumber = new HashMap<>();

	@Override
	public
	String getJarFile()
	{
		return "%{source0}";
	}

	@Override
	public
	String getUntouchedWarFile()
	{
		return "%{source5}";
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

	public
	boolean hasAnyEntries()
	{
		return !filesBySourceNumber.isEmpty();
	}

	public
	SpecShard asSpecShard()
	{
		final
		List<String> sourceLines=new ArrayList<>(filesBySourceNumber.size());

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
