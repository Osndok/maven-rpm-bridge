package com.github.osndok.mrb.grinder.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;

/**
 * Created by robert on 10/31/14.
 */
public
class Exec
{
	//TODO: convert this to a slf4j logger... unless it is destined for the javax-module package
	private static final
	PrintStream log=System.err;

	private static final
	Runtime runtime=Runtime.getRuntime();

	public static
	String toString(String... args) throws IOException
	{
		log.print("\n----------------------------\nexec:");

		for (String arg : args)
		{
			log.print(' ');
			log.print(arg);
		}

		log.println();
		log.flush();

		final long startTime = System.currentTimeMillis();

		final Process exec = runtime.exec(args);

		final Thread gobbler;
		{
			gobbler=new StreamGobbler(exec.getErrorStream(), System.err, "ERR> ");
			gobbler.start();
		}

		BufferedReader br = new BufferedReader(new InputStreamReader(exec.getInputStream()));
		StringBuilder builder = new StringBuilder();

		int i;

		while ( (i = br.read()) >= 0)
		{
			builder.append((char)i);
		}

		final int exitStatus;

		try
		{
			exitStatus=exec.waitFor();
			gobbler.join();
		}
		catch (InterruptedException e)
		{
			throw new IOException(e);
		}

		final long duration = System.currentTimeMillis()-startTime;

		if (exitStatus!=0)
		{
			log.println("exec: took "+duration+" milliseconds, exit status: "+exitStatus);
			throw new IOException(args[0]+" execution returned exit status "+exitStatus);
		}
		else
		{
			log.println("exec: took "+duration+" milliseconds");
		}

		String retval=builder.toString();

		System.err.println("stdout(retval) => "+retval);

		return retval;
	}

	public static
	void andWait(String... args) throws IOException
	{
		log.print("\n----------------------------\nexec:");

		for (String arg : args)
		{
			log.print(' ');
			log.print(arg);
		}

		log.println();
		log.flush();

		final long startTime = System.currentTimeMillis();

		final Process exec = runtime.exec(args);

		final Thread outputGobbler;
		{
			outputGobbler = new StreamGobbler(exec.getInputStream(), System.err, "out| ");
			outputGobbler.start();
		}

		final Thread errorGobbler;
		{
			errorGobbler=new StreamGobbler(exec.getErrorStream(), System.err, "ERR> ");
			errorGobbler.start();
		}

		final int exitStatus;

		try
		{
			exitStatus=exec.waitFor();
			outputGobbler.join();
			errorGobbler.join();
		}
		catch (InterruptedException e)
		{
			throw new IOException(e);
		}

		final long duration = System.currentTimeMillis()-startTime;

		if (exitStatus!=0)
		{
			log.println("exec: took "+duration+" milliseconds, exit status: "+exitStatus);
			throw new IOException(args[0]+" execution returned exit status "+exitStatus);
		}
		else
		{
			log.println("exec: took "+duration+" milliseconds");
		}
	}
}
