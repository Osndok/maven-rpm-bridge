package com.github.osndok.mrb.grinder;

import com.github.osndok.mrb.grinder.aether.ConsoleRepositoryListener;
import com.github.osndok.mrb.grinder.aether.ConsoleTransferListener;
import com.github.osndok.mrb.grinder.aether.ManualRepositorySystemFactory;
import org.apache.bcel.classfile.*;
import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.reflections.ReflectionUtils;
import org.reflections.Reflections;
import org.reflections.Store;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ConfigurationBuilder;
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

import javax.module.Version;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.module.Dependency;
import javax.module.ModuleKey;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
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

			if (retval==null)
			{
				throw new IllegalStateException(file+": does not contain pom.properties");
			}

			this.mavenInfo=retval;
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
	Map<String, String> getExecClassesByToolName(ModuleKey moduleKey) throws MalformedURLException
	{
		Map<String,String> retval=new HashMap<String, String>();
		String mainClassName=getMainClassName();

		URL url=file.toURI().toURL();

		log.debug("listing classes in: {}", url);

		//And the sad thing is... this unreadable glob is actually *much-easier* than any "standard" way of doing it...
		Reflections reflections = new Reflections(
			new ConfigurationBuilder()
				.setScanners(new SubTypesScanner(false))
				//.setScanners(new SubTypesScanner(false), new ResourcesScanner())
				//.setScanners(new TypesScanner())
				.setUrls(Collections.singleton(url))
		);

		boolean hasOverride=false;

		Store store=reflections.getStore();
		//Collection<Class<?>> classes=reflections.getSubTypesOf(Object.class);
		//Set<String> jarContents = store.keySet();
		//log.debug("found {} classes & resources", jarContents.size());

		//for (Class<?> aClass : classes)
		//for (Map.Entry<String, String> me : store.get("SubTypesScanner").entries())
		//for (Map.Entry<String, String> me : store.get("TypesScanner").entries())
		//for (String name : reflections.getAllTypes())
		for (String name : store.get("SubTypesScanner").values())
		{
			log.trace("class name: {}", name);
			//Multimap<String, String> multiValue = store.get(name);

			/*
			for (Map.Entry<String, String> me : multiValue.entries())
			{
				String key=me.getKey();
				String value=me.getValue();
				log.debug("{} -> {}", key, value);
			}
			*/
			//TODO: initializing a class might throw an exception, or run static code (e.g. JDBC driver installation)
			Class<?> aClass;

			try
			{
				aClass=ReflectionUtils.forName(name, reflections.getConfiguration().getClassLoaders());
			}
			catch (Throwable t)
			{
				log.error("unable to load {}: {}", name, t.toString());
				continue;
			}

			if (hasPublicStaticMainMethod(aClass))
			{
				String className=aClass.getName();
				log.info("has main class: {}", className);

				String toolName=moduleKey.toString()+(className.equals(mainClassName)?"":"-"+aClass.getSimpleName());

				String override=staticJavaXModuleField(aClass);

				if (override==null)
				{
					if (retval.containsKey(toolName))
					{
						String[] segments=className.split("\\.");
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
						while (retval.containsKey(toolName));

						log.info("from tool-name contention: {}", toolName);
					}

					retval.put(toolName, className);
				}
				else
				{
					hasOverride=true;
					retval.put(override, className);
				}
			}
			else
			{
				log.trace("no main class: {}", aClass);
			}
		}

		if (retval.size()==1 && !hasOverride)
		{
			//If there is only one main class in the jar, and they did not specify a cli-tool name... grant "the big one"
			retval.put(retval.keySet().iterator().next(), moduleKey.toString());
		}

		//TODO: fixme: this is a bit hackish...
		if (hasSysconfigResource())
		{
			retval.put("sysconfig", "true");
		}

		return retval;
	}

	private
	boolean hasSysconfigResource()
	{
		ZipEntry entry = jarFile.getEntry("sysconfig");
		return (entry!=null);
	}

	private
	String staticJavaXModuleField(Class<?> aClass)
	{
		try
		{
			Field field = aClass.getDeclaredField("JAVAX_MODULE_EXEC");
			field.setAccessible(true);
			return field.get(null).toString();
		}
		catch (Exception e)
		{
			if (log.isDebugEnabled())
			{
				log.debug("can't get javax-module-exec field: {}", e.toString());
			}
			return null;
		}
	}

	private
	boolean hasPublicStaticMainMethod(Class<?> aClass)
	{
		try
		{
			Method main = aClass.getMethod("main", String[].class);
			return Modifier.isStatic(main.getModifiers()) && Modifier.isPublic(main.getModifiers());
		}
		catch (NoSuchMethodException e)
		{
			return false;
		}
		catch (Throwable t)
		{
			//TODO: *often* a module will have methods with missing parameter/return types, and the getDeclaredMethods throws, even though we only ask for the one type... workaround: if you want your main functions exposed as CLI objects, put your main methods in 'safe' classes.
			log.warn("can't inspect class: {}", t.toString());
			return false;
		}
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
					throw new IOException();
				}
			}
		}

		final
		Set<Dependency> retval=new HashSet<Dependency>(declaredDependencies);

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
					log.debug("parsed {} -> {}", cClass, className);

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

		//Everything but the module loader requires the module loader at install time (in order to run).
		if (!moduleKey.getModuleName().equals("javax-module"))
		{
			retval.add(Version.JAVAX_MODULE.asDependencyOf(moduleKey));
		}

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
			for (Dependency dependency : declaredDependencies)
			{
				RPM rpm=rpmRepo.get(dependency);

				if (rpm.innerJarContainsEntry(dependency, classEntryName))
				{
					log.trace("in-dep: {}", classEntryName);
					return;
				}

				//To be nice, we go one level deep... beyond that, and you are on your own!
				for (Dependency transitive:rpm.listModuleDependencies(dependency))
				{
					if (rpmRepo.get(transitive).innerJarContainsEntry(transitive, classEntryName))
					{
						log.warn("use of {} implies transitive dependency: {}", transitive);
						actualDependencies.add(transitive);
					}
				}
			}

			//We don't make this fatal, because there *are* ways to use undeclared classes via reflection and whatnot.
			log.error("{} not found in dependencies, this module may therefore be broken", classEntryName);
		}
	}

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

				MavenInfo mavenInfo = parseMavenDependency(context, e);

				if (isTestOrProvidedScope(e))
				{
					log.debug("ignoring test/provided scope: {}", mavenInfo);
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
	MavenInfo parseMavenDependency(String context, Element dep) throws AetherRequiredException
	{
		String artifactId= getPomTag(context, dep, "artifactId");
		context+=" dependency/artifact '"+artifactId+"'";
		String groupId= getPomTag(context, dep, "groupId");
		String version= getPomTag(context, dep, "version");

		boolean optional=false;

		Node node=dep.getElementsByTagName("optional").item(0);

		if (node!=null)
		{
			optional=node.getTextContent().equals("true");
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
	boolean isTestOrProvidedScope(Element dep)
	{
		NodeList list = dep.getElementsByTagName("scope");

		if (list.getLength()>=1)
		{
			String scope=list.item(0).getTextContent().toLowerCase();

			return isTestOrProvidedScope(scope);
		}
		else
		{
			//BUG? Dependency scope might be declarable in a parent pom...
			//Is it worth the trouble to support?
			//I suppose that it doesn't hurt TOO much to have the test harness at runtime.
			log.debug("no scope");
		}

		return false;
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
}
