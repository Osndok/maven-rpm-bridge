package javax.module;


import javax.module.meta.LoaderModule;
import javax.module.util.VersionString;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Dynamically creates a class which adapts a module's "fully-qualified" module
 * name to the actual class name. This is principally used to circumvent a limitation
 * or safety check within the virtual machine where a classloader cannot return a
 * class with a name which differs from the one requested.
 * <p/>
 * Intended for use via a Class.forName() call, and works for the majority of
 * real-life cases with the following limitations:
 * - the target class must be public & not final
 * - the target class must have a public no-args constructor
 * - the requesting code must use the no-args constructor (i.e. class.newInstance()).
 */
@CommandLineTool(suffix = "bytecode-extension-test")
class ModuleNameStrippingByteCodeClassExtender
{

	private final String oldName;
	private final String newName;

	ModuleNameStrippingByteCodeClassExtender(String oldName, String newName)
	{
		this.oldName = oldName;
		this.newName = newName;
	}

	private
	String getBinaryName(String s)
	{
		if (s.endsWith("class"))
		{
			//@bug: check logic
			return s.substring(0, newName.length() - 6).replace(".", "/");
		}
		else
		{
			return s.replace(".", "/");
		}
	}

	/**
	 * Returns the class-file-format bytecode for a very-very-simple class which:
	 * - extends the given class
	 * - has the given newName as it's name
	 * - has a no-args constructor (which defers to the super-no-args-constructor).
	 */
	byte[] getClassData() throws IOException
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(baos);

		dump(PREAMBLE, out);
		out.writeByte(01);
		out.writeUTF(getBinaryName(newName));
		out.writeByte(01);
		out.writeUTF(getBinaryName(oldName));
		dump(POSTAMBLE, out);
		out.flush();

		return baos.toByteArray();
	}

	private
	void dump(String hex, DataOutputStream out) throws IOException
	{
		int l = hex.length();
		for (int i = 0; i < l; i += 2)
		{
			int one = hex.charAt(i);
			if (one >= 'A')
			{
				one = (one - 'A') + 10;
			}
			else
			{
				one -= '0';
			}
			int two = hex.charAt(i + 1);
			if (two >= 'A')
			{
				two = (two - 'A') + 10;
			}
			else
			{
				two -= '0';
			}
			out.writeByte((one << 4) | two);
		}
	}

	/**
	 * contains the class-file magic and most of the constant pool, ending
	 * just before the needed class-name & super-class-name.
	 */
	private static final
	String PREAMBLE =
		"CAFEBABE00000031" + "000A0A0003000707" +
		"0008070009010006" + "3C696E69743E0100" +
		"0328295601000443" + "6f64650C00040005";

	/**
	 * declares one method (the public no-args constructor) with the bytecode therefor,
	 * zero interfaces, one attribute (public), etc.
	 */
	private static final
	String POSTAMBLE =
		"002100020003" + "0000000000010001" +
		"0004000500010006" + "0000001100010001" +
		"000000052AB70001" + "B1000000000000";

	private static final
	char[] HEX_DIGITS = {
		 '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
	};

	private static
	void print(byte[] ba)
	{
		java.io.PrintStream out = System.out;
		int length = ba.length;
		char[] buf = new char[length * 2];
		for (int i = 0, j = 0, k; i < length; )
		{
			k = ba[i++];
			buf[j++] = HEX_DIGITS[(k >>> 4) & 0x0F];
			buf[j++] = HEX_DIGITS[k & 0x0F];
		}
		out.println(buf);
	}

	public static
	void main(String[] args) throws IOException
	{
		//args[0] = oldName
		//args[1] = newName
		ModuleNameStrippingByteCodeClassExtender se = new ModuleNameStrippingByteCodeClassExtender(args[0], args[1]);
		byte[] bs = se.getClassData();
		print(bs);
	}
}

/*
 to compare byte-codes, try compiling this small class without debugging symbols:
-----------
package module_v.actual.pack;
public class Name extends java.util.ArrayList {
}
-----------
$ javac -g:none Name.java
$ hexdump -C Name.class

 00000000  ca fe ba be 00 00 00 31  00 0a 0a 00 03 00 07 07  |????...1........|
 00000010  00 08 07 00 09 01 00 06  3c 69 6e 69 74 3e 01 00  |........<init>..|
 00000020  03 28 29 56 01 00 04 43  6f 64 65 0c 00 04 00 05  |.()V...Code.....|
 00000030  01 00 19 6d 6f 64 75 6c  65 5f 76 2f 61 63 74 75  |...module_v/actu|
 00000040  61 6c 2f 70 61 63 6b 2f  4e 61 6d 65 01 00 13 6a  |al/pack/Name...j|
 00000050  61 76 61 2f 75 74 69 6c  2f 41 72 72 61 79 4c 69  |ava/util/ArrayLi|
 00000060  73 74 00 21 00 02 00 03  00 00 00 00 00 01 00 01  |st.!............|
 00000070  00 04 00 05 00 01 00 06  00 00 00 11 00 01 00 01  |................|
 00000080  00 00 00 05 2a b7 00 01  b1 00 00 00 00 00 00     |....*?..?......|
 0000008f

*/
