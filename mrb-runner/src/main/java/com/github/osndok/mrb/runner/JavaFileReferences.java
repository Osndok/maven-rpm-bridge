package com.github.osndok.mrb.runner;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.TypeParameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.module.LegacyMainMethod;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;

import static com.github.osndok.mrb.runner.JavaReferenceType.LIBRARY;
import static com.github.osndok.mrb.runner.JavaReferenceType.PACKAGE;
import static com.github.osndok.mrb.runner.JavaReferenceType.SELF;
import static com.github.osndok.mrb.runner.JavaReferenceType.SIBLING;
import static com.github.osndok.mrb.runner.JavaReferenceType.STATIC;
import static com.github.osndok.mrb.runner.JavaReferenceType.SYSTEM;

/**
 * Given a special java file as input (that conforms to the prescribed *subset* of the
 * java language) and a "known" list of system classes, generate a usable list of all
 * classes and libraries (by package) that this java file depends on.
 *
 * TODO: this class should be able to optionally detect ENTRY_POINT references too.
 */
public
class JavaFileReferences implements Runnable, Callable<Set<JavaReference>>
{
	private final
	InputStream inputStream;

	private final
	OutputStream outputStream;

	public
	JavaFileReferences(InputStream inputStream)
	{
		this.inputStream = inputStream;
		this.outputStream = null;
	}

	public
	JavaFileReferences(InputStream inputStream, OutputStream outputStream)
	{
		this.inputStream = inputStream;
		this.outputStream = outputStream;
	}

	private final
	Set<JavaReference> references = new TreeSet<>();

	private final
	Map<String, JavaReference> byIdentifier = new HashMap<>();

	private final
	Map<String, JavaReference> byFullyQualifiedName = new HashMap<>();

	private final
	Map<String, JavaReference> exampleByPackageName = new HashMap<>();

	public
	Set<JavaReference> getReferences()
	{
		if (!hasRun)
		{
			run();
		}

		return references;
	}

	@Override
	public
	Set<JavaReference> call() throws Exception
	{
		if (!hasRun)
		{
			run2();
		}

		return references;
	}

	private
	boolean hasRun;

