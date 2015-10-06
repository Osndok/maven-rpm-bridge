package javax.module.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by robert on 4/2/15.
 */
public
class Convert
{
	public static
	Class[] getSupportedNonPrimitiveTypes()
	{
		return supportedNonPrimitiveTypes;
	}

	private static
	Class[] supportedNonPrimitiveTypes =
		new Class[]
			{
				String.class, File.class,
				InputStream.class, FileInputStream.class, PrintStream.class,
				OutputStream.class, FileOutputStream.class, BufferedReader.class,
				Enum.class, // <-- ???
				Short.class, Integer.class, Long.class, Float.class, Double.class, Boolean.class, Character.class, Byte.class,
			};

	private static
	Set<Class> supportedNonPrimitiveTypeSet;

	public static
	boolean isSupportedType(Class aClass)
	{
		if (aClass.isPrimitive())
		{
			return true;
		}

		if (supportedNonPrimitiveTypeSet==null)
		{
			supportedNonPrimitiveTypeSet=new HashSet<Class>();

			for (Class supportedNonPrimitiveType : supportedNonPrimitiveTypes)
			{
				supportedNonPrimitiveTypeSet.add(supportedNonPrimitiveType);
			}
		}

		return supportedNonPrimitiveTypeSet.contains(aClass);
	}

	@Deprecated
	public static
	Object stringToBasicObject(String stringValue, Class targetType)
	{
		return stringToBasicObject(stringValue, targetType, null);
	}

	public static
	Object stringToBasicObject(String stringValue, Class targetType, Object[] contextArrayOrNull)
	{
		//TODO: primitive types cannot be null, can probably give a much better message therefor.
		if (stringValue.equals("null")) return null;

		if (targetType == String.class) return stringValue;

		if (Enum.class.isAssignableFrom(targetType))
		{
			try
			{
				return Enum.valueOf(targetType, stringValue);
			}
			catch (IllegalArgumentException e)
			{
				final
				String context=getContextFrom(contextArrayOrNull);

				throw new IllegalArgumentException(context+targetType.getSimpleName()+" parameter cannot be '"+stringValue+"', valid values are: "+ enumValuesToHumanReadableCsv(targetType));
			}
		}

		if (targetType==short   .class || targetType==Short    .class) return new Short(stringValue);
		if (targetType==int     .class || targetType==Integer  .class) return new Integer(stringValue);
		if (targetType==long    .class || targetType==Long     .class) return new Long(stringValue);
		if (targetType==float   .class || targetType==Float    .class) return new Float(stringValue);
		if (targetType==double  .class || targetType==Double   .class) return new Double(stringValue);
		if (targetType==boolean .class || targetType==Boolean  .class) return stringToBooleanObject(stringValue);

		if (targetType==char    .class || targetType==Character.class)
		{
			assert(stringValue.length()==1);
			return stringValue.charAt(0);
		}

		if (targetType==byte    .class || targetType==Byte     .class)
		{
			//Byte::decode() has sign issues... can't do 0xFF, for example.
			//return Byte.decode(stringValue);

			if (stringValue.startsWith("0x"))
			{
				stringValue=stringValue.substring(2);
			}

			//TODO: make this less ugly.
			assert(stringValue.length()<=2);
			return hexStringToByteArray(stringValue)[0];
		}

		if (targetType==byte[]  .class)
		{
			if (stringValue.startsWith("0x"))
			{
				stringValue=stringValue.substring(2);
			}

			//BigInteger drops leading zeros...
			//return new BigInteger(stringValue, 16).toByteArray();
			return hexStringToByteArray(stringValue);
		}

		if (targetType==File.class)
		{
			return new File(stringValue);
		}

		if (targetType==InputStream.class || targetType==FileInputStream.class)
		{
			try
			{
				if (stringValue.equals("-"))
				{
					if (targetType==FileInputStream.class)
					{
						//TODO: check to make sure this works (FileInputStream argument satisfied by stdin)
						return new FileInputStream("/dev/stdin");
					}
					else
					{
						return System.in;
					}
				}
				else
				{
					return new FileInputStream(stringValue);
				}
			}
			catch (FileNotFoundException e)
			{
				throw new RuntimeException(e);
			}
		}

		if (targetType == OutputStream.class || targetType == FileOutputStream.class)
		{
			try
			{
				if (stringValue.equals("-"))
				{
					if (targetType==FileOutputStream.class)
					{
						//TODO: check to make sure this works (FileOutputStream argument satisfied by stdout)
						return new FileOutputStream("/dev/stdout");
					}
					else
					{
						return System.out;
					}
				}
				else
				{
					return new FileOutputStream(stringValue);
				}
			}
			catch (FileNotFoundException e)
			{
				throw new RuntimeException(e);
			}
		}

		if (targetType==PrintStream.class)
		{
			if (stringValue.equals("-"))
			{
				return System.out;
			}
			else
			{
				try
				{
					return new PrintStream(stringValue);
				}
				catch (FileNotFoundException e)
				{
					throw new RuntimeException(e);
				}
			}
		}

		if (targetType==BufferedReader.class)
		{
			try
			{
				if (stringValue.equals("-"))
				{
					//TODO: check to make sure this works (BufferedReader argument satisfied by stdin)
					return new BufferedReader(new FileReader("/dev/stdin"));
				}
				else
				{
					return new BufferedReader(new FileReader(stringValue));
				}
			}
			catch (FileNotFoundException e)
			{
				throw new RuntimeException(e);
			}
		}

		throw new UnsupportedOperationException(targetType+" constructor parameters are not supported");
	}

