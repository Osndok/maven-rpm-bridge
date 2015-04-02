package com.github.osndok.mrb.grinder;

import com.github.osndok.mrb.grinder.rpm.RPM;
import com.github.osndok.mrb.grinder.rpm.RPMManifold;
import com.github.osndok.mrb.grinder.rpm.RPMRepo;
import org.apache.bcel.Constants;
import org.apache.bcel.classfile.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.module.Dependency;
import javax.module.ModuleKey;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.Deprecated;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/**
 * Created by robert on 10/30/14.
 */
public
class MavenJar
{
	private final
	File file;

	private final
	JarFile jarFile;

	private
	Map<String, String> execClassesByToolName;

	public
	Map<ModuleKey, Map<String, Set<String>>> getPluginMapping(ModuleKey moduleKey)
	{
		if (execClassesByToolName == null)
		{
			//TODO: complete the factor-out operation....
			scanModuleClasses(moduleKey);
		}

		return pluginMapping;
	}

	private final
	Map<ModuleKey, Map<String, Set<String>>> pluginMapping = new HashMap<ModuleKey, Map<String, Set<String>>>();

	private
	Set<Dependency> dependencies;

	public
	MavenJar(File file) throws IOException
	{
		this.file = file;
		this.jarFile = new JarFile(file);
	}

	public
	MavenJar(File file, MavenInfo mavenInfo) throws IOException
	{
		this.file = file;
		this.jarFile = new JarFile(file);
		this.mavenInfo = mavenInfo;
	}

	public
	File getFile()
	{
		return file;
	}

	public
	JarFile getJarFile()
	{
		return jarFile;
	}

	private
	MavenInfo mavenInfo;

	public
	MavenInfo getInfo() throws IOException
	{
		MavenInfo retval = this.mavenInfo;

		if (retval == null)
		{
			retval = this.mavenInfo = RPMManifold.getMavenInfoFromAnyRegistry(file);

			if (retval == null)
			{
				return _getInfo();
			}
		}

		return retval;
	}

	private
	MavenInfo _getInfo() throws IOException
	{
		MavenInfo retval = this.mavenInfo;

		if (retval == null)
		{
			try
			{
				if (mavenPom!=null)
				{
					retval=mavenPom.getMavenInfo();
				}
			}
			catch (Exception e)
			{
				log.error("unable to get maven info from pom file", e);
			}

			if (retval==null)
			{
				Enumeration e = jarFile.entries();

				while (e.hasMoreElements())
				{
					JarEntry je = (JarEntry) e.nextElement();
					String name = je.getName();

					if (name.endsWith("/pom.properties"))
					{
						if (retval == null)
						{
							retval = readMavenProps(jarFile.getInputStream(je));
						}
						else
						{
							throw new IllegalStateException(file + ": contains multiple pom.properties");
						}
					}
				}
			}

			if (retval == null)
			{
				throw new IllegalStateException(file + ": does not contain pom.xml or pom.properties");
			}

			this.mavenInfo = retval;
		}

		return retval;
	}

	private static
	MavenInfo readMavenProps(InputStream inputStream) throws IOException
	{
		final
		Properties p = new Properties();

		try
		{
			p.load(inputStream);
		}
		finally
		{
			inputStream.close();
		}

		return new MavenInfo(p.getProperty("groupId"), p.getProperty("artifactId"), p.getProperty("version"));
	}

	/**
	 * @return a set of main method classes and their desired CLI-tool names, which are usually prefixed \
	 *         with the module key (e.g. "alpha-v3"), unless specified otherwise (which might lead to collisions).
	 */
	public
	Map<String, String> getExecClassesByToolName(ModuleKey moduleKey)
	{
		if (execClassesByToolName == null)
		{
			//TODO: complete the factor-out operation....
			scanModuleClasses(moduleKey);
		}
		return execClassesByToolName;
	}

