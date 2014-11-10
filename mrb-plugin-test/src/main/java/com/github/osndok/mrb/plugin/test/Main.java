package com.github.osndok.mrb.plugin.test;

import javax.module.Plugins;

/**
 * Created by robert on 11/10/14.
 */
public
class Main
{
	public static
	void main(String[] args)
	{
		for (Quacker quacker : Plugins.load(Quacker.class))
		{
			System.err.println("Running: "+quacker);
			quacker.run();
		}

		final
		Main main=new Main();

		for (NeedsMainArgument plugin : Plugins.load(NeedsMainArgument.class, main))
		{
			System.err.println("Running: "+plugin);
			plugin.run();
		}
	}
}
