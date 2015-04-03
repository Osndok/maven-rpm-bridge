package javax.module.tools;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Created by robert on 4/2/15.
 */
@Test
public
class FuzzyEntryPointTest extends Assert
{
	@Test
	public
	void testIdentifierExplosion()
	{
		List<String> bits=FuzzyEntryPoint.explodeIdentifier("testingTesting123");
		assertTrue(bits.contains("testing"));
		assertTrue(bits.contains("Testing"));
		assertTrue(bits.contains("123"));

		bits=FuzzyEntryPoint.explodeIdentifier("TestSomethingElse");
		assertTrue(bits.contains("Test"));
		assertTrue(bits.contains("Something"));
		assertTrue(bits.contains("Else"));

		bits=FuzzyEntryPoint.explodeIdentifier("USB");
		assertTrue(bits.contains("USB"));

		bits=FuzzyEntryPoint.explodeIdentifier("setString");
		assertTrue(bits.contains("set"));
		assertTrue(bits.contains("String"));
	}
}
