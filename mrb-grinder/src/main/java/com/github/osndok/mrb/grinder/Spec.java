package com.github.osndok.mrb.grinder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.module.Dependency;
import javax.module.ModuleKey;
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
	File write(ModuleKey moduleKey, MavenJar mavenJar, RPMRepo rpmRepo) throws IOException
	{
		final
		File jar=mavenJar.getFile();

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
			generalInfos.put("@VERSION@", moduleKey.getMajorVersion() + "." + moduleKey.getMinorVersion());
			generalInfos.put("@RELEASE@", RELEASE);

			//TODO: extract license information from embedded pom.xml
			generalInfos.put("@LICENSE@", "Unknown");

			generalInfos.put("@JAR@", jar.getName());

			generalInfos.put("@MODULE_NAME@", moduleKey.getModuleName());
			generalInfos.put("@MAJOR_VERSION@", moduleKey.getMajorVersion());
			generalInfos.put("@MINOR_VERSION@", moduleKey.getMinorVersion());
		}

		final
		Set<Dependency> dependencies=mavenJar.listRpmDependencies(rpmRepo);

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

			out.write(descriptionFromPomFile(mavenJar));

			final
			Map<String,String> dependencyReplacements=buildDependencyReplacements(moduleKey, dependencies, mavenJar, generalInfos, execClassesByToolName);
		}
		finally
		{
			out.close();
		}

		return spec;
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
		retval.put("@BUILD_EXEC_FILES@", buildExecFiles(execClassesByToolName, generalInfos));
		retval.put("@EXEC_PATHS@", execPaths(execClassesByToolName));

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
			sb.append("\n# ").append(moduleKey).append(" dependencies:\n");

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
	String buildExecFiles(Map<String, String> execClassesByToolName, Map<String, String> generalInfos) throws IOException
	{
		final
		StringBuilder retval=new StringBuilder();

		for (Map.Entry<String, String> me : execClassesByToolName.entrySet())
		{
			String toolName = me.getKey();
			String className = me.getValue();

			StringBuilder sb=readTemplate("spec.exec");

			replace(sb, generalInfos);
			replace(sb, "@TOOL_NAME@", toolName);
			replace(sb, "@CLASS@", className);

			retval.append(sb.toString());
		}

		return retval.toString();
	}

	private static
	String execPaths(Map<String, String> execClassesByToolName)
	{
		StringBuilder sb=new StringBuilder();

		for (Map.Entry<String, String> me : execClassesByToolName.entrySet())
		{
			String toolName=me.getKey();
			sb.append("/usr/bin/").append(toolName).append("\n");
		}

		return sb.toString();
	}

	private static
	byte[] descriptionFromPomFile(MavenJar mavenJar)
	{
		StringBuilder sb=new StringBuilder("\n%description\n");
		//TODO: read description from pom.xml
		sb.append("Upstream JAR/WAR conversion by Maven-RPM-Bridge (MrB).");

		sb.append("\n\n");
		return sb.toString().getBytes();
	}

	private static
	byte[] requiresLine(Dependency dependency)
	{
		StringBuilder sb=new StringBuilder("Requires: ");
		sb.append(dependency.toString());

		String minor=dependency.getMinorVersion();

		if (minor!=null)
		{
			sb.append(" >= ");
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

		InputStream in=Spec.class.getResourceAsStream(name);

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
