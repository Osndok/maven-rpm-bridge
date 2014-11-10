package com.github.osndok.mrb.plugin.a;

import com.github.osndok.mrb.plugin.test.Quacker;

import javax.module.Plugin;

/**
 * Created by robert on 11/10/14.
 */
@Plugin
public
class QuackA implements Quacker
{
	@Override
	public
	void run()
	{
		System.err.println("Quack! From module 'A'...");
	}
}
