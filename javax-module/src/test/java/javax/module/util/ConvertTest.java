package javax.module.util;

import org.testng.annotations.Test;

import java.util.Observable;

import static org.testng.Assert.*;

/**
 * Created by robert on 2015-10-06 14:52.
 */
public
class ConvertTest
{
	@Test
	public
	void testStringToBooleanPrimitive() throws Exception
	{
		assertTrue(Convert.stringToBooleanPrimitive("t"));
		assertTrue(Convert.stringToBooleanPrimitive("T"));
		assertTrue(Convert.stringToBooleanPrimitive("true"));
		assertTrue(Convert.stringToBooleanPrimitive("TrUe"));
		assertTrue(Convert.stringToBooleanPrimitive("yes"));
		assertTrue(Convert.stringToBooleanPrimitive("1"));

		assertFalse(Convert.stringToBooleanPrimitive("f"));
		assertFalse(Convert.stringToBooleanPrimitive("F"));
		assertFalse(Convert.stringToBooleanPrimitive("false"));
		assertFalse(Convert.stringToBooleanPrimitive("FaLsE"));
		assertFalse(Convert.stringToBooleanPrimitive("no"));
		assertFalse(Convert.stringToBooleanPrimitive("0"));
	}

	@Test
	public
	void testStringToBooleanObject() throws Exception
	{

	}

	@Test
	public
	void testCharacterToBooleanPrimitive() throws Exception
	{

	}

	@Test
	public
	void testCharacterToBooleanObject() throws Exception
	{

	}

	@Test
	public
	void testStringToArray() throws Exception
	{

	}

	@Test(invocationCount = 10)
	public
	void testObjectToInterface() throws Exception
	{
		final
		Object o=new Object(){
			public
			void run()
			{
				System.err.println("Coercion works!");
			}
		};

		final
		Runnable runnable=Convert.objectToInterface(o, Runnable.class);
		{
			runnable.run();
		}

	}

	@Test(invocationCount = 10)
	public
	void testObjectToInterfaceTiming() throws Exception
	{
		final
		Object o=new Object(){
			public
			void run()
			{
				//System.err.println("Coercion works!");
			}
		};

		final
		long startTime=System.nanoTime();

		final
		Runnable runnable=Convert.objectToInterface(o, Runnable.class);
		{
			runnable.run();
		}

		final
		long duration=System.nanoTime()-startTime;

		System.err.println("object-to-interface time: "+duration+" nanos");
	}

	@Test(invocationCount = 10)
	public
	void testObjectToInterfaceBaseline() throws Exception
	{
		final
		Runnable o=new Runnable(){
			public
			void run()
			{
				//System.err.println("No Coercion needed!");
			}
		};

		final
		long startTime=System.nanoTime();

		final
		Runnable runnable=o;
		{
			runnable.run();
		}

		final
		long duration=System.nanoTime()-startTime;

		System.err.println("native-interface time: "+duration+" nanos");
	}
}