	private
	void scanModuleClasses(ModuleKey moduleKey)
	{
		log.debug("scanModuleClasses: {}", moduleKey);

		execClassesByToolName = new HashMap<String, String>();
		String mainClassName = getMainClassName();

		boolean hasOverride=false;

		/*
		URL url = file.toURI().toURL();

		log.debug("listing classes in: {}", url);

		//And the sad thing is... this unreadable glob is actually *much-easier* than any "standard" way of doing it...
		Reflections reflections = new Reflections(
			new ConfigurationBuilder()
				.setScanners(new SubTypesScanner(false))
				//.setScanners(new SubTypesScanner(false), new ResourcesScanner())
				//.setScanners(new TypesScanner())
				.setUrls(Collections.singleton(url))
		);

		Store store=reflections.getStore();
		for (String name : store.get("SubTypesScanner").values())
		*/

		for(JavaClass javaClass : allJavaClasses())
		{
			String name=javaClass.getClassName();
			log.trace("class name: {} ( {} / {} )", name, javaClass.getMajor(), javaClass.getMinor());
			//Multimap<String, String> multiValue = store.get(name);

			if (hasPluginAnnotation(javaClass))
			{
				log.info("is a modular plugin class... (@Plugin)");

				//TODO: support inheritance? cross module boundaries?
				//TODO: don't stride across deps, do that only once (inefficient if many plugins).

				for (String interfaceName : javaClass.getInterfaceNames())
				{
					String entryName=classEntryName(interfaceName);
					ModuleKey targetModuleKey;

					if (inThisJar(entryName))
					{
						targetModuleKey=moduleKey;
					}
					else
					{
						targetModuleKey = dependencyForClassName(entryName);
					}

					if (targetModuleKey==null)
					{
						log.debug("plugin target not found: {}", entryName);
					}
					else
					{
						log.info("plugin: {} implements {} :: {}", name, targetModuleKey, interfaceName);

						Map<String, Set<String>> implementationsByInterface = pluginMapping.get(targetModuleKey);

						if (implementationsByInterface==null)
						{
							implementationsByInterface=new HashMap<String, Set<String>>();
							pluginMapping.put(targetModuleKey, implementationsByInterface);
						}

						Set<String> implementations=implementationsByInterface.get(interfaceName);

						if (implementations==null)
						{
							implementations=new HashSet<String>();
							implementationsByInterface.put(interfaceName, implementations);
						}

						implementations.add(name);
					}
				}
			}

			/*
			Class<?> aClass;

			try
			{
				aClass= ReflectionUtils.forName(name, reflections.getConfiguration().getClassLoaders());
			}
			catch (Throwable t)
			{
				log.error("unable to load {}: {}", name, t.toString());
				continue;
			}
			*/

			boolean hasMainMethod=hasPublicStaticMainMethod(javaClass);
			String requestedCommandLineToolName = computeExplicitCommandLineToolName(javaClass, moduleKey);

			if (hasMainMethod || requestedCommandLineToolName!=null)
			{
				String className=name;//aClass.getName();
				log.info("a main class: {} ( {} / {} )", className, javaClass.getMajor(), javaClass.getMinor());

				String toolName=moduleKey.toString()+(className.equals(mainClassName)?"":"-"+getSimpleName(className));

				if (requestedCommandLineToolName==null)
				{
					if (execClassesByToolName.containsKey(toolName))
					{
						String[] segments=className.split("[\\.\\$]");
						int start=segments.length-2;

						do
						{
							StringBuilder sb=new StringBuilder(moduleKey.toString()).append('-');

							for (int i=start; i<segments.length; i++)
							{
								if (i!=start) sb.append('.');
								sb.append(segments[i]);
							}

							toolName=sb.toString();
							start--;
						}
						while (execClassesByToolName.containsKey(toolName));

						log.info("from tool-name contention: {}", toolName);
					}

					log.debug("implicit tool name: {}", toolName);
					execClassesByToolName.put(toolName, className);
				}
				else
				{
					hasOverride=true;
					requestedCommandLineToolName=replaceModuleInfoMacros(requestedCommandLineToolName, moduleKey);

					log.debug("explicit tool name: {}", requestedCommandLineToolName);
					execClassesByToolName.put(requestedCommandLineToolName, className);
				}
			}
			else
			{
				log.trace("no main class: {} / {}", name, javaClass);
			}
		}

		if (execClassesByToolName.size()==1 && !hasOverride)
		{
			//If there is only one main class in the jar, and they did not specify a cli-tool name... grant "the big one"
			execClassesByToolName.put(moduleKey.toString(), execClassesByToolName.values().iterator().next());
		}

		//TODO: fixme: this is a bit hackish...
		if (hasSysconfigResource())
		{
			execClassesByToolName.put("sysconfig", "true");
		}
	}

