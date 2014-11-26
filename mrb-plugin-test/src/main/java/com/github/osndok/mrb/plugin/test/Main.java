package com.github.osndok.mrb.plugin.test;

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