	@Override
	public
	void run()
	{
		try
		{
			run2();
		}
		catch (RuntimeException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	private static final
	Logger log = LoggerFactory.getLogger(JavaFileReferences.class);

	private
	Set<String> selfReferentialNames;

	private
	String packageName;

	private
	void run2() throws Exception
	{
		final
		JavaSystemClasses javaSystemClasses = JavaSystemClasses.getInstance();

		final
		CompilationUnit cu;
		{
			try
			{
				cu = JavaParser.parse(inputStream);
			}
			finally
			{
				inputStream.close();
			}
		}

		final
		PackageDeclaration packageDeclaration = cu.getPackage();
		{
			if (packageDeclaration == null)
			{
				throw new UnsupportedJavaGrammar("must have a declared package name");
			}

			packageName = packageDeclaration.getName().toString();

			log.debug("package = '{}'", packageName);
		}

		for (ImportDeclaration importDeclaration : cu.getImports())
		{
			final
			String name = importDeclaration.getName().toString();

			if (importDeclaration.isAsterisk())
			{
				throw new UnsupportedJavaGrammar("asterisk/wildcard imports are not supported: '" + name + "'");
			}

			log.debug("import: {}", name);

			if (importDeclaration.isStatic())
			{
				addReference(STATIC, name);

				final
				String fullyQualified = JavaReference.getPackageName(name);

				addFullyQualifiedClassName(fullyQualified);
			}
			else if (javaSystemClasses.contains(name))
			{
				addReference(SYSTEM, name);
			}
			else
			{
				addFullyQualifiedClassName(name);
			}
		}

		final
		Set<String> genericsPlaceholderNames = new HashSet<>();

		selfReferentialNames = extractRelevantTypeNames(cu);

		final
		VoidVisitorAdapter typeVisitor = new VoidVisitorAdapter()
		{
			@Override
			public
			void visit(TypeParameter n, Object arg)
			{
				final
				String name = n.getName();

				log.debug("generic-type: {}", name);

				genericsPlaceholderNames.add(name);

				super.visit(n, arg);
			}

			@Override
			public
			void visit(ClassOrInterfaceType n, Object arg)
			{
				super.visit(n, arg);

				final
				ClassOrInterfaceType scope=n.getScope();

				/**
				 * The issue of 'scope' is a handled a bit weird. If we read in a fully qualified
				 * class name like "java.util.Set", the visitor will visit each one in turn, with
				 * the scope accruing. The only way I know to avoid this is to backtrack... :(
				 */
				if (scope!=null)
				{
					log.debug("scope: {}", scope);
					maybeUndoVisit(scope);
				}

				final
				String name = n.getName();

				if (genericsPlaceholderNames.contains(name))
				{
					log.debug("ignoring generic type: {}", name);
				}
				else
				if (selfReferentialNames.contains(name))
				{
					log.debug("ignoring self-reference: {}", name);
				}
				else
				if (scope==null)
				{
					log.debug("class/interface: {} (unqualified)", name);
					maybeAddUnqualifiedClassName(n.getName());
				}
				else
				{
					final
					String fullyQualified=scope.toString()+'.'+name;

					log.debug("class/interface: {} (full)", fullyQualified);
					addFullyQualifiedClassName(fullyQualified);
				}
			}
		};

		typeVisitor.visit(cu, null);

		if (!includeStatic)
		{
			removeStaticReferences();
		}

		if (outputStream != null)
		{
			printTo(outputStream);
		}

		//Last, so that an exception will not cause a confusing empty set to be returned on a subsequent call.
		hasRun = true;
	}

	private
	void removeStaticReferences()
	{
		final
		Iterator<JavaReference> i = references.iterator();

		while (i.hasNext())
		{
			if (i.next().getReferenceType()==STATIC)
			{
				i.remove();
			}
		}
	}

	private
	void maybeUndoVisit(ClassOrInterfaceType type)
	{
		final
		ClassOrInterfaceType scope=type.getScope();

		if (scope==null)
		{
			maybeUndoUnqualifiedIdentifier(type.getName());
		}
		else
		{
			maybeUnfoFullyQualifiedIdentifier(scope.toString()+'.'+type.getName());
		}
	}

	private
	Set<String> extractRelevantTypeNames(CompilationUnit cu)
	{
		final
		Set<String> retval = new HashSet<>();

		for (TypeDeclaration typeDeclaration : cu.getTypes())
		{
			retval.add(typeDeclaration.getName());
		}

		return retval;
	}

	private
	void printTo(OutputStream outputStream)
	{
		final
		PrintStream out = new PrintStream(outputStream);

		try
		{
			for (JavaReference reference : references)
			{
				out.println(reference);
			}
		}
		finally
		{
			out.close();
		}
	}

	private
	void addFullyQualifiedClassName(String name)
	{
		if (byFullyQualifiedName.containsKey(name))
		{
			//cut down on logging noise (file guesses)...
			return;
		}

		final
		String packageName = JavaReference.getPackageName(name);

		final
		JavaReference example = exampleByPackageName.get(packageName);

		if (example!=null)
		{
			//avoid unnecessary file accesses & guessing...
			//we presume the same package always implies the same reference type
			//(e.g. classes in jars won't be overridden by this module)
			addReference(example.getReferenceType(), name);
			return;
		}

		final
		String subPath = classNameToJavaFileSubPath(name);

		if (hasMatchingSelfFile(subPath))
		{
			addReference(SELF, name);
		}
		else
		if (hasMatchingSiblingFile(subPath))
		{
			addReference(SIBLING, name);
		}
		else
		if (name.startsWith(this.packageName))
		{
			addReference(PACKAGE, name);
		}
		else
		{
			addReference(LIBRARY, name);
		}
	}

	private
	void maybeUndoUnqualifiedIdentifier(String name)
	{
		maybeUnfoFullyQualifiedIdentifier(packageName+'.'+name);
	}

	private
	void maybeUnfoFullyQualifiedIdentifier(String name)
	{
		final
		JavaReference javaReference = byFullyQualifiedName.remove(name);

		log.debug("undo-full: {} -> {}", name, javaReference);

		if (javaReference!=null)
		{
			if (!javaReference.getPackageName().equals(packageName))
			{
				exampleByPackageName.remove(javaReference.getPackageName());
				errantPackageNames.add(javaReference.getPackageName());
			}

			references.remove(javaReference);
			byIdentifier.remove(javaReference.getClassName());
		}
	}

	private
	Set<String> errantPackageNames = new HashSet<>();

	private
	<M>
	Collection<M> notNull(Collection<M> c)
	{
		if (c == null)
		{
			return Collections.emptyList();
		}
		else
		{
			return c;
		}
	}

	/**
	 * @param packageAndClassName - e.g. "java.lang.Object"
	 * @return e.g. "java/lang/Object.java"
	 */
	private
	String classNameToJavaFileSubPath(String packageAndClassName)
	{
		return packageAndClassName.replaceAll("\\.", "/") + ".java";
	}

	private
	boolean hasMatchingSelfFile(String subPath)
	{
		if (self == null)
		{
			return false;
		}
		else
		{
			return exists(self, subPath);
		}
	}

	private
	boolean exists(File directory, String subPath)
	{
		final
		File file = new File(directory, subPath);

		if (file.exists())
		{
			log.debug("noticed: {}", file);
			return true;
		}
		else
		{
			log.debug("dne: {}", file);
			return false;
		}
	}

	private
	boolean hasMatchingSiblingFile(String subPath)
	{
		for (File directory : getSiblingDirectories())
		{
			if (exists(new File(directory, sourcePath), subPath))
			{
				return true;
			}
		}

		return false;
	}

	private
	void addReference(JavaReferenceType javaReferenceType, String packageAndClass)
	{
		if (byFullyQualifiedName.containsKey(packageAndClass))
		{
			throw new IllegalStateException("already added: '" + packageAndClass + "'");
		}

		log.debug("add: {} / {}", javaReferenceType, packageAndClass);

		final
		JavaReference javaReference = new JavaReference(javaReferenceType, packageAndClass);

		references.add(javaReference);
		byIdentifier.put(javaReference.getClassName(), javaReference);
		byFullyQualifiedName.put(packageAndClass, javaReference);
		exampleByPackageName.put(javaReference.getPackageName(), javaReference);
	}

	private
	boolean includeStatic;

	public
	void setIncludeStatic(boolean includeStatic)
	{
		this.includeStatic=includeStatic;
	}

	private
	File self;

	public
	void setSelf(File self)
	{
		if (!self.isDirectory())
		{
			throw new IllegalArgumentException("not a directory: " + self);
		}

		this.self = self;
	}

	private
	File siblingBase;

	public
	void setSiblingBase(File siblingBase)
	{
		this.siblingBase = siblingBase;
		this.siblingDirectories = null;
	}

	private
	String sourcePath = "src/main/java";

	public
	void setSourcePath(String sourcePath)
	{
		this.sourcePath = sourcePath;
	}

	private
	List<File> siblingDirectories;

	public
	List<File> getSiblingDirectories()
	{
		if (siblingDirectories == null)
		{
			siblingDirectories = new ArrayList<>();

			if (siblingBase != null)
			{
				for (File file : notNull(siblingBase.listFiles()))
				{
					if (file.isDirectory())
					{
						siblingDirectories.add(file);
					}
				}
			}
		}

		return siblingDirectories;
	}

	private
	File[] notNull(File[] files)
	{
		if (files == null)
		{
			return new File[0];
		}
		else
		{
			return files;
		}
	}

	private
	void maybeAddUnqualifiedClassName(String name)
	{
		//If we already know of an identifier with this simple name (from an import, or import-static), do nothing.
		if (byIdentifier.containsKey(name))
		{
			log.trace("seen: {}", name);
		}
		else if (JavaSystemClasses.getInstance().contains("java.lang." + name))
		{
			log.debug("implicit/system: {}", name);
			addReference(SYSTEM, "java.lang." + name);
		}
		else
		{
			log.debug("implicit/package: {}", name);
			addReference(PACKAGE, packageName + "." + name);
		}
	}

	@LegacyMainMethod
	public static
	void main(String[] args) throws FileNotFoundException
	{
		final
		InputStream inputStream;
		{
			if (args.length >= 1)
			{
				inputStream = new FileInputStream(args[0]);
			}
			else
			{
				inputStream = System.in;
			}
		}

		final
		OutputStream outputStream;
		{
			if (args.length >= 2)
			{
				outputStream = new FileOutputStream(args[1]);
			}
			else
			{
				outputStream = System.out;
			}
		}

		final
		JavaFileReferences javaFileReferences = new JavaFileReferences(inputStream, outputStream);

		final
		String self = System.getProperty("SELF");
		{
			if (self != null)
			{
				javaFileReferences.setSelf(new File(self));
			}
		}

		final
		String siblingBase = System.getProperty("SIBLING_BASE");
		{
			if (siblingBase != null)
			{
				javaFileReferences.setSiblingBase(new File(siblingBase));
			}
		}

		final
		String sourcePath = System.getProperty("SOURCE_PATH");
		{
			if (sourcePath != null)
			{
				javaFileReferences.setSourcePath(sourcePath);
			}
		}

		javaFileReferences.run();
	}

	/*
	Junk below this line is for testing... that the class should be able to operate on it's own
	source file.
	 */

	enum InnerEnumTesting
	{
		ALPHA,
		BETA,
		GAMMA
	}

	class InnerClassTesting
	{
		JavaFileReferences selfReference;

		void FunctionName()
		{
			Class fullyQualified = java.util.concurrent.TimeoutException.class;
			Class field = Integer.TYPE;
			Class fullyQualifiedField = java.lang.Integer.TYPE;
			String functionOfFieldName = JavaReferenceType.PACKAGE.toString();
		}
	}

}
