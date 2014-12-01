package com.github.osndok.mrb.grinder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.module.Dependency;
import javax.module.ModuleKey;
import javax.module.Version;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by robert on 10/31/14.
 */
public
class Spec
{
	public static final String RPM_NAME_PREFIX="mrb-";

	//TODO: maybe pull release in from mrb-grinder maven version?
	private static final String RELEASE="0";

	private final
	File file;

	public
	Spec(File file)
	{
		this.file = file;
	}

	public static
	File writeSunTools(ModuleKey moduleKey, RPMRepo rpmRepo) throws IOException
	{
		final
		File spec=File.createTempFile("mrb-sun-tools-",".spec");

		final
		Map<String,String> generalInfos;
		{
			generalInfos=new HashMap<String, String>();
			generalInfos.put("@NAME@", moduleKey.toString());
			generalInfos.put("@VERSION@", rpmVersionString(moduleKey));
			generalInfos.put("@RELEASE@", RELEASE);

			generalInfos.put("@MODULE_NAME@", moduleKey.getModuleName());
			generalInfos.put("@MAJOR_VERSION@", moduleKey.getMajorVersion());
			generalInfos.put("@MINOR_VERSION@", moduleKey.getMinorVersion());
		}

		generalInfos.put("@DEPS_FILE_CONTENTS@",moduleKey.getModuleName()+" "+moduleKey.getMajorVersion()+"\n");

		StringBuilder sb=readTemplate("com.sun-tools.spec");

		replace(sb, generalInfos);

		final
		OutputStream out=new FileOutputStream(spec);

		out.write(sb.toString().getBytes());

		out.flush();
		out.close();

		return spec;
	}

	public static
	File write(ModuleKey moduleKey, MavenJar mavenJar, Main main) throws IOException
	{
		final
		RPMRepo rpmRepo=main.getRPMRepo();

		final
		File jar=mavenJar.getFile().getCanonicalFile();

		final
		File spec=new File(jar.getParent(), jar.getName()+".spec");

		if (spec.exists())
		{
			log.warn("already exists: {}", spec);
			if (!spec.delete())
			{
				throw new IOException("cannot delete: "+spec);
			}
		}

		final
		Map<String,String> generalInfos;
		{
			generalInfos=new HashMap<String, String>();
			generalInfos.put("@NAME@", moduleKey.toString());
			generalInfos.put("@VERSION@", rpmVersionString(moduleKey));
			generalInfos.put("@RELEASE@", RELEASE);

			//TODO: extract license information from embedded pom.xml
			generalInfos.put("@LICENSE@", "Unknown");

			generalInfos.put("@JAR@", jar.getName());

			generalInfos.put("@MODULE_NAME@", moduleKey.getModuleName());
			generalInfos.put("@MAJOR_VERSION@", moduleKey.getMajorVersion());
			generalInfos.put("@MINOR_VERSION@", moduleKey.getMinorVersion());
		}

		//NB: do this (listing the dependencies) *strictly* before writing the spec file.
		final
		Set<Dependency> dependencies;

		try
		{
			//TODO: minor version is not being picked carried here (inside moduleKey)
			dependencies=mavenJar.listRpmDependencies(moduleKey, main);
		}
		catch (DependencyNotProcessedException e)
		{
			throw new IOException("unable to list rpm dependencies", e);
		}
		catch (ParserConfigurationException e)
		{
			throw new IOException("malformed pom.xml?", e);
		}
		catch (SAXException e)
		{
			throw new IOException("unable to process pom.xml", e);
		}

		final
		Map<String,String> execClassesByToolName=mavenJar.getExecClassesByToolName(moduleKey);

		StringBuilder sb=readTemplate("spec.prefix");

		replace(sb, generalInfos);

		final
		OutputStream out=new FileOutputStream(spec);

		try
		{
			out.write(sb.toString().getBytes());

			for (Dependency dependency : dependencies)
			{
				out.write(requiresLine(dependency));
			}

			/*
			NB: a bit of a 'hidden' dependency... every javax module *RPM* implicitly depends on the module
			loader (so that it can run). Except for the module loader itself (which would make it depend on itself).
			 */
			if (!moduleKey.equals(Version.JAVAX_MODULE) && !dependencies.contains(Version.JAVAX_MODULE))
			{
				out.write(requiresLine(Version.JAVAX_MODULE));
			}

			out.write(kludgy_descriptionFromPomFile(mavenJar));

			final
			Map<String,String> dependencyReplacements=buildDependencyReplacements(moduleKey, dependencies, mavenJar, generalInfos, execClassesByToolName);

			sb=readTemplate("spec.postfix");

			replace(sb, dependencyReplacements);
			replace(sb, generalInfos);

			out.write(sb.toString().getBytes());
		}
		finally
		{
			out.close();
		}

		return spec;
	}

