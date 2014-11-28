package com.github.osndok.mrb.plugin.b;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;

/**
 * The purpose of this class is to test the ability to create a command-line-tool without a
 * main method, by mapping primitive (and string) conversion into a constructor of the same
 * number of parameters.
 *
 * Some example/test vectors:
 * mrb-plugin-b-static string 0x3e 0xff 123 1234 12345 123456 1234567 12345678 1.11 1.12 1.23 1.234 t f a b 0x00ff00ff ALPHA
 * mrb-plugin-b-static null 0x3e null 123 null 12345 null 1234567 null 1.11 null 1.23 null t null a null null null
 */
public
class StaticRunnable implements Runnable
{
	private static final String JAVAX_MODULE_EXEC="mrb-plugin-b-static";

	private final
	String s;

	private final
	byte b;

	private final
	Byte b2;

	private final
	short aShort;

	private final
	Short aShort2;

	private final
	int i;

	private final
	Integer i2;

	private final
	long l;

	private final
	Long l2;

	private final
	float f;

	private final
	Float f2;

	private final
	double d;

	private final
	Double d2;

	private final
	boolean bool;

	private final
	Boolean bool2;

	private final
	char c;

	private final
	Character c2;

	private final
	byte[] bytes;

	private final
	EnumParameter anEnum;

	public
	StaticRunnable(
					  String s,
					  byte b,
					  Byte b2,
					  short s1,
					  Short s2,
					  int i,
					  Integer i2,
					  long l,
					  Long l1,
					  float f,
					  Float f2,
					  double d,
					  Double d2,
					  boolean bool,
					  Boolean bool2,
					  char c,
					  Character c2,
					  byte[] bytes,
					  EnumParameter anEnum
	)
	{
		this.s = s;
		this.b = b;
		this.b2 = b2;
		this.aShort = s1;
		this.aShort2 = s2;
		this.i = i;
		this.i2 = i2;
		this.l = l;
		this.l2 = l1;
		this.f = f;
		this.f2 = f2;
		this.d = d;
		this.d2 = d2;
		this.bool = bool;
		this.bool2 = bool2;
		this.c = c;
		this.c2 = c2;
		this.bytes = bytes;
		this.anEnum = anEnum;
	}

	public
	StaticRunnable(String s)
	{
		this.s=s;
		d=f=l=i=aShort=b=0; c=0;
		bool=false;
		d2=null; f2=null; l2=null; i2=null; aShort2=null; b2=null; bool2=null; c2=null;
		bytes=null;
		anEnum=null;
	}

	public
	StaticRunnable()
	{
		this.s="no-arguments-provided";
		d=f=l=i=aShort=b=0; c=0;
		bool=false;
		d2=null; f2=null; l2=null; i2=null; aShort2=null; b2=null; bool2=null; c2=null;
		bytes=null;
		anEnum=null;
	}

	@Override
	public
	String toString()
	{
		return "StaticRunnable{" +
				   "s='" + s + '\'' +
				   ", b=" + b +
				   ", b2=" + b2 +
				   ", aShort=" + aShort +
				   ", aShort2=" + aShort2 +
				   ", i=" + i +
				   ", i2=" + i2 +
				   ", l=" + l +
				   ", l2=" + l2 +
				   ", f=" + f +
				   ", f2=" + f2 +
				   ", d=" + d +
				   ", d2=" + d2 +
				   ", bool=" + bool +
				   ", bool2=" + bool2 +
				   ", c=" + c +
				   ", c2=" + c2 +
				   ", bytes=" + Arrays.toString(bytes) +
				   ", anEnum=" + anEnum +
				   '}';
	}

	public static
	void usage(PrintStream out)
	{
		out.println("usage: StaticRunnable <a-bunch-of-stuff-too-long-to-list-here>");
		out.println("\nused to test an alternate command-line-tool pattern that uses no main method, but instead parses the command line parameters into primitives for you and might be useful via native java references too.");
	}

	@Override
	public
	void run()
	{
		System.out.println("got: "+toString());
	}

	public
	enum EnumParameter
	{
		ALPHA,
		BETA,
		GAMMA,
		DELTA
	}
}
