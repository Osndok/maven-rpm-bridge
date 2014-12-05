package com.github.osndok.mrb.grinder.api;

import java.io.File;

/**
 * Created by robert on 12/5/14.
 */
public
interface SpecSourceAllocator
{
	String getJarFile();
	String getWarFile();

	/**
	 * @param file
	 * @return the full rpm macro that can be used to summon the file in the spec (e.g. "%{source12}")
	 */
	String allocateFile(File file);
}
