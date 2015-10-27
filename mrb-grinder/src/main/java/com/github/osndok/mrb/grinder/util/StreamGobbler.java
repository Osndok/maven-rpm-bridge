package com.github.osndok.mrb.grinder.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;


/**
 * Based largely on:
 * http://stackoverflow.com/questions/14165517/processbuilder-forwarding-stdout-and-stderr-of-started-processes-without-blocki
 */
public
class StreamGobbler extends Thread
{
	final InputStream inputStream;
	final PrintStream output;
	final String prefix;

	public
	StreamGobbler(InputStream inputStream, PrintStream output, String prefix)
	{
		this.inputStream = inputStream;
		this.output = output;
		this.prefix = prefix;
	}

	@Override
	public
	void run()
	{
		try
		{
			final InputStreamReader isr = new InputStreamReader(inputStream);
			final BufferedReader br = new BufferedReader(isr);

			boolean lastLineContainsSpaceReducer=false;
			String line;
			while ((line = br.readLine()) != null)
			{
				if (knownNuisanceMessage(line))
				{
					//Skip obnoxious messages.
				}
				else
				if (lastLineContainsSpaceReducer && line.trim().length()==0)
				{
					//suppress blank line, squeezing some output
				}
				else
				{
					output.println(prefix + line);
					lastLineContainsSpaceReducer = (line.trim().length()==0) || line.contains("Installing") || line.contains("Verifying");
				}
			}
		}
		catch (IOException ioe)
		{
			ioe.printStackTrace();
		}
	}

	private
	boolean knownNuisanceMessage(String line)
	{
		if (line.startsWith("[WARNING] "))
		{
			//Yeah... we get it, maven... you don't like it...
			return line.contains("contains an expression but should be a constant.")
				|| line.contains("they threaten the stability of your build.")
				|| line.contains("building such malformed projects.")
				;
		}
		else
		{
			return false;
		}
	}
}