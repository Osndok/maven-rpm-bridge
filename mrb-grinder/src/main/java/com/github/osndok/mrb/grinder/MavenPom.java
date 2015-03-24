package com.github.osndok.mrb.grinder;

import com.github.osndok.mrb.grinder.aether.ConsoleRepositoryListener;
import com.github.osndok.mrb.grinder.aether.ConsoleTransferListener;
import com.github.osndok.mrb.grinder.aether.ManualRepositorySystemFactory;
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
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Created by robert on 12/1/14.
 */
public
class MavenPom
{
	private static final Logger log = LoggerFactory.getLogger(MavenPom.class);

	private final
	MavenInfo mavenInfo;

	private final
	MavenInfo parentInfo;

	private
	MavenPom parentPom;

	private
	String description;

	private
	Set<MavenInfo> declaredDependencies;

	private
	Set<MavenInfo> dependencies;

	private
	Integer deploymentPortNumber;

	private final
	Properties localProperties;

	public
	MavenPom(InputStream inputStream) throws IOException, ParserConfigurationException, SAXException
	{
		this(null, inputStream);
	}

	public
	MavenPom(
				MavenInfo mavenInfo,
				InputStream inputStream
	) throws IOException, ParserConfigurationException, SAXException
	{
		final
		Document pom = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputStream);

		/*
		Without a pom.xml, we have to way of determining what a jar's dependencies are.
		However... some (particularly low-level) apis simply don't have deps, or are built
		from the ant tool, and injected manually into the maven repo.

		TODO: is there a way to get a pom.xml for a jar given only it's MavenInfo? It's worth trying.

		 */
		if (pom == null)
		{
			throw new IOException("could not find embedded pom.xml for " + mavenInfo);
			//return Collections.emptySet();
		}

		//pom.getDocumentElement().normalize();

		NodeList topLevel = pom.getDocumentElement().getChildNodes();

		log.debug("top level to pom: {}", topLevel);

		String artifactId = stringChild(topLevel, "artifactId");

		//TODO: these two can (and often are) null, because they are defined in the parent pom. Maybe we should at least try and fetch it?
		String groupId = stringChild(topLevel, "groupId");
		String version = stringChild(topLevel, "version");

		final
		Node parent=tagNamed("parent", topLevel);

		if (parent==null)
		{
			this.parentPom=null;
			this.parentInfo=null;
		}
		else
		{
			log.debug("{} pom has a parent...", artifactId);

			NodeList parentInfo = parent.getChildNodes();

			String parentGroupId=stringChild(parentInfo, "groupId");
			String parentArtifactId=stringChild(parentInfo, "artifactId");
			String parentVersion=stringChild(parentInfo, "version");

			if (groupId==null) groupId=parentGroupId;
			if (version==null) version=parentVersion;

			this.parentInfo=new MavenInfo(parentGroupId, parentArtifactId, parentVersion);

			//File parentFile=Main.guessLocalPomPath(this.parentInfo);
		}

		final
		Node propertiesNode=tagNamed("properties", topLevel);

		if (propertiesNode==null)
		{
			log.debug("{} has no localProperties...", artifactId);

			this.localProperties = new Properties();
		}
		else
		{
			this.localProperties = readProperties(propertiesNode);
		}

		if (mavenInfo == null)
		{
			mavenInfo = new MavenInfo(groupId, artifactId, version);
			log.debug("constructed mavenInfo: {}", mavenInfo);
		}
		else
		{
			log.debug("given mavenInfo:  {}", mavenInfo);
			String msg = "pom.xml does not correspond to actual artifact. ";

			//Verify that it's what we think it is...
			if (!mavenInfo.getArtifactId().equals(artifactId))
			{
				throw new IOException(msg+mavenInfo.getArtifactId()+" != "+artifactId);
			}

			//ATM, these can be null (in parent pom)
			if (groupId!=null && !mavenInfo.getGroupId().equals(groupId))
			{
				throw new IOException(msg + mavenInfo.getGroupId() + " != " + groupId);
			}

			if (version!=null && !mavenInfo.getVersion().equals(version))
			{
				throw new IOException(msg+mavenInfo.getVersion()+" != "+version);
			}
		}

