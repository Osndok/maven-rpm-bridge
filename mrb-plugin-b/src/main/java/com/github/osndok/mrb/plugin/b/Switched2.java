package com.github.osndok.mrb.plugin.b;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * Much like 'Switched', but without *any* annotations, and *requires* a filename.
 * Created by robert on 4/2/15.
 */
public
class Switched2 implements Callable<File>
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

	public
	void explicit(String s)
	{
		log.info("explicit: {}", s);
	}

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

	public static
	void eee()
	{
		log.debug("e (static)");
	}

	private final
	File file;

	public
	Switched2(File file)
	{
		this.file=file;
		log.info("CONSTRUCTED: %s", file);
	}

	@Override
	public
	File call() throws Exception
	{
		log.info("call()");
		return file;
	}
}
