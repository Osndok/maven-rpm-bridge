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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;

/**
 * Created by robert on 12/1/14.
 */
public
class MavenPom
{
	private static final Logger log = LoggerFactory.getLogger(MavenPom.class);

	private final
	MavenInfo mavenInfo;

	private
	String description;

	private
	Set<MavenInfo> dependencies;

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

		pom.getDocumentElement().normalize();

		NodeList topLevel = pom.getChildNodes().item(0).getChildNodes();

		String groupId = stringChild(topLevel, "groupId");
		String artifactId = stringChild(topLevel, "artifactId");
		String version = stringChild(topLevel, "version");

		/*
		String groupId = pom.getElementsByTagName("groupId").item(0).getTextContent().trim();
		String artifactId = pom.getElementsByTagName("artifactId").item(0).getTextContent().trim();
		String version = pom.getElementsByTagName("version").item(0).getTextContent().trim();
		*/

		if (mavenInfo == null)
		{
			mavenInfo = new MavenInfo(groupId, artifactId, version);
		}
		else
		{
			String msg = "pom.xml does not correspond to parent artifact. ";

			//Verify that it's what we think it is...
			if (groupId!=null && !mavenInfo.getGroupId().equals(groupId))
				throw new IOException(msg + mavenInfo.getGroupId() + " != " + groupId);
			if (artifactId!=null && !mavenInfo.getArtifactId().equals(artifactId)) throw new IOException(msg+mavenInfo.getArtifactId()+" != "+artifactId);
			if (version!=null && !mavenInfo.getVersion().equals(version)) throw new IOException(msg+mavenInfo.getVersion()+" != "+version);
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

		this.dependencies = _getDependencies(mavenInfo, pom);
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
					return e.getTextContent().trim();
				}
			}
		}

		return null;
	}

	private static
	Set<MavenInfo> _getDependencies(MavenInfo mavenInfo, Document pom) throws IOException
	{
		Node depsGroup = pom.getElementsByTagName("dependencies").item(0);

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

				if (!isTestOrProvidedScope(scope))
				{
					retval.add(new MavenInfo(aetherArtifact.getGroupId(), aetherArtifact.getArtifactId(), aetherArtifact.getVersion(), dependency.isOptional()));
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

		return new MavenInfo(groupId, artifactId, version, optional);
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
		MavenRepositorySystemSession session = new MavenRepositorySystemSession();
		LocalRepository localRepo = new LocalRepository( "target/local-repo" );
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

}
