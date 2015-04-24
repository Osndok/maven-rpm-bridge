package com.github.osndok.mrb.runner;

/**
 * Used to describe and segregate any mention of another java class into several
 * buckets that might have distinct processing concerns.
 */
public
enum JavaReferenceType
{
	/**
	 * A SYSTEM reference indicates the use of a class that is bundled with the java
	 * runtime environment. For normal compilation and execution, these are not expected
	 * to require any additions to the classpath or module reference. Classes under the
	 * 'java.lang' package may be used without an explicit 'import' statement.
	 */
	SYSTEM,

	/**
	 * A LIBRARY reference indicates the use of a class that comes pre-packaged in a
	 * distinct jar library or module. For example, it is implied that the source code
	 * for this class is not relevant (or may not be available), because it is outside
	 * of our parent group. These always require an 'import' statement.
	 */
	LIBRARY,

	/**
	 * A SELF reference indicates the use of a class that is in the same module (but not
	 * package) as this java class. As such, it is implied that the source code for
	 * the target class is very relevant, and likely to be in the same 'jar' archive.
	 * These always require an 'import' statement.
	 */
	SELF,

	/**
	 * A SIBLING reference indicates the use of a class that is in a different module
	 * (but under the same parent group) as the given java file. As such, it is implied that
	 * the source code for the target class is moderately relevant and available, and that
	 * the class resides in a different package. These always require an 'import' statement.
	 */
	SIBLING,

	/**
	 * A STATIC reference is usually reserved for concisely referencing one or more enum
	 * constants, and implies that the target is not actually a class (but a field). This
	 * reference type is used to disambiguate what would otherwise be surmised as PACKAGE
	 * class references. These always require and imply an 'import static' statement.
	 */
	STATIC,

	/**
	 * A PACKAGE reference indicates that the target is *BOTH* in the same module *AND* in
	 * the same package as this java class. These usually do not require an explicit 'import'
	 * statement, unless required to disambiguate another available class of the same name
	 * (perhaps under the 'java.lang' package). This implies that the source code for the
	 * target is not only available, and extremely relevant, but also likely to be in the
	 * same module or 'jar' archive.
	 */
	PACKAGE,

	/**
	 * Unlike the other reference types, which indicate a dependency, this "reference"
	 * indicates a Callable/Runnable/main-method that can serve as a useful entry point
	 * (or start of execution; like a public main method) into this code module.
	 */
	ENTRY_POINT,
}
