package com.github.osndok.mrb.grinder;

import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
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
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
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

		//And the sad thing is... this unreadable glob is actually *much-easier* than any "standard" way of doing it...
		Reflections reflections = new Reflections(new ConfigurationBuilder().setUrls(Collections.singleton(file.toURI().toURL())));

		boolean hasOverride=false;

		for (Class<?> aClass : reflections.getSubTypesOf(Object.class))
		{
			if (hasPublicStaticMainMethod(aClass))
			{
				String className=aClass.getName();
				log.debug("has main: {}", className);

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
			log.info("can't get javax-module-exec field", e);
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
			log.debug("hasPublicStaticMainMethod?", e);
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
				mainClassName = jarFile.getManifest().getMainAttributes().getValue("Main-Class");
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
		Set<Dependency> retval=new HashSet<Dependency>();

		retval.add(Version.JAVAX_MODULE.asDependencyOf(moduleKey));

		for (MavenInfo info : listMavenDependenciesFromPomXml())
		{
			retval.add(rpmRepo.getFullModuleDependency(moduleKey, info));
		}

		return retval;
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

		NodeList dependencies = ((Element) pom.getElementsByTagName("dependencies").item(0)).getElementsByTagName("dependency");

		int l=dependencies.getLength();

		final
		Set<MavenInfo> retval=new HashSet<MavenInfo>();

		for (int i=0; i<l; i++)
		{
			Element e = (Element) dependencies.item(i);

			MavenInfo mavenInfo=parseMavenDependency(e);

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

	private
	MavenInfo parseMavenDependency(Element dep)
	{
		String groupId=dep.getElementsByTagName("groupId").item(0).getTextContent();
		String artifactId=dep.getElementsByTagName("artifactId").item(0).getTextContent();
		String version=dep.getElementsByTagName("version").item(0).getTextContent();

		return new MavenInfo(groupId, artifactId, version);
	}

	private
	boolean isTestOrProvidedScope(Element dep)
	{
		NodeList list = dep.getElementsByTagName("scope");

		if (list.getLength()>=1)
		{
			String scope=list.item(0).getTextContent().toLowerCase();

			if (scope.equals("test") || scope.equals("provided"))
			{
				return true;
			}
			else
			{
				log.info("scope={}", scope);
			}
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
