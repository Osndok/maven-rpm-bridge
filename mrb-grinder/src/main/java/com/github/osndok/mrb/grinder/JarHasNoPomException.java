package com.github.osndok.mrb.grinder;

import java.io.File;

/**
 * Created by robert on 12/2/14.
 */
public
class JarHasNoPomException extends Throwable
{
	public
	JarHasNoPomException(File s)
	{
		super(s+" does not have a pom.xml entry");
	}
}