		this.mavenInfo = mavenInfo;

		Node description = pom.getElementsByTagName("description").item(0);

		if (description == null)
		{
			log.debug("no description");
			this.description = null;
		}
		else
		{
			//TODO: if a multiline description, trim each line to avoid indentation issues.
			this.description = description.getTextContent().trim();
			log.debug("description: {}", this.description);
		}

		this.declaredDependencies = _getDependencies(mavenInfo, pom);
	}

	private
	void possiblePortNumber(Properties properties, String key)
	{
		final
		String value = properties.getProperty(key);

		if (value!=null)
		{
			this.deploymentPortNumber = new Integer(value);
			log.debug("discovered web port number: {}", deploymentPortNumber);
		}
	}

	/**
	 * @param parent localProperties, untouched, for reference only.
	 * @param child localProperties, modified and reused as a return value.
	 * @return a set of localProperties that is generally an additive set of both the parent and child localProperties, but were the child localProperties wins when there is a contest
	 */
	private static
	Properties overrideProperties(Properties parent, Properties child)
	{
		for (String key : parent.stringPropertyNames())
		{
			String value=parent.getProperty(key);

			if (!child.containsKey(key))
			{
				child.setProperty(key, value);
			}
		}

		return child;
	}

	private
	Properties readProperties(Node parentNode)
	{
		final
		Properties retval=new Properties();

		final
		NodeList nodeList = parentNode.getChildNodes();

		final
		int l=nodeList.getLength();

		for (int i=0; i<l; i++)
		{
			final
			Node node=nodeList.item(i);

			if (node instanceof Element)
			{
				Element e=(Element)node;

				String name=e.getTagName();
				String value=e.getTextContent();

				log.debug("property: {} -> {}", name, value);
				retval.setProperty(name, value);
			}
		}

		return retval;
	}

	private static
	Node tagNamed(String tagName, NodeList nodeList)
	{
		int l=nodeList.getLength();
		for (int i=0; i<l; i++)
		{
			Node node=nodeList.item(i);
			if (node instanceof Element)
			{
				Element e=(Element)node;
				if (e.getTagName().equals(tagName))
				{
					return node;
				}
			}
		}

		log.debug("{} tag not found under {}", tagName, nodeList);
		return null;
	}

	private
	String stringChild(NodeList nodeList, String tagName)
	{
		int l=nodeList.getLength();
		for (int i=0; i<l; i++)
		{
			Node node=nodeList.item(i);
			if (node instanceof Element)
			{
				Element e=(Element)node;
				if (e.getTagName().equals(tagName))
				{
					String retval=e.getTextContent().trim();
					log.debug("{} -> {}", e, retval);
					return retval;
				}
				else
				{
					log.trace("other tag: {} / {}", e.getTagName(), e);
				}
			}
			else
			if (node instanceof Text)
			{
				log.trace("probably whitespace: {}", node);
			}
			else
			{
				log.trace("not an element: {} / {}", node.getClass(), node);
			}
		}

		log.debug("{} tag not found under {}", tagName, nodeList);
		return null;
	}

	private static
	Set<MavenInfo> _getDependencies(MavenInfo mavenInfo, Document pom) throws IOException
	{
		//Node depsGroup = pom.getElementsByTagName("dependencies").item(0);
		Node depsGroup=tagNamed("dependencies", pom.getDocumentElement().getChildNodes());

		if (!(depsGroup instanceof Element))
		{
			//usually null...
			log.info("dependencies is not an element: {}", depsGroup);
			return Collections.emptySet();
		}

		NodeList dependencies = ((Element) depsGroup).getElementsByTagName("dependency");

		int l=dependencies.getLength();

		String context=mavenInfo+"::pom.xml";

		try
		{
			final
			Set<MavenInfo> retval = new HashSet<MavenInfo>();

			for (int i = 0; i < l; i++)
			{
				Element e = (Element) dependencies.item(i);
				String scope=getScope(e);
				boolean optional=isTestOrProvidedScope(scope);

				MavenInfo dependencyInfo = parseMavenDependency(context, e, optional);

				log.debug("parsed mavenInfo for declared dependency: {}", dependencyInfo);

				if (scope.equals("test"))
				{
					log.debug("ignoring test scope: {}", dependencyInfo);
				}
				else
				{
					retval.add(dependencyInfo);
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

			Artifact artifact = new DefaultArtifact( mavenInfo.toString() );

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
				String classifier=aetherArtifact.getClassifier();
				String packaging=aetherArtifact.getExtension();

				if (!isTestOrProvidedScope(scope))
				{
					retval.add(new MavenInfo(aetherArtifact.getGroupId(), aetherArtifact.getArtifactId(), aetherArtifact.getVersion(),
												classifier, packaging, dependency.isOptional()));
				}
			}

			return retval;
		}
	}

	private static
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

		Node classifierTag=dep.getElementsByTagName("classifier").item(0);

		final
		String classifier;
		{
			if (classifierTag == null)
			{
				classifier = null;
			}
			else
			{
				classifier = classifierTag.getTextContent().trim();
			}
		}

		String packaging="jar";

		return new MavenInfo(groupId, artifactId, version, classifier, packaging, optional);
	}

	private static
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

	private static
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

	private static
	boolean isTestOrProvidedScope(String scope)
	{
		return scope!=null && (scope.equals("test") || scope.equals("provided"));
	}

	private static
	RepositorySystemSession newRepositorySystemSession(RepositorySystem system)
	{
		//TODO: ask someone "in the know" if this is redundant (or dangerous), should we have to specify the "default" user repo?
		String HOME=System.getenv("HOME");
		LocalRepository localRepo = new LocalRepository( HOME+"/.m2/repository" );

		MavenRepositorySystemSession session = new MavenRepositorySystemSession();
		session.setLocalRepositoryManager( system.newLocalRepositoryManager( localRepo ) );
		session.setTransferListener( new ConsoleTransferListener() );
		session.setRepositoryListener( new ConsoleRepositoryListener() );
		// uncomment to generate dirty trees
		// session.setDependencyGraphTransformer( null );
		return session;
	}

	public
	Set<MavenInfo> getDependencies()
	{
		if (dependencies==null)
		{
			if (parentInfo==null)
			{
				dependencies=declaredDependencies;
			}
			else
			{
				try
				{
					Set<MavenInfo> accumulator=new HashSet<>(declaredDependencies);

					accumulator.addAll(getParentPom().getDependencies());

					dependencies=accumulator;
				}
				catch (Exception e)
				{
					log.error("unable to include parent pom's dependencies (if any)", e);
					dependencies=declaredDependencies;
				}
			}
		}

		return dependencies;
	}

	public
	String getDescription()
	{
		return description;
	}

	public
	MavenInfo getMavenInfo()
	{
		return mavenInfo;
	}

	public
	MavenPom getParentPom() throws IOException, ParserConfigurationException, SAXException
	{
		if (parentPom==null && parentInfo!=null)
		{
			File pomFile = Main.guessLocalPomPath(parentInfo);
			FileInputStream fis = new FileInputStream(pomFile);
			try
			{
				parentPom=new MavenPom(parentInfo, fis);
			}
			finally
			{
				fis.close();
			}
		}
		return parentPom;
	}

	public
	int getDeploymentPortNumber()
	{
		if (deploymentPortNumber==null)
		{
			final
			Properties p=getProperties();

			//Order by most-specific-last...
			possiblePortNumber(p, "web.port");
			possiblePortNumber(p, "com.allogy.web.port");
			possiblePortNumber(p, "production.web.port");

			if (deploymentPortNumber==null)
			{
				log.error("{} has no deployment port number", mavenInfo);
				deploymentPortNumber=8080;
			}
		}

		return deploymentPortNumber;
	}

	private
	Properties combinedProperties;

	public
	Properties getProperties()
	{
		if (combinedProperties==null)
		{
			try
			{
				final
				MavenPom parent = getParentPom();

				if (parent==null)
				{
					//TODO: clone properties (if wanting to be writable)?
					combinedProperties=localProperties;
				}
				else
				{
					combinedProperties=overrideProperties(parent.getProperties(), localProperties);
				}
			}
			catch (FileNotFoundException e)
			{
				log.warn("parent pom not available? {}", parentInfo, e);
				combinedProperties=localProperties;
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

		return combinedProperties;
	}

}
