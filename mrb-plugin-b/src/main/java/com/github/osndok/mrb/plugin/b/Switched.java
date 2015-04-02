package com.github.osndok.mrb.plugin.b;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.module.CommandLineOption;
import javax.module.CommandLineTool;
import java.io.File;
import java.util.concurrent.Callable;

/**
 * Created by robert on 4/2/15.
 */
@CommandLineTool(suffix = "-switched")
public
class Switched implements Callable<File>
{
	private static final Logger log = LoggerFactory.getLogger(Switched.class);

	public
	void setString(String s)
	{
		log.info("setString: {}", s);
	}

	public static
	void setStaticString(String s)
	{
		log.info("setStaticString: {}", s);
	}

	public
	void string2(String s)
	{
		log.info("string2: {}", s);
	}

	public static
	void staticString2(String s)
	{
		log.info("staticString2: {}", s);
	}

	@CommandLineOption(_short = 'x', _long = "--explicit")
	public
	void explicit(String s)
	{
		log.info("explicit: {}", s);
	}

	@CommandLineOption(_short = 'y', _long = "--static-explicit")
	public static
	void staticExplicit(String s)
	{
		log.info("staticExplicit: {}", s);
	}

	public
	void a()
	{
		log.debug("a");
	}

	@CommandLineOption(_short='b')
	public
	void bee()
	{
		log.debug("b");
	}

	public
	void c()
	{
		log.debug("c");
	}

	public static
	void d()
	{
		log.debug("d (static)");
	}

	@CommandLineOption(_short = 'e')
	public static
	void eee()
	{
		log.debug("e (static)");
	}

	public
	Switched()
	{
		log.info("CONSTRUCTED (no args)");
	}

	public
	Switched(String s)
	{
		log.info("CONSTRUCTED: %s", s);
	}

	@Override
	public
	File call() throws Exception
	{
		log.info("call()");
		return new File("/this/is-the/output-of-switched");
	}
}
