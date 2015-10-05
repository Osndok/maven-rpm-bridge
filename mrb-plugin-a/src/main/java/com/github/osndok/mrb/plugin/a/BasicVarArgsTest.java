package com.github.osndok.mrb.plugin.a;

import java.io.File;
import java.io.PrintStream;

/**
 * Created by robert on 2015-10-05 14:54.
 */
public
class BasicVarArgsTest implements Runnable
{
	public
	BasicVarArgsTest(String... args)
	{
		final
		PrintStream e = System.err;

		e.print("BasicVarArgsTest(");

		for (String arg : args)
		{
			e.print(arg);
			e.print(", ");
		}

		e.println("...)"); // Otherwise the extra/trailing comma might lead someone to think there is an issue
	}

	public static
	void staticMethodName(File... args)
	{
		final
		PrintStream e = System.err;

		e.print(" -> staticMethodName(");

		for (File arg : args)
		{
			e.print(arg);
			e.print(", ");
		}

		e.println("...)"); // Otherwise the extra/trailing comma might lead someone to think there is an issue
	}

	public
	void nonStaticMethod(String... args)
	{
		final
		PrintStream e = System.err;

		e.print(" -> nonStaticMethod(");

		for (String arg : args)
		{
			e.print(arg);
			e.print(", ");
		}

		e.println("...)"); // Otherwise the extra/trailing comma might lead someone to think there is an issue
	}

	@Override
	public
	void run()
	{
		System.out.println("run()");
	}
}
