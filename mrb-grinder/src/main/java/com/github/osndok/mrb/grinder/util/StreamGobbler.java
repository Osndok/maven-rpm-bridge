package com.github.osndok.mrb.grinder.util;

import java.io.*;


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
}