	private
	ModuleKey dependencyForClassName(String classEntryName)
	{
		if (dependencies==null) throw new IllegalStateException("dependencies have not yet been listed");

		for (Dependency dependency : dependencies)
		{
			try
			{
				if (dependencyHasEntry(dependency, classEntryName))
				{
					return dependency;
				}
			}
			catch (IOException e)
			{
				log.error("reading {}", dependency, e);
			}
		}

		return null;
	}

	@Deprecated
	private
	RPMRepo rpmRepo;

	private
	boolean dependencyHasEntry(Dependency dependency, String classEntryName) throws IOException
	{
		final
		RPM rpm = RPMManifold.getInstance().getAnyRpmMatching(dependency);

		if (rpm == null)
		{
			log.error("unable to locate dependency: {}", dependency);
			return false;
		}
		else
		{
			return rpm.innerJarContainsEntry(dependency, classEntryName);
		}
	}

	private
	String classEntryName(String className)
	{
		return className.replaceAll("\\.", "/") + ".class";
	}

	private
	String replaceModuleInfoMacros(String string, ModuleKey moduleKey)
	{
		string = string.replace("{m}", moduleKey.getModuleName());
		string = string.replace("{v}", moduleKey.vMajor());

		//TODO: major? minor? toString()... ???

		return string;
	}

	private
	String getSimpleName(String className)
	{
		final
		int period = className.lastIndexOf('.');

		if (className.endsWith("Main") || className.contains("Main$"))
		{
			return getSimpleName(className.substring(0, period));
		}

		final
		int end;
		{
			final
			int dollarSign = className.lastIndexOf('$');

			if (dollarSign <= 0)
			{
				end = className.length();
			}
			else
			{
				end = dollarSign;
			}
		}

		if (period > 0)
		{
			return className.substring(period + 1, end);
		}
		else
		{
			return className.substring(0, end);
		}
	}

	private
	boolean hasPluginAnnotation(JavaClass javaClass)
	{
		for (AnnotationEntry annotationEntry : javaClass.getAnnotationEntries())
		{
			log.debug("annotation type: {}", annotationEntry.getAnnotationType());

			if (annotationEntry.getAnnotationType().equals("Ljavax/module/Plugin;"))
			{
				return true;
			}

			for (ElementValuePair elementValuePair : annotationEntry.getElementValuePairs())
			{
				log.debug("evp: {} -> {}", elementValuePair.getNameString(), elementValuePair.getValue());
			}
		}

		for (Attribute attribute : javaClass.getAttributes())
		{
			log.debug("attribute: {}", attribute);
		}

		//javaClass.
		return false;
	}

	private
	boolean hasSysconfigResource()
	{
		ZipEntry entry = jarFile.getEntry("sysconfig");
		return (entry != null);
	}

	private
	String computeExplicitCommandLineToolName(JavaClass javaClass, ModuleKey moduleKey)
	{
		//The new-fangled way...
		for (AnnotationEntry annotationEntry : javaClass.getAnnotationEntries())
		{
			log.debug("annotation type: {}", annotationEntry.getAnnotationType());

			if (annotationEntry.getAnnotationType().equals("Ljavax/module/CommandLineTool;"))
			{
				return computeExplicitCommandLineToolNameFromAnnotation(javaClass, annotationEntry, moduleKey);
			}
		}

		//The original way...
		try
		{
			Field field = staticFieldNamed("JAVAX_MODULE_EXEC", javaClass);

			if (field != null)
			{
				for (Attribute attribute : field.getAttributes())
				{
					log.debug("attribute: {} -> {}", attribute.getName(), attribute);
				}

				ConstantValue constantValue = field.getConstantValue();
				ConstantPool constantPool = constantValue.getConstantPool();
				Constant constant = constantPool.getConstant(constantValue.getConstantValueIndex());
				int stringIndex = ((ConstantString) constant).getStringIndex();
				ConstantUtf8 inner = (ConstantUtf8) constantPool.getConstant(stringIndex, Constants.CONSTANT_Utf8);

				return inner.getBytes();
			}

			//Field field = javaClass.getDeclaredField("JAVAX_MODULE_EXEC");
			//field.setAccessible(true);
			//return field.get(null).toString();
		}
		catch (Exception e)
		{
			//TODO: fix null pointer exception... how can we get (even a constant) value without actually loading the class?
			log.error("can't get javax-module-exec field", e);
		}

		return null;
	}