	private static
	String rpmVersionString(ModuleKey moduleKey)
	{
		final
		String majorVersion=moduleKey.getMajorVersion();

		final
		String minorVersion=moduleKey.getMinorVersion();

		if (majorVersion==null)
		{
			if (minorVersion==null)
			{
				//NB: "snapshot is already in the rpm name
				return "0";
			}
			else
			{
				return "0."+minorVersion;
			}
		}
		else
		if (minorVersion==null)
		{
			return majorVersion;
		}
		else
		{
			return majorVersion+"."+minorVersion;
		}
	}

	private static
	Map<String, String> buildDependencyReplacements(
													   ModuleKey moduleKey,
													   Set<Dependency> dependencies,
													   MavenJar mavenJar,
													   Map<String, String> generalInfos,
													   Map<String, String> execClassesByToolName
	) throws IOException
	{
		Map<String, String> retval=new HashMap<String, String>();

		retval.put("@DEPS_FILE_CONTENTS@", depsFile(moduleKey, dependencies));
		retval.put("@BUILD_EXEC_FILES@", buildExecFiles(execClassesByToolName, generalInfos, mavenJar, moduleKey));
		retval.put("@EXEC_PATHS@", execPaths(execClassesByToolName, moduleKey, mavenJar));

		return retval;
	}

	private static
	String depsFile(
					 ModuleKey moduleKey,
					 Set<Dependency> dependencies
	)
	{
		StringBuilder sb=new StringBuilder("# Module's own identity:\n");
		depLine(sb, moduleKey);

		if (dependencies.isEmpty())
		{
			sb.append("\n# No dependencies ?!");
		}
		else
		{
			sb.append("\n# ... and its dependencies:\n");

			for (Dependency dependency : dependencies)
			{
				depLine(sb, dependency);
			}
		}

		return sb.toString();
	}

	private static
	void depLine(StringBuilder sb, ModuleKey moduleKey)
	{
		sb.append(moduleKey.getModuleName());

		String major=moduleKey.getMajorVersion();

		if (major==null)
		{
			sb.append(" snapshot");
		}
		else
		{
			sb.append(' ');
			sb.append(major);
		}

		String minor=moduleKey.getMinorVersion();

		if (minor!=null)
		{
			sb.append(' ');
			sb.append(minor);
		}

		sb.append('\n');
	}