	private static
	String getContextFrom(Object[] contextArrayOrNull)
	{
		if (contextArrayOrNull==null)
		{
			return "";
		}
		else
		{
			final
			StringBuilder sb=new StringBuilder();

			for (Object o : contextArrayOrNull)
			{
				sb.append(o);
			}

			return sb.toString();
		}
	}

	/*
	http://stackoverflow.com/questions/140131/convert-a-string-representation-of-a-hex-dump-to-a-byte-array-using-java
	 */
	public static
	byte[] hexStringToByteArray(String s)
	{
		int len = s.length();

		if (len%2==1)
		{
			s="0"+s;
			len++;
		}

		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
									  + Character.digit(s.charAt(i+1), 16));
		}
		return data;
	}

	public static
	boolean stringToBooleanPrimitive(String s)
	{
		if (s.length()==1)
		{
			return characterToBooleanPrimitive(s.charAt(0));
		}
		else
		{
			//To avoid accidentally interpreting a misplaced string as a boolean, we require the full string (except for the one-char options above).
			s=s.toLowerCase();

			/*
			Matching this fine specification (missing 'undefined' strings, though):
			http://www.postgresql.org/docs/9.1/static/datatype-boolean.html
			 */

			if (s.equals("true" ) || s.equals("yes") || s.equals("on") ) return Boolean.TRUE;
			if (s.equals("false") || s.equals("no" ) || s.equals("off")) return Boolean.FALSE;
			throw new IllegalArgumentException("unable to interpret boolean parameter: "+s);
		}
	}

	public static
	Boolean stringToBooleanObject(String s)
	{
		if (s.length()==1)
		{
			return characterToBooleanObject(s.charAt(0));
		}
		else
		{
			//To avoid accidentally interpreting a misplaced string as a boolean, we require the full string (except for the one-char options above).
			s=s.toLowerCase();

			/*
			Matching this fine specification (missing 'undefined' strings, though):
			http://www.postgresql.org/docs/9.1/static/datatype-boolean.html
			 */

			if (s.equals("true" ) || s.equals("yes") || s.equals("on") ) return Boolean.TRUE;
			if (s.equals("false") || s.equals("no" ) || s.equals("off")) return Boolean.FALSE;
			if (s.equals("undefined") || s.equals("maybe")) return null;
			throw new IllegalArgumentException("unable to interpret boolean parameter: "+s);
		}
	}

	/**
	 * Duplicated from amicus::SubConfigImpl
	 *
	 * @param c
	 * @return
	 */
	public static
	boolean characterToBooleanPrimitive(char c)
	{
		switch (c)
		{
			case '1':
			case 't':
			case 'T':
			case 'y':
			case 'Y':
				return true;

			case '0':
			case 'f':
			case 'F':
			case 'n':
			case 'N':
				return false;

			default:
				throw new IllegalArgumentException("unable to convert '"+c+"' into a boolean primitive");
		}
	}

	/**
	 * Duplicated from amicus::SubConfigImpl; this might become the new SoA.
	 *
	 * @param c
	 * @return
	 */
	public static
	Boolean characterToBooleanObject(char c)
	{
		switch (c)
		{
			case '1':
			case 't':
			case 'T':
			case 'y':
			case 'Y':
				return Boolean.TRUE;

			case '0':
			case 'f':
			case 'F':
			case 'n':
			case 'N':
				return Boolean.FALSE;

			case 'x':
			case 'u':
			case 'U':
			case '?':
				return null;

			default:
				throw new IllegalArgumentException("unable to convert '" + c + "' into a boolean object");
		}
	}

	private static
	String enumValuesToHumanReadableCsv(Class<? extends Enum> enumClass)
	{
		try
		{
			Method method = enumClass.getMethod("values");
			Object[] enumValues = (Object[]) method.invoke(null);

			StringBuilder sb=new StringBuilder();

			for (Object o : enumValues)
			{
				sb.append(o);
				sb.append(',');
				sb.append(' ');
			}

			//clip off the last comma... and space...
			sb.deleteCharAt(sb.length()-1);
			sb.deleteCharAt(sb.length()-1);

			return sb.toString();
		}
		catch (Throwable t)
		{
			t.printStackTrace();
			return "<unable-to-determine>";
		}
	}

	private
	Convert()
	{
		throw new UnsupportedOperationException();
	}

	public static
	Object stringToArray(String s, Class parameterType, Object[] context)
	{
		//NB: CSV seems to be the most common for command-line flags
		//NB: ...but this is *also* called for each array argument.
		//e.g. "do-something --with=a,b,c --but-not=d,e,f other arguments here"
		//TODO: support "literal" arrays, like "[a,b,c]", or a better parsing mechanic.
		final
		String[] args = s.split(",");

		final
		Class componentType = parameterType.getComponentType();

		final
		Object retval=Array.newInstance(componentType, args.length);

		int i=0;

		for (String arg : args)
		{
			Array.set(retval, i, stringToBasicObject(arg, componentType, context));
			i++;
		}

		return retval;
	}

	static
	Object dumpRemainingVarargs(List<String> arguments, int i, Class parameterType, Object[] context)
	{
		final
		Class componentType = parameterType.getComponentType();

		final
		int l=arguments.size()-i;

		final
		Object retval=Array.newInstance(componentType, l);

		for (int j=0; j<l; j++)
		{
			Array.set(retval, j, stringToBasicObject(arguments.get(j+i), componentType, context));
		}

		return retval;

	}
}
