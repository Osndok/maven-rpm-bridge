
package javax.module.tools;

// Copyright 2010 The Apache Software Foundation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * A Tuple holds two values of two possibly-distinct types.
 * Adapted from Howard Lewis Ship's most excellent Tapestry-5::Tuple class.
 *
 * @url https://github.com/apache/tapestry-5/blob/master/tapestry-func/src/main/java/org/apache/tapestry5/func/Tuple.java
 * @param <A> - the type of the first entry
 * @param <B> - the type of the second entry
 * @param <C> - the type of the third entry
 * @param <D> - the type of the fourth entry
 */
public class Tuple4 <A, B, C, D> extends Tuple
{
	public final A first;

	public final B second;

	public final C third;

	public final D fourth;

	public
	Tuple4(A first, B second, C third, D fourth)
	{
		super(4);
		this.first = first;
		this.second = second;
		this.third = third;
		this.fourth = fourth;
	}

	/**
	 * Returns the values of the tuple, separated by commas, enclosed in parenthesis. Example:
	 * <code>("Ace", "Spades")</code>.
	 */
	@Override
	public String toString()
	{
		StringBuilder builder = new StringBuilder("(");

		builder.append(String.valueOf(first));
		builder.append(", ");
		builder.append(String.valueOf(second));
		builder.append(", ");
		builder.append(String.valueOf(third));
		builder.append(", ");
		builder.append(String.valueOf(fourth));

		extendDescription(builder);

		return builder.append(")").toString();
	}

	/**
	 * Overriden in subclasses to write additional values into the
	 * description.
	 *
	 * @param builder
	 */
	protected void extendDescription(StringBuilder builder)
	{
	}

	/** Utility for comparing two values, either of which may be null. */
	static boolean isEqual(Object left, Object right)
	{
		return left == right || (left != null && left.equals(right));
	}

	/**
	 * Compares this Tuple to another object. Equality is defined by: other object is not null,
	 * is same class as this Tuple, and all values are themselves equal.
	 */
	@Override
	public boolean equals(Object obj)
	{
		if (obj == this)
			return true;

		if (obj == null || !(obj.getClass() == getClass()))
			return false;

		return isMatch(obj);
	}

	/**
	 * The heart of {@link #equals(Object)}; the other object is the same class as this object.
	 *
	 * @param other
	 *            other tuple to compare
	 * @return true if all values stored in tuple match
	 */
	protected boolean isMatch(Object other)
	{
		Tuple4 tuple = (Tuple4) other;

		return isEqual(first, tuple.first) &&
			isEqual(second, tuple.second) &&
			isEqual(third , tuple.third ) &&
			isEqual(fourth, tuple.fourth)
			;
	}

	@Override
	public
	int hashCode()
	{
		int result = first != null ? first.hashCode() : 0;
		result = 31 * result + (second != null ? second.hashCode() : 0);
		result = 31 * result + (third != null ? third.hashCode() : 0);
		result = 31 * result + (fourth != null ? fourth.hashCode() : 0);
		return result;
	}
}
