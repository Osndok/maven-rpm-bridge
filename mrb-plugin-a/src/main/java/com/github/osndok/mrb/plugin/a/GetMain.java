package com.github.osndok.mrb.plugin.a;

import com.github.osndok.mrb.plugin.test.Main;
import com.github.osndok.mrb.plugin.test.NeedsMainArgument;

import javax.module.Plugin;

/**
 * Created by robert on 11/10/14.
 */
@Plugin
public
class GetMain implements NeedsMainArgument
{
	private final
	Main main;

	public
	GetMain(Main main)
	{
		this.main = main;
	}

	@Override
	public
	void run()
	{
		System.err.println("Got: " + main);
	}
}
