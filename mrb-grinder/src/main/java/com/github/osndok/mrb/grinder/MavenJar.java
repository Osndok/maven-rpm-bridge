package com.github.osndok.mrb.grinder;

import com.github.osndok.mrb.grinder.aether.ConsoleRepositoryListener;
import com.github.osndok.mrb.grinder.aether.ConsoleTransferListener;
import com.github.osndok.mrb.grinder.aether.ManualRepositorySystemFactory;
import org.apache.bcel.Constants;
import org.apache.bcel.classfile.*;
import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.repository.LocalRepository;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.resolution.ArtifactDescriptorException;
import org.sonatype.aether.resolution.ArtifactDescriptorRequest;
import org.sonatype.aether.resolution.ArtifactDescriptorResult;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.module.Dependency;
import javax.module.ModuleKey;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
	MavenInfo getInfo(Registry r) throws IOException
	{
		MavenInfo retval = this.mavenInfo;

		if (retval == null)
		{
			retval = this.mavenInfo = r.getMavenInfoOverrideForJarName(file.getName());

			if (retval == null)
			{
				return getInfo();
			}
		}

		return retval;
	}

	public
	MavenInfo getInfo() throws IOException
	{
		MavenInfo retval = this.mavenInfo;

		if (retval == null)
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

			if (retval == null)
			{
				throw new IllegalStateException(file + ": does not contain pom.properties");
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
				log.info("is a modular plugin class...");

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
			String requestedCommandLineToolName= staticJavaXModuleExecField(javaClass);

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

					execClassesByToolName.put(toolName, className);
				}
				else
				{
					hasOverride=true;
					requestedCommandLineToolName=replaceModuleInfoMacros(requestedCommandLineToolName, moduleKey);
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

	private
	RPMRepo rpmRepo;

	private
	boolean dependencyHasEntry(Dependency dependency, String classEntryName) throws IOException
	{
		if (rpmRepo==null) throw new IllegalStateException("rpmRepo is null");

		final
		RPM rpm=rpmRepo.get(dependency);

		if (rpm==null)
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
		return className.replaceAll("\\.", "/")+".class";
	}

	private
	String replaceModuleInfoMacros(String string, ModuleKey moduleKey)
	{
		string=string.replace("{m}", moduleKey.getModuleName());
		string=string.replace("{v}", moduleKey.vMajor());

		//TODO: major? minor? toString()... ???

		return string;
	}

	private
	String getSimpleName(String className)
	{
		final
		int period=className.lastIndexOf('.');

		if (className.endsWith("Main") || className.contains("Main$"))
		{
			return getSimpleName(className.substring(0,  period));
		}

		final
		int end;
		{
			final
			int dollarSign=className.lastIndexOf('$');

			if (dollarSign<=0)
			{
				end=className.length();
			}
			else
			{
				end=dollarSign;
			}
		}

		if (period>0)
		{
			return className.substring(period+1, end);
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
		return (entry!=null);
	}

	private
	String staticJavaXModuleExecField(JavaClass javaClass)
	{
		try
		{
			Field field = staticFieldNamed("JAVAX_MODULE_EXEC", javaClass);

			if (field!=null)
			{
				for (Attribute attribute : field.getAttributes())
				{
					log.debug("attribute: {} -> {}", attribute.getName(), attribute);
				}

				ConstantValue constantValue = field.getConstantValue();
				ConstantPool constantPool = constantValue.getConstantPool();
				Constant constant = constantPool.getConstant(constantValue.getConstantValueIndex());
				int stringIndex=((ConstantString)constant).getStringIndex();
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
			org.apache.bcel.classfile.Method method=publicStaticMethod(aClass, "main");
			//Method main = aClass.getMethod("main", String[].class);
			//return Modifier.isStatic(main.getModifiers()) && Modifier.isPublic(main.getModifiers());
			return (method!=null);
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
		if (mainClassName==null)
		{
			try
			{
				Manifest manifest = jarFile.getManifest();

				if (manifest==null)
				{
					mainClassName="dne; do not match any class"; //fix me, if thisb ecomes a public method...
					return mainClassName;
				}

				Attributes mainAttributes = manifest.getMainAttributes();
				mainClassName = mainAttributes.getValue("Main-Class");

				if (mainClassName==null)
				{
					mainClassName="dne; do not match any class"; //fix me, if thisb ecomes a public method...
				}
			}
			catch (Exception e)
			{
				log.error("unable to get jar's main-class for {}", this, e);
				mainClassName=e.toString(); //fix me, if this becomes a public method...
			}
		}
		return mainClassName;
	}

	private static final Logger log = LoggerFactory.getLogger(MavenJar.class);

	public
	Set<Dependency> listRpmDependencies(ModuleKey moduleKey, RPMRepo rpmRepo) throws DependencyNotProcessedException, IOException, ParserConfigurationException, SAXException
	{
		log.debug("listRpmDependencies: {}", moduleKey);

		this.rpmRepo=rpmRepo;

		final
		Set<Dependency> declaredDependencies=new HashSet<Dependency>();

		for (MavenInfo info : listMavenDependenciesFromPomXml())
		{
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
		Set<Dependency> retval=new HashSet<Dependency>(declaredDependencies);

		//TODO: combine this with the scanModuleClasses above...
		/*
		Maven allows for implicit transitive dependencies. While convenient for coding, they are quite
		sloppy when modularizing. Therefore, we must (at least) make a best-effort check to ensure that
		our jar has all the *ACTUAL* dependencies that it needs, otherwise there will be CNF thrown at
		runtime.
		 */
		for(JavaClass javaClass : allJavaClasses())
		{
			ConstantPool constantPool = javaClass.getConstantPool();
			int l=constantPool.getLength();

			for (int i=0; i<l; i++)
			{
				Constant constant = constantPool.getConstant(i);

				if (constant instanceof ConstantClass)
				{
					ConstantClass cClass=(ConstantClass)constant;
					String className=constantPool.constantToString(constantPool.getConstant(cClass.getNameIndex()));
					log.trace("parsed {} -> {}", cClass, className);

					if (className.charAt(0)=='[')
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

		dependencies=retval;

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
		else
		if (inThisJar(classEntryName))
		{
			log.trace("in-jar: {}", classEntryName);
		}
		else
		{
			if (transitiveDependenciesByEntryName==null)
			{
				directDependenciesByEntryName=new HashMap<String, Dependency>();
				transitiveDependenciesByEntryName=new HashMap<String, Dependency>();

				for (Dependency dependency : declaredDependencies)
				{
					RPM rpm = rpmRepo.get(dependency);

					rpm.dumpInnerJarClassEntries(dependency, directDependenciesByEntryName);

					//To be nice, we go one level deep... beyond that, and you are on your own!
					for (Dependency transitive : rpm.listModuleDependencies(dependency))
					{
						rpmRepo.get(transitive).dumpInnerJarClassEntries(transitive, transitiveDependenciesByEntryName);
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

	private transient
	String description;

	public
	String kludge_getDescription_onlyAfterListingDependencies()
	{
		return description;
	}

	private
	Set<MavenInfo> listMavenDependenciesFromPomXml() throws ParserConfigurationException, SAXException, IOException
	{
		Document pom = getPomXmlDom();

		/*
		Without a pom.xml, we have to way of determining what a jar's dependencies are.
		However... some (particularly low-level) apis simply don't have deps, or are built
		from the ant tool, and injected manually into the maven repo.

		TODO: is there a way to get a pom.xml for a jar given only it's MavenInfo? It's worth trying.

		 */
		if (pom==null)
		{
			log.warn("could not find embedded pom.xml in: {}",file);
			return Collections.emptySet();
		}

		pom.getDocumentElement().normalize();

		Node description = pom.getElementsByTagName("description").item(0);

		if (description==null)
		{
			log.debug("no description");
		}
		else
		{
			//TODO: if a multiline description, trim each line to avoid indentation issues.
			this.description=description.getTextContent().trim();
			log.debug("description: {}", this.description);
		}

		Node depsGroup = pom.getElementsByTagName("dependencies").item(0);

		if (!(depsGroup instanceof Element))
		{
			//usually null...
			log.info("dependencies is not an element: {}", depsGroup);
			return Collections.emptySet();
		}

		NodeList dependencies = ((Element)depsGroup).getElementsByTagName("dependency");

		int l=dependencies.getLength();

		String context=file.getName()+"::pom.xml";

		try
		{
			final
			Set<MavenInfo> retval = new HashSet<MavenInfo>();

			for (int i = 0; i < l; i++)
			{
				Element e = (Element) dependencies.item(i);
				String scope=getScope(e);
				boolean optional=isTestOrProvidedScope(scope);

				MavenInfo mavenInfo = parseMavenDependency(context, e, optional);

				if (scope.equals("test"))
				{
					log.debug("ignoring test scope: {}", mavenInfo);
				}
				else
				{
					retval.add(mavenInfo);
				}
			}

			return retval;
		}
		catch (AetherRequiredException e)
		{
			if (log.isInfoEnabled())
			{
				log.info("moving to aether backup plan: {}", e.toString());
			}

			RepositorySystem system = ManualRepositorySystemFactory.newRepositorySystem();

			RepositorySystemSession session = newRepositorySystemSession(system);

			Artifact artifact = new DefaultArtifact( getInfo().toString() );

			RemoteRepository repo = new RemoteRepository( "central", "default", "http://repo1.maven.org/maven2/" );

			ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
			descriptorRequest.setArtifact( artifact );
			descriptorRequest.addRepository( repo );

			ArtifactDescriptorResult descriptorResult;

			try
			{
				descriptorResult=system.readArtifactDescriptor( session, descriptorRequest );
			}
			catch (ArtifactDescriptorException e1)
			{
				throw new IOException("unable to read artifact descriptor", e1);
			}

			final
			Set<MavenInfo> retval = new HashSet<MavenInfo>();

			for ( org.sonatype.aether.graph.Dependency dependency : descriptorResult.getDependencies() )
			{
				System.out.println( dependency );
				String scope=dependency.getScope();
				Artifact aetherArtifact = dependency.getArtifact();

				if (!isTestOrProvidedScope(scope))
				{
					retval.add(new MavenInfo(aetherArtifact.getGroupId(), aetherArtifact.getArtifactId(), aetherArtifact.getVersion(), dependency.isOptional()));
				}
			}

			return retval;
		}
	}

	private
	RepositorySystemSession newRepositorySystemSession(RepositorySystem system)
	{
		MavenRepositorySystemSession session = new MavenRepositorySystemSession();
		LocalRepository localRepo = new LocalRepository( "target/local-repo" );
		session.setLocalRepositoryManager( system.newLocalRepositoryManager( localRepo ) );
		session.setTransferListener( new ConsoleTransferListener() );
		session.setRepositoryListener( new ConsoleRepositoryListener() );
		// uncomment to generate dirty trees
		// session.setDependencyGraphTransformer( null );
		return session;
	}

	private
	MavenInfo parseMavenDependency(String context, Element dep, boolean optional) throws AetherRequiredException
	{
		String artifactId= getPomTag(context, dep, "artifactId");
		context+=" dependency/artifact '"+artifactId+"'";
		String groupId= getPomTag(context, dep, "groupId");
		String version= getPomTag(context, dep, "version");

		Node optionalTag=dep.getElementsByTagName("optional").item(0);

		if (optionalTag!=null && optionalTag.getTextContent().equals("true"))
		{
			//TODO: maybe the logic would be clearer if we test the scope in here, and conditionally add it to the list in here?
			optional=true;
		}

		return new MavenInfo(groupId, artifactId, version, optional);
	}

	private
	String getPomTag(String context, Element element, String tagName) throws AetherRequiredException
	{
		NodeList nodeList = element.getElementsByTagName(tagName);

		Node node = nodeList.item(0);
		if (node==null)
		{
			throw new AetherRequiredException(context+" does not have a "+tagName+" tag");
		}

		String retval=node.getTextContent();
		if (retval.contains("{"))
		{
			throw new AetherRequiredException(context+" '"+tagName+"' tag contains a macro expansion");
		}

		return retval;
	}

	private
	String getScope(Element dep)
	{
		NodeList list = dep.getElementsByTagName("scope");

		if (list.getLength()>=1)
		{
			String scope=list.item(0).getTextContent().toLowerCase();

			//return isTestOrProvidedScope(scope);
			return scope;
		}
		else
		{
			//BUG? Dependency scope might be declarable in a parent pom...
			//Is it worth the trouble to support?
			//I suppose that it doesn't hurt TOO much to have the test harness at runtime.
			log.debug("no scope");
			return "compile";
		}

		//return false;
	}

	private
	boolean isTestOrProvidedScope(String scope)
	{
		return scope!=null && (scope.equals("test") || scope.equals("provided"));
	}

	private
	Document getPomXmlDom() throws IOException, ParserConfigurationException, SAXException
	{
		Enumeration e = jarFile.entries();

		while (e.hasMoreElements())
		{
			JarEntry je = (JarEntry) e.nextElement();
			String name = je.getName();

			if (name.endsWith("/pom.xml"))
			{
				return domFromInputStream(jarFile.getInputStream(je));
			}
		}

		return null;
	}

	private
	Document domFromInputStream(InputStream inputStream) throws ParserConfigurationException, IOException, SAXException
	{
		return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputStream);
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
	void setRpmRepo(RPMRepo rpmRepo)
	{
		this.rpmRepo = rpmRepo;
	}
}