	private
	String computeExplicitCommandLineToolNameFromAnnotation(
															   JavaClass javaClass,
															   AnnotationEntry annotationEntry,
															   ModuleKey moduleKey
	)
	{
		log.info("@CommandLineTool");

		String prefix=moduleKey.getModuleName()+'-';
		String suffix=""; //<--- TODO: this needs help, does not yet comply with contract.

		for (ElementValuePair elementValuePair : annotationEntry.getElementValuePairs())
		{
			String key=elementValuePair.getNameString();
			String value=elementValuePair.getValue().toString();

			log.debug("evp: {} -> {}", key, value);

			if (key.equals("name"))
			{
				return value;
			}
			else
			if (key.equals("prefix"))
			{
				prefix=value;
			}
			else
			if (key.equals("suffix"))
			{
				suffix=value;
			}
			else
			{
				log.warn("unknown @CommandLineTool field: {} -> {}", key, value);
			}
		}

		return prefix + moduleKey.vMajor() + suffix;
	}

	private
	Field staticFieldNamed(String fieldName, JavaClass javaClass)
	{
		for (Field field : javaClass.getFields())
		{
			if (field.isStatic() && field.getName().equals(fieldName))
			{
				return field;
			}
		}

		return null;
	}

	private
	boolean hasPublicStaticMainMethod(JavaClass aClass)
	{
		try
		{
			org.apache.bcel.classfile.Method method = publicStaticMethod(aClass, "main");
			//Method main = aClass.getMethod("main", String[].class);
			//return Modifier.isStatic(main.getModifiers()) && Modifier.isPublic(main.getModifiers());
			return (method != null);
		}
		/*
		catch (NoSuchMethodException e)
		{
			return false;
		}
		*/
		catch (Throwable t)
		{
			//TODO: *often* a module will have methods with missing parameter/return types, and the getDeclaredMethods throws, even though we only ask for the one type... workaround: if you want your main functions exposed as CLI objects, put your main methods in 'safe' classes.
			log.warn("can't inspect class: {}", t.toString());
			return false;
		}
	}

	private
	Method publicStaticMethod(JavaClass javaClass, String methodName)
	{
		for (Method method : javaClass.getMethods())
		{
			if (method.isStatic() && method.isPublic() && method.getName().equals(methodName))
			{
				return method;
			}
		}

		return null;
	}

	private
	String mainClassName;

	private
	String getMainClassName()
	{
		if (mainClassName == null)
		{
			try
			{
				Manifest manifest = jarFile.getManifest();

				if (manifest == null)
				{
					mainClassName = "dne; do not match any class"; //fix me, if thisb ecomes a public method...
					return mainClassName;
				}

				Attributes mainAttributes = manifest.getMainAttributes();
				mainClassName = mainAttributes.getValue("Main-Class");

				if (mainClassName == null)
				{
					mainClassName = "dne; do not match any class"; //fix me, if thisb ecomes a public method...
				}
			}
			catch (Exception e)
			{
				log.error("unable to get jar's main-class for {}", this, e);
				mainClassName = e.toString(); //fix me, if this becomes a public method...
			}
		}
		return mainClassName;
	}

	private static final Logger log = LoggerFactory.getLogger(MavenJar.class);

