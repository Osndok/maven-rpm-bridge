package javax.module;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by robert on 10/29/14.
 */
public final
class Version implements Comparable<Version>, Serializable
{
	private static final
	char ASCII_TOLOWER_GAP = ('a' - 'A');

	static final
	boolean DEBUG = false;

	//TODO: convert this to be populated by Maven at build time
	//TODO: have this cause the same module to *not* be loaded at runtime (as it's in the ole... 'classpath' mechanism)
	public static final
	ModuleKey JAVAX_MODULE = new ModuleKey("javax-module", "1", null);

	public final
	String stringValue;

	public
	Version(String stringValue)
	{
		if (stringValue == null) throw new NullPointerException();
		this.stringValue = stringValue;
	}

	@Override
	public
	String toString()
	{
		return stringValue;
	}

	@Override
	public
	int compareTo(Version v)
	{
		return compare(getBits(), v.getBits());
	}

	public
	boolean isNewerThan(Version v)
	{
		return compare(getBits(), v.getBits())>0;
	}

	public
	boolean isOlderThan(Version v)
	{
		return compare(getBits(), v.getBits())<0;
	}

	@Deprecated
	public static
	int compare(String a, String b)
	{
		if (a==null)
		{
			return (b==null?0:-1);
		}
		else
		if (b==null)
		{
			return 1;
		}
		else
		{
			return new Version(a).compareTo(new Version(b));
		}
	}

	private static
	int compare(Object[] a, Object[] b)
	{
		final int A_IS_NEWER =  1;
		final int B_IS_NEWER = -1;
		final int DRAW=0;

		int i=0;

		do
		{
			Object alpha=(i<a.length?a[i]:null);
			Object beta =(i<b.length?b[i]:null);

			if (DEBUG)
			{
				System.err.println("compare: i=" + i + ", alpha=" + alpha + ", beta=" + beta);
			}

			if (alpha==null)
			{
				if (beta==null) return DRAW;

				//We ran out of 'a', but not 'b'...

				//NB: a trailing-string *LESSENS* your version value (1.0-SNAPSHOT, 1.0-rc4, etc),
				//    while a trailing-number *INCREASES* your value (1.0 < 1.0.1)
				if (beta instanceof String)
				{
					return A_IS_NEWER;
				}
				else
				{
					//Consuming trailing zeros
					while(beta instanceof Long && (Long)beta==0)
					{
						i++;
						if (i>=b.length)
						{
							return DRAW;
						}
						beta = b[i];
					}

					return B_IS_NEWER;
				}
			}
			else
			if (beta==null)
			{
				//NB: a trailing-string *LESSENS* your version value (1.0-SNAPSHOT, 1.0-rc4, etc),
				//    while a trailing-number *INCREASES* your value (1.0 < 1.0.1)
				if (alpha instanceof String)
				{
					return B_IS_NEWER;
				}
				else
				{
					//Consuming trailing zeros
					while(alpha instanceof Long && (Long)alpha==0)
					{
						i++;
						if (i>=a.length)
						{
							return DRAW;
						}
						alpha = a[i];
					}

					return A_IS_NEWER;
				}
			}
			else
			if (alpha instanceof Long)
			{
				if (beta instanceof Long)
				{
					int diff=(int)(((Long)alpha)-((Long)beta));
					if (diff!=0) return diff;
				}
				else
				{
					//beta is a string, which is lesser than an integer.
					return A_IS_NEWER;
				}
			}
			else
			{
				//alpha isa string
				if (beta instanceof String)
				{
					//Not very meaningful... can we do any better? should we just presume all strings are equal?
					int diff=((String)alpha).compareTo((String)beta);
					if (diff!=0) return diff;
				}
				else
				{
					//Integers come first.
					return B_IS_NEWER;
				}
			}

			i++;
		}
		while(true);
	}

	@Override
	public
	boolean equals(Object o)
	{
		if (o instanceof Version)
		{
			return compareTo((Version)o)==0;
		}
		else
		{
			return this.stringValue.equals(o.toString());
		}
	}

