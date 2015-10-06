package javax.module.util;

/**
 * Tuple classes are provided here to encourage interoperability between modules, and enhanced
 * code readability (because java can natively only return one value from a function, and it
 * can be useful to make a class that extends a typed-tuple).
 *
 * A particular design decision of note is that these Tuples do *NOT* inherit the class of lower
 * cardinality (as I have seen many Tuple classes do on github). IMO it is both incorrect and
 * not meaningful to claim that "PlayingCard isa Tuple2<Suit,Number>" if "Tuple2 isa Tuple1".
 */
public
class Tuple
{
	/**
	 * Having only a package-local constructor means that others will not be able to create their
	 * own tuples of higher cardinality (they should add them to this package as needed), and
	 * should also help separate any confusion between so many classes of similar names (e.g.
	 * Tapestry's Tuple class implies cardinality=2).
	 */
	/*package*/
	Tuple(int cardinality)
	{
		this.cardinality = cardinality;
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

	/* --------------------------------------------------------------------- */

	public
	int getCardinality()
	{
		return cardinality;
	}

	private final
	int cardinality;
}