	public
	Set<Dependency> listRpmDependencies(
										   ModuleKey moduleKey,
										   Main main
	) throws DependencyNotProcessedException, IOException, ParserConfigurationException, SAXException
	{
		log.debug("listRpmDependencies: {}", moduleKey);

		final
		Set<Dependency> declaredDependencies = new HashSet<Dependency>();

		//TODO: initially populate declared dependencies using module-native deps file?

		Set<MavenInfo> mavenInfos;
		{
			try
			{
				mavenInfos = getMavenPom().getDependencies();
			}
			catch (JarHasNoPomException e)
			{
				log.error("no pom file in jar", e);
				return declaredDependencies;
			}
		}

		for (MavenInfo info : mavenInfos)
		{
			final
			RPMRepo rpmRepo = RPMManifold.getRepoFor(info);

			if (main.looksLikeSunTools(info))
			{
				declaredDependencies.add(main.getSunTools().asDependencyOf(moduleKey));
				continue;
			}

			try
			{
				Dependency dependency = rpmRepo.getFullModuleDependency(moduleKey, info);
				log.debug("rpmRepo.getFullModuleDependency({}, {}) -> {}", moduleKey, info, dependency);
				declaredDependencies.add(dependency);
			}
			catch (IOException e)
			{
				if (info.isOptional())
				{
					log.error("skipping optional maven dependency: {}", info, e);
				}
				else
				{
					throw e;
				}
			}
		}

		final
		Set<Dependency> retval = new HashSet<Dependency>(declaredDependencies);

		//TODO: combine this with the scanModuleClasses above...
		/*
		Maven allows for implicit transitive dependencies. While convenient for coding, they are quite
		sloppy when modularizing. Therefore, we must (at least) make a best-effort check to ensure that
		our jar has all the *ACTUAL* dependencies that it needs, otherwise there will be CNF thrown at
		runtime.
		 */
		for (JavaClass javaClass : allJavaClasses())
		{
			ConstantPool constantPool = javaClass.getConstantPool();
			int l = constantPool.getLength();

			for (int i = 0; i < l; i++)
			{
				Constant constant = constantPool.getConstant(i);

				if (constant instanceof ConstantClass)
				{
					ConstantClass cClass = (ConstantClass) constant;
					String className = constantPool.constantToString(constantPool.getConstant(cClass.getNameIndex()));
					log.trace("parsed {} -> {}", cClass, className);

					if (className.charAt(0) == '[')
					{
						//it's an array type... don't bother.
					}
					else
					{
						ensurePossiblyTransitiveDependencyIsIncluded(className + ".class", declaredDependencies,
																		rpmRepo, retval);
					}
				}
			}
		}

		dependencies = retval;

		return retval;
	}

	/**
	 * @url http://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Transitive_Dependencies
	 */
	private
	void ensurePossiblyTransitiveDependencyIsIncluded(
														 String classEntryName,
														 Set<Dependency> declaredDependencies,
														 RPMRepo rpmRepo,
														 Set<Dependency> actualDependencies
	) throws IOException
	{
		if (isSystemClass(classEntryName))
		{
			log.trace("system: {}", classEntryName);
		}
		else if (inThisJar(classEntryName))
		{
			log.trace("in-jar: {}", classEntryName);
		}
		else
		{
			if (transitiveDependenciesByEntryName == null)
			{
				log.info("indexing transitive dependencies...");

				directDependenciesByEntryName = new HashMap<String, Dependency>();
				transitiveDependenciesByEntryName=new HashMap<String, Dependency>();

				for (Dependency dependency : declaredDependencies)
				{
					RPM rpm = RPMManifold.getInstance().getAnyRpmMatching(dependency);

					rpm.dumpInnerJarClassEntries(dependency, directDependenciesByEntryName);

					//To be nice, we go one level deep... beyond that, and you are on your own!
					for (Dependency transitive : rpm.listModuleDependencies(dependency))
					{
						final
						RPM requiredRpm=RPMManifold.getInstance().getAnyRpmMatching(transitive);

						requiredRpm.dumpInnerJarClassEntries(transitive, transitiveDependenciesByEntryName);
					}
				}
			}

			Dependency dependency=directDependenciesByEntryName.get(classEntryName);

			if (dependency!=null)
			{
				log.trace("in-dep: {}: {}", dependency, classEntryName);
				return;
			}

			dependency=transitiveDependenciesByEntryName.get(classEntryName);

			if (dependency==null)
			{
				//We don't make this fatal, because there *are* ways to use undeclared classes via reflection and whatnot.
				log.error("{} not found in dependencies, this module may therefore be broken", classEntryName);
			}
			else
			{
				log.warn("use of {} implies transitive dependency: {}", classEntryName, dependency);
				actualDependencies.add(dependency);
			}
		}
	}

