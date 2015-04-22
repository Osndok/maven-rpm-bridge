package com.github.osndok.mrb.runner;

/**
 * Created by robert on 4/22/15.
 */
public
class UnsupportedJavaGrammar extends UnsupportedOperationException
{
	public
	UnsupportedJavaGrammar()
	{
	}

	public
	UnsupportedJavaGrammar(String message)
	{
		super(message);
	}

	public
	UnsupportedJavaGrammar(String message, Throwable cause)
	{
		super(message, cause);
	}

	public
	UnsupportedJavaGrammar(Throwable cause)
	{
		super(cause);
	}
}
