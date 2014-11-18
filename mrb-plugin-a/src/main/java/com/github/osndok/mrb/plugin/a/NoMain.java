package com.github.osndok.mrb.plugin.a;

import com.github.osndok.mrb.plugin.test.NeedsMainArgument;

import javax.module.Plugin;

/**
 * Created by robert on 11/10/14.
 */
@Plugin
public
class NoMain implements NeedsMainArgument
{
	@Override
	public
	void run()
	{
		System.err.println("NoMain here!.. did not get (or need) main class!");
	}
}
