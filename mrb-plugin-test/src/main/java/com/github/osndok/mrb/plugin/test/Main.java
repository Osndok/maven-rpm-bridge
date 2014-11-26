package com.github.osndok.mrb.plugin.test;

import org.slf4j.LoggerFactory;

import javax.module.Plugins;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

/**
 * Created by robert on 11/10/14.
 */
public
class Main
{
	public static
	void main(String[] args) throws IOException
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

		System.err.flush();

		/*
		This is a test of the startup context... slf4j-api expects to find an implementation, but
		DOES-NOT-DEPEND-ON-ONE*... instead, you are expected to select/provide one at runtime,
		which it detects because the class is directly findable via the classic/flat classpath
		mechanism. We support this by providing access to the startup-module's dependencies to
		all other modules loaded in that "context"... so the net effect is roughly the same,
		since we are running, we decide which slf4j implementation is selected (by virtual of it
		being one of our dependencies).
		*/
		LoggerFactory.getLogger(Main.class).error("LOGGING WORKS");

		//This parallels the way that tapestry-ioc scans for it's modules...
		//For a proper implementation, it must list all the manifest files for our immediate dependencies
		//***AND*** everything in our startup context.
		Enumeration<URL> urls = Main.class.getClassLoader().getResources("META-INF/MANIFEST.MF");

		while (urls.hasMoreElements())
		{
			URL url = urls.nextElement();
			System.out.println(url);
		}
	}
}