	private static
	String buildExecFiles(
							 Map<String, String> execClassesByToolName,
							 Map<String, String> generalInfos,
							 MavenJar mavenJar, ModuleKey moduleKey
	) throws IOException
	{
		final
		StringBuilder retval=new StringBuilder();

		for (Map.Entry<String, String> me : execClassesByToolName.entrySet())
		{
			String toolName = me.getKey();
			String className = me.getValue();

			log.debug("cli tool: {} -> {}", toolName, className);

			if (toolName.equals("sysconfig"))
			{
				retval.append("\n\nmkdir -p ./etc/sysconfig\ncat -> ./etc/sysconfig/").append(moduleKey).append(" <<\"EOF\"\n");
				mavenJar.appendSysconfig(retval);
				retval.append("\nEOF\n");
			}
			else
			{
				StringBuilder sb = readTemplate("spec.exec");

				replace(sb, generalInfos);
				replace(sb, "@TOOL_NAME@", toolName);
				replace(sb, "@CLASS@", className);

				retval.append(sb.toString());
			}
		}

		for (Map.Entry<ModuleKey, Map<String, Set<String>>> me : mavenJar.getPluginMapping(moduleKey).entrySet())
		{
			final
			ModuleKey targetModule=me.getKey();

			final
			Map<String, Set<String>> implementationsByInterfaceName = me.getValue();

			retval.append("\n\nmkdir -p ./usr/share/java/").append(targetModule).append("/plugins.d\n");
			retval.append("cat -> ./usr/share/java/").append(targetModule).append("/plugins.d/").append(moduleKey).append(".plugin <<\"EOF\"\n");

			for (Map.Entry<String, Set<String>> me2 : implementationsByInterfaceName.entrySet())
			{
				final
				String interfaceName=me2.getKey();

				for (String implementationClass : me2.getValue())
				{
					retval.append(interfaceName);
					retval.append("\t");
					retval.append(implementationClass);
					retval.append("\n");
				}
			}

			retval.append("EOF\n\n");
		}


		return retval.toString();
	}

	private static
	String execPaths(Map<String, String> execClassesByToolName, ModuleKey moduleKey, MavenJar mavenJar)
	{
		StringBuilder sb=new StringBuilder();

		for (Map.Entry<String, String> me : execClassesByToolName.entrySet())
		{
			String toolName=me.getKey();

			if (toolName.equals("sysconfig"))
			{
				sb.append("/etc/sysconfig/").append(moduleKey).append('\n');
			}
			else
			{
				sb.append("%attr(755,root,root) /usr/bin/").append(toolName).append('\n');
			}
		}

		for (Map.Entry<ModuleKey, Map<String, Set<String>>> me : mavenJar.getPluginMapping(moduleKey).entrySet())
		{
			ModuleKey targetModule=me.getKey();
			sb.append("/usr/share/java/").append(targetModule).append("/plugins.d/").append(moduleKey).append(".plugin\n");
		}

		return sb.toString();
	}

	private static
	byte[] kludgy_descriptionFromPomFile(MavenJar mavenJar)
	{
		StringBuilder sb=new StringBuilder("\n%description\n");

		String descriptionFromPom=mavenJar.getDescription();

		if (descriptionFromPom==null)
		{
			sb.append("Upstream JAR/WAR conversion by Maven-RPM-Bridge (MrB).");
		}
		else
		{
			sb.append(descriptionFromPom);
		}

		sb.append("\n\n");
		return sb.toString().getBytes();
	}

	private static
	byte[] requiresLine(ModuleKey dependency)
	{
		StringBuilder sb=new StringBuilder("Requires: ");
		sb.append(RPM_NAME_PREFIX);
		sb.append(dependency.toString());

		String minor=dependency.getMinorVersion();

		if (minor!=null)
		{
			sb.append(" >= ");
			//NB: the "rpm version" is "{major}.{minor}" for increased readability... so we need to re-add the major number.
			sb.append(dependency.getMajorVersion());
			sb.append('.');
			sb.append(minor);
		}

		sb.append('\n');

		return sb.toString().getBytes();
	}

	private static
	void replace(StringBuilder sb, Map<String, String> replacements)
	{
		for (Map.Entry<String, String> me : replacements.entrySet())
		{
			String replaceMe=me.getKey();
			String withThis=me.getValue();

			int start;

			while ((start=sb.indexOf(replaceMe))>=0)
			{
				int end=start+replaceMe.length();
				sb.replace(start, end, withThis);
			}
		}
	}

	private static
	void replace(StringBuilder sb, String replaceMe, String withThis)
	{
		int start;

		while ((start=sb.indexOf(replaceMe))>=0)
		{
			int end=start+replaceMe.length();
			sb.replace(start, end, withThis);
		}
	}

	private static
	StringBuilder readTemplate(String name) throws IOException
	{
		StringBuilder sb=new StringBuilder();

		InputStream in=Spec.class.getClassLoader().getResourceAsStream(name);

		try
		{
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

		return sb;
	}

	private static final Logger log = LoggerFactory.getLogger(Spec.class);

}