	/*
	 * NB: does not comply with the other side of the equals() contract very well, we
	 * *could* generate a hash based on getBits(), but that would still not catch all
	 * the nuances of the version.equals() command, which at times ignores strings
	 * and zeros based on what you are comparing it against.
	 */
	@Override
	public
	int hashCode()
	{
		return stringValue.hashCode();
	}

	/**
	 * Given a version string (e.g. "1.20.3-rc4-aoe9u87"), return the relevant bits that should be individually compared.
	 * @param s
	 * @return An array of Strings and Longs
	 */
	public static
	Object[] split(String s)
	{
		final
		int s_length=s.length();

		List<Object> retval=new ArrayList<Object>(5);

		if (s == null || s.length() == 0)
		{
			s = "0";
		}

		boolean oneDigit = isDigit(s.charAt(0));

		//example: 2.43.1; proto4; alpha5 (assume numbers to be greater)

		//read-head (position); starts at zero
		int overallPosition = 0;

		boolean eatNumber = oneDigit;

		do
		{
			//find the next digits from the readhead
			//if we are munching strings compare to the next number
			int nextBreak = overallPosition;
			boolean gotNonBreak = false;
			char c;

			do
			{
				nextBreak++;

				if (nextBreak == s.length())
				{
					break;
				}

				c=s.charAt(nextBreak);

				if (isBreak(c))
				{
					if (gotNonBreak) break;
				}
				else
				{
					gotNonBreak=true;
				}

				if (eatNumber != isDigit(c))
				{
					break;
				}
			}
			while (true);

			String oneSub = s.substring(overallPosition, nextBreak);

			if (eatNumber)
			{
				//if we are munching numbers, compare each number as an int (e.g. 2.10 > 2.9)
				//This should always be a parsable int, or the algorithm is wrong
				//We use "Long" b/c it is not too uncommon to use dates+hours w/o separators, which makes a big integer!
				retval.add(new Long(oneSub));
				//System.err.println("int: "+oneSub);
			}
			else
			{
				//System.err.print("not: " + oneSub);
				String trimmed=trimBreakTokensAndLowerCase(oneSub);
				//System.err.println(" --> '" + trimmed + "'");

				//USUALLY equal, and USUALLY "." (a single period) which is trimmed away
				//ehh.. just do a lowercase string compare...
				if (trimmed.length()!=0)
				{
					retval.add(trimmed);
				}
			}

			//advance the readheads (note that particularly for numbers, they may not be equal).
			overallPosition += oneSub.length();

			if (overallPosition == s_length)
			{
				break;
			}

			char nextChar=s.charAt(overallPosition);
			//System.err.println("nextChar="+nextChar);

			eatNumber = isDigit(nextChar);

			//have we run out of string?
		} while (true);

		return retval.toArray();
	}

	private static
	String trimBreakTokensAndLowerCase(String oneSub)
	{
		final
		int l=oneSub.length();

		final
		StringBuilder sb=new StringBuilder(oneSub.length());

		for(int i=0; i<l; i++)
		{
			char c=oneSub.charAt(i);

			if (!isBreak(c))
			{
				if (isUpper(c))
				{
					sb.append((char) (c + ASCII_TOLOWER_GAP));
				}
				else
				{
					sb.append(c);
				}
			}
		}

		return sb.toString();
	}

	private static
	boolean isUpper(char c)
	{
		return c>='A' && c<='Z';
	}

	private static
	boolean isBreak(char c)
	{
		//Almost any non-alphanumeric character would be acceptable here...
		return c=='.' || c=='-' || c==' ' || c==':' || c=='_' || c=='/' || c=='@';
	}

	private static
	boolean isDigit(char a)
	{
		return (a>='0' && a<='9');
	}

	private transient
	Object[] bits;

	/**
	 * @return an array of Integer and String objects that make up this version
	 */
	public
	Object[] getBits()
	{
		Object[] retval=this.bits;

		if (retval==null)
		{
			this.bits=retval=split(stringValue);
		}

		return retval;
	}
}
