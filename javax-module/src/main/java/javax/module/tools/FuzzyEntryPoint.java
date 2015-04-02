package javax.module.tools;

/**
 * Used when a java class is being called into, but does not provide an explicit main() method
 * that would normally
 * Created by robert on 4/2/15.
 */
public
class FuzzyEntryPoint
{
	final
	Class aClass;

	public
	FuzzyEntryPoint(Class aClass)
	{
		this.aClass=aClass;
	}

	public
	void execute(String[] argumentsAndCommandLineOptions)
	{

	}
}