	private
	Map<String,Dependency> directDependenciesByEntryName;

	private
	Map<String,Dependency> transitiveDependenciesByEntryName;

	private
	boolean isSystemClass(String classEntryName)
	{
		//TODO: there are probably more system packages now... but our classpath is probably too clouded to check directly.
		return classEntryName.startsWith("java/");
	}

	private
	boolean inThisJar(String classEntryName)
	{
		ZipEntry entry = jarFile.getEntry(classEntryName);
		return (entry!=null);
	}

	private
	Iterable<JavaClass> allJavaClasses()
	{
		final
		Map<String,JarEntry> entriesByNames=new HashMap<String, JarEntry>();

		Enumeration e = jarFile.entries();

		while (e.hasMoreElements())
		{
			JarEntry je = (JarEntry) e.nextElement();
			String name = je.getName();

			if (name.endsWith(".class"))
			{
				entriesByNames.put(name, je);
			}
		}

		final
		Iterator<Map.Entry<String, JarEntry>> nameIterator=entriesByNames.entrySet().iterator();

		final
		Iterator<JavaClass> metaIterator=new Iterator<JavaClass>()
		{
			@Override
			public
			boolean hasNext()
			{
				return nameIterator.hasNext();
			}

			@Override
			public
			JavaClass next()
			{
				Map.Entry<String, JarEntry> me=nameIterator.next();
				String name=me.getKey();
				JarEntry jarEntry=me.getValue();
				log.debug("parse class: {}", name);
				try
				{
					InputStream inputStream = jarFile.getInputStream(jarEntry);
					return new ClassParser(inputStream, file.getName()).parse();
				}
				catch (IOException e)
				{
					throw new RuntimeException("cannot parse class file", e);
				}
			}

			@Override
			public
			void remove()
			{
				throw new UnsupportedOperationException();
			}
		};

		//NB: this iterable can only be iterated once per function call.
		return new Iterable<JavaClass>()
		{
			@Override
			public
			Iterator<JavaClass> iterator()
			{
				return metaIterator;
			}
		};
	}

	public
	String getDescription() throws IOException, JarHasNoPomException
	{
		return getMavenPom().getDescription();
	}

	private
	MavenPom mavenPom;

	public
	MavenPom getMavenPom() throws IOException, JarHasNoPomException
	{
		if (mavenPom==null)
		{
			try
			{
				MavenInfo mavenInfo=getInfo();
				log.debug("creating mavenPom for {} / {}", mavenInfo, file);
				mavenPom=new MavenPom(mavenInfo, pomXmlInputStream());
			}
			catch (ParserConfigurationException e)
			{
				throw new IOException(e);
			}
			catch (SAXException e)
			{
				throw new IOException(e);
			}
		}

		return mavenPom;
	}

	public
	void setMavenPom(MavenPom mavenPom)
	{
		this.mavenPom=mavenPom;
	}

	private
	InputStream pomXmlInputStream() throws IOException, JarHasNoPomException
	{
		Enumeration e = jarFile.entries();

		while (e.hasMoreElements())
		{
			JarEntry je = (JarEntry) e.nextElement();
			String name = je.getName();

			if (name.endsWith("/pom.xml"))
			{
				return jarFile.getInputStream(je);
			}
		}

		throw new JarHasNoPomException(file);
	}

	public
	void appendSysconfig(StringBuilder sb) throws IOException
	{
		InputStream in = jarFile.getInputStream(jarFile.getEntry("sysconfig"));
		try
		{
			//TODO: forbid "EOF" line
			int i;
			while ((i=in.read())>0)
			{
				sb.append((char)i);
			}
		}
		finally
		{
			in.close();
		}
	}

	public
	RPMRepo getRpmRepo() throws IOException
	{
		if (rpmRepo==null)
		{
			rpmRepo=RPMManifold.getRepoFor(getInfo());
		}

		return rpmRepo;
	}

	@Deprecated
	public
	void setRpmRepo(RPMRepo rpmRepo)
	{
		this.rpmRepo = rpmRepo;
	}
}
