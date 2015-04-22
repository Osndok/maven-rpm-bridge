package com.github.osndok.mrb.runner;

import java.beans.MethodDescriptor;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.module.LegacyMainMethod;

import static com.github.osndok.mrb.runner.JavaReferenceType.PACKAGE;
import static com.github.osndok.mrb.runner.JavaReferenceType.SELF;
import static com.github.osndok.mrb.runner.JavaReferenceType.SIBLING;
import static com.github.osndok.mrb.runner.JavaReferenceType.STATIC;
import static com.github.osndok.mrb.runner.JavaReferenceType.SYSTEM;
import static com.github.osndok.mrb.runner.JavaReferenceType.LIBRARY;

/**
 * Given a special java file as input (that conforms to the prescribed *subset* of the
 * java language) and a "known" list of system classes, generate a usable list of all
 * classes and libraries (by package) that this java file depends on.
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
	String packageName;

	private
	void run2() throws Exception
	{
		final
		JavaSystemClasses javaSystemClasses=JavaSystemClasses.getInstance();

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
				throw new UnsupportedJavaGrammar("asterisk/wildcard imports are not supported: '"+name+"'");
			}

			log.debug("import: {}", name);

			if (importDeclaration.isStatic())
			{
				addReference(STATIC, name);
			}
			else
			if (javaSystemClasses.contains(name))
			{
				addReference(SYSTEM, name);
			}
			else
			{
				addFullyQualifiedClassName(name);
			}
		}

		for (TypeDeclaration typeDeclaration : notNull(cu.getTypes()))
		{
			scanTypeDeclaration(typeDeclaration);
		}

		if (outputStream!=null)
		{
			printTo(outputStream);
		}

		//Last, so that an exception will not cause a confusing empty set to be returned on a subsequent call.
		hasRun = true;
	}

	private
	void printTo(OutputStream outputStream)
	{
		final
		PrintStream out=new PrintStream(outputStream);

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
		if (name.startsWith(packageName))
		{
			addReference(PACKAGE, name);
		}
		else
		{
			addReference(LIBRARY, name);
		}
	}

	private <M>
	Collection<M> notNull(Collection<M> c)
	{
		if (c==null)
		{
			return Collections.emptyList();
		}
		else
		{
			return c;
		}
	}

	private
	String classNameToJavaFileSubPath(String packageAndClassName)
	{
		return packageAndClassName.replaceAll("\\.", "/")+".java";
	}

	private
	boolean hasMatchingSelfFile(String subPath)
	{
		if (self==null)
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
		File file=new File(directory, subPath);

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
			if (exists(directory, subPath))
			{
				return true;
			}
		}

		return false;
	}

	private
	void addReference(JavaReferenceType javaReferenceType, String packageAndClass)
	{
		log.debug("add: {} / {}", javaReferenceType, packageAndClass);

		final
		JavaReference javaReference=new JavaReference(javaReferenceType, packageAndClass);

		references.add(javaReference);
		byIdentifier.put(javaReference.getClassName(), javaReference);
	}

	private
	File self;

	public
	void setSelf(File self)
	{
		if (!self.isDirectory())
		{
			throw new IllegalArgumentException("not a directory: "+self);
		}

		this.self=self;
	}

	private
	File siblingBase;

	public
	void setSiblingBase(File siblingBase)
	{
		this.siblingBase=siblingBase;
		this.siblingDirectories=null;
	}

	private
	String sourcePath="src/main/java";

	public
	void setSourcePath(String sourcePath)
	{
		this.sourcePath=sourcePath;
	}

	private
	List<File> siblingDirectories;

	public
	List<File> getSiblingDirectories()
	{
		if (siblingDirectories==null)
		{
			siblingDirectories=new ArrayList<>();

			if (siblingBase!=null)
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
		if (files==null)
		{
			return new File[0];
		}
		else
		{
			return files;
		}
	}

	/**
	 * Reads the body of a TypeDeclaration (which can be a class, enum, interface, etc) for
	 * references to other classes/types.
	 *
	 * @param typeDeclaration
	 */
	private
	void scanTypeDeclaration(TypeDeclaration typeDeclaration)
	{
		log.debug("scan: {} / {}", typeDeclaration.getClass(), typeDeclaration.getName());

		scanAnnotations(typeDeclaration.getAnnotations());

		for (BodyDeclaration bodyDeclaration : typeDeclaration.getMembers())
		{
			log.trace("member: {}", bodyDeclaration.getClass());
			scanAnnotations(bodyDeclaration.getAnnotations());

			if (bodyDeclaration instanceof FieldDeclaration)
			{
				scanFieldDeclaration((FieldDeclaration)bodyDeclaration);
			}
			else
			if (bodyDeclaration instanceof MethodDeclaration)
			{
				scanMethodDeclaration((MethodDeclaration)bodyDeclaration);
			}
			else
			if (bodyDeclaration instanceof TypeDeclaration)
			{
				log.debug("recurse");
				scanTypeDeclaration((TypeDeclaration)bodyDeclaration);
			}
		}
	}

	private
	void scanFieldDeclaration(FieldDeclaration bodyDeclaration)
	{
		//TODO: fieldType
		//TODO: types used in initializer
	}

	private
	void scanMethodDeclaration(MethodDeclaration bodyDeclaration)
	{
		//TODO: xxx
	}

	private
	void scanAnnotations(List<AnnotationExpr> annotations)
	{
		for (AnnotationExpr annotationExpr : annotations)
		{
			final
			String name=annotationExpr.getName().getName();

			if (name.indexOf('.') > 0)
			{
				log.debug("annotation (with package): {}", name);
				addFullyQualifiedClassName(name);
			}
			else
			if (isPrimitiveAnnotation(name))
			{
				log.debug("annotation (built-in / primitive): {}", name);
			}
			else
			{
				log.debug("annotation (identifier): {}", name);
				maybeAddUnqualifiedClassName(name);
			}

			for (Node node : annotationExpr.getChildrenNodes())
			{
				log.warn("unhandled annotation-child: {}", node);
				//TODO: handle types found in annotation arguments... right here?
			}
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
		else
		if (JavaSystemClasses.getInstance().contains("java.lang."+name))
		{
			log.debug("implicit/system: {}", name);
			addReference(SYSTEM, "java.lang."+name);
		}
		else
		{
			log.debug("implicit/package: {}", name);
			addReference(PACKAGE, packageName+"."+name);
		}
	}

	private
	boolean isPrimitiveAnnotation(String name)
	{
		/*
		http://en.wikipedia.org/wiki/Java_annotation#Built-in_annotations
		 */
		return name.equals("Override")
			|| name.equals("Deprecated")
			|| name.equals("SuppressWarnings")
			|| name.equals("SafeVarargs")
			|| name.equals("FunctionalInterface")
			|| name.equals("Retention")
			|| name.equals("Documented")
			|| name.equals("Target")
			|| name.equals("Inherited")
			|| name.equals("Repeatable")
			;
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
				inputStream=new FileInputStream(args[0]);
			}
			else
			{
				inputStream=System.in;
			}
		}

		final
		OutputStream outputStream;
		{
			if (args.length >= 2)
			{
				outputStream=new FileOutputStream(args[1]);
			}
			else
			{
				outputStream=System.out;
			}
		}

		final
		JavaFileReferences javaFileReferences=new JavaFileReferences(inputStream, outputStream);

		final
		String self=System.getProperty("SELF");
		{
			if (self!=null)
			{
				javaFileReferences.setSelf(new File(self));
			}
		}

		final
		String siblingBase=System.getProperty("SIBLING_BASE");
		{
			if (siblingBase!=null)
			{
				javaFileReferences.setSiblingBase(new File(siblingBase));
			}
		}

		final
		String sourcePath=System.getProperty("SOURCE_PATH");
		{
			if (sourcePath!=null)
			{
				javaFileReferences.setSourcePath(sourcePath);
			}
		}

		javaFileReferences.run();
	}
}
