package com.github.osndok.mrb.grinder;

import javax.module.ModuleKey;

/**
 * Created by robert on 10/30/14.
 */
public
class ObsoleteJarException extends Exception
{
	private final
	ModuleKey moduleKey;

	public
	ObsoleteJarException(String s, ModuleKey moduleKey)
	{
		super(s);

		this.moduleKey=moduleKey;
	}

	public
	ModuleKey getModuleKey()
	{
		return moduleKey;
	}
}
