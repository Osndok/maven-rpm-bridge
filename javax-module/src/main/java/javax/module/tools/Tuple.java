package javax.module.tools;

/**
 * Tuple classes are provided here to encourage interoperability between modules, and enhanced
 * code readability (because java can natively only return one value from a function).
 */
public
class Tuple
{
	/**
	 * Being a static class, and unable to construct a "Tuple" will help separate any confusion between
	 * so many classes of similar names. Our tuples do not inherit one another, and have a suffix indicating
	 * the cardinality.
	 */
	private
	Tuple()
	{

	}

	public static <X, Y>
	Tuple2<X, Y> create(X first, Y second)
	{
		return new Tuple2<X, Y>(first, second);
	}

	public static <X, Y, Z>
	Tuple3<X, Y, Z> create(X first, Y second, Z third)
	{
		return new Tuple3<X, Y, Z>(first, second, third);
	}

	public static <X, Y, Z, W>
	Tuple4<X, Y, Z, W> create(X first, Y second, Z third, W fourth)
	{
		return new Tuple4<X, Y, Z, W>(first, second, third, fourth);
	}

}
