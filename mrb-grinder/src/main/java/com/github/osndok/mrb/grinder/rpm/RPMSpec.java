package com.github.osndok.mrb.grinder.rpm;

import com.github.osndok.mrb.grinder.DependencyNotProcessedException;
import com.github.osndok.mrb.grinder.JarHasNoPomException;
import com.github.osndok.mrb.grinder.Main;
import com.github.osndok.mrb.grinder.MavenJar;
import com.github.osndok.mrb.grinder.api.SpecShard;
import com.github.osndok.mrb.grinder.meta.GrinderModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.module.meta.LoaderModule;
import javax.module.util.Dependency;
import javax.module.util.ModuleKey;
import javax.module.util.VersionString;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by robert on 10/31/14.
 */
public
class RPMSpec
{
	public static final String RPM_NAME_PREFIX="mrb-";

	//TODO: maybe pull release in from mrb-grinder maven version? or .build_number file?
	//TODO: !!!: maybe publish incremental releases (relative to the newest in-repo), esp. for snapshots
	private static final String RELEASE="0";

	private final
	File file;

	public
	RPMSpec(File file)
	{
		this.file = file;
	}

	public static
	File writeSunTools(ModuleKey moduleKey, RPMRepo rpmRepo) throws IOException
	{
		final
		File spec=File.createTempFile("mrb-sun-tools-", ".spec");

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

	private static final
	ModuleKey LOADER_MODULE_KEY = LoaderModule.getModuleKey();

	public static
	File write(
				  ModuleKey moduleKey,
				  MavenJar mavenJar,
				  Main main,
				  File warFile,
				  Collection<SpecShard> extraShards
	) throws IOException
	{
		final
		RPMRepo rpmRepo = RPMManifold.getRepoFor(mavenJar.getInfo());

		final
		File jar = mavenJar.getFile().getCanonicalFile();

		final
		File spec = new File(jar.getParent(), jar.getName() + ".spec");

		if (spec.exists())
		{
			log.warn("already exists: {}", spec);
			if (!spec.delete())
			{
				throw new IOException("cannot delete: " + spec);
			}
		}

		log.debug("writing spec for: {} / {} / {}", moduleKey, mavenJar, mavenJar.getInfo());

		final
		Map<String, String> generalInfos;
		{
			generalInfos = new HashMap<String, String>();
			generalInfos.put("@NAME@", moduleKey.toString());
			generalInfos.put("@VERSION@", rpmVersionString(moduleKey));
			generalInfos.put("@RELEASE@", RELEASE);

			//TODO: extract license information from embedded pom.xml
			generalInfos.put("@LICENSE@", "Unknown");

			generalInfos.put("@JAR@", jar.getName());

			if (warFile == null)
			{
				//Repeating 'jar' will probably not cause an error, as an empty source declaration would
				generalInfos.put("@WAR@", jar.getName());
			}
			else
			{
				generalInfos.put("@WAR@", warFile.getName());
			}

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
			dependencies = mavenJar.listRpmDependencies(moduleKey, main);
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
		Map<String, String> execClassesByToolName = mavenJar.getExecClassesByToolName(moduleKey);

		StringBuilder sb = readTemplate("spec.prefix");

		replace(sb, generalInfos);

		final
		OutputStream out = new FileOutputStream(spec);

		try
		{
			out.write(sb.toString().getBytes());

			for (Dependency dependency : dependencies)
			{
				out.write(requiresLine(dependency));
			}

			writeRequiresLines(out, null, extraShards);

			//TODO: detect the jar's major.minor and require the appropriate java version

			/*
			NB: a bit of a 'hidden' dependency... every javax module *RPM* implicitly depends on the module
			loader (so that it can run). Except for the module loader itself (which would make it depend on itself).
			 */
			if (!moduleKey.matchesName(LOADER_MODULE_KEY) && !dependencies.contains(LOADER_MODULE_KEY))
			{
				out.write(requiresLine(LOADER_MODULE_KEY));
			}

			out.write(descriptionFromPomFile(mavenJar));

			for (SpecShard specShard : notNull(extraShards))
			{
				String name=specShard.getSubPackageName();
				if (name==null) continue;

				out.write(String.format("%%package %s\n", name).getBytes());

				for (String requiresLine : notNull(specShard.getRpmRequiresLines()))
				{
					out.write("Requires: ".getBytes());
					out.write(requiresLine.getBytes());
					out.write("\n".getBytes());
				}

				String description=specShard.getSubPackageDescription();
				out.write(String.format("Summary: %s\n%%description %s\n%s\n", description, name, description).getBytes());

				for (Map.Entry<String, String> me : specShard.getScriptletBodiesByType().entrySet())
				{
					String type=me.getKey();
					String body=me.getValue();

					//Install scriptlets must be merged with the main install scriptlet.
					if (type.equals("install")) continue;

					out.write(String.format("\n%%%s %s\n%s\n", type, name, body).getBytes());
				}
			}

			final
			Map<String,String> dependencyReplacements=buildDependencyReplacements(moduleKey, dependencies, mavenJar, generalInfos, execClassesByToolName, extraShards);

			sb=readTemplate("spec.postfix");

			replace(sb, dependencyReplacements);
			replace(sb, generalInfos);

			out.write(sb.toString().getBytes());

			for (SpecShard specShard : notNull(extraShards))
			{
				String name=specShard.getSubPackageName();
				if (name==null) continue;

				out.write(String.format("\n%%files %s\n", name).getBytes());

				//for (String path : notNull(specShard.getFileContentsByPath().keySet()))
				for (String path : notNull(specShard.getFilePathsToPackage()))
				{
					if (looksLikeConfigFile(path)) out.write("%config ".getBytes());
					out.write(path.getBytes());
					out.write("\n".getBytes());
				}
			}

		}
		finally
		{
			out.close();
		}

		return spec;
	}

	private static
	boolean looksLikeConfigFile(String path)
	{
		return path.endsWith(".props")
			|| path.endsWith(".conf")
			|| path.endsWith(".config")
			|| path.endsWith(".cfg")
			|| path.endsWith(".ini")
			;
	}

	private static
	void writeRequiresLines(OutputStream out, String shardName, Collection<SpecShard> extraShards) throws IOException
	{
		for (SpecShard shard : notNull(extraShards))
		{
			String name=shard.getSubPackageName();

			if (nullableNamesMatch(name, shardName))
			{
				for (String s : notNull(shard.getRpmRequiresLines()))
				{
					out.write(s.getBytes());
				}
			}
		}
	}

	private static
	boolean nullableNamesMatch(String actualName, String expectedName)
	{
		if (expectedName==null)
		{
			return actualName==null;
		}
		else
		{
			return expectedName.equals(actualName);
		}
	}

	private static <T>
	Collection<T> notNull(Collection<T> possiblyNullCollection)
	{
		if (possiblyNullCollection==null)
		{
			return Collections.emptySet();
		}
		else
		{
			return possiblyNullCollection;
		}
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
													   Map<String, String> execClassesByToolName,
													   Collection<SpecShard> extraShards
	) throws IOException
	{
		Map<String, String> retval=new HashMap<String, String>();

		retval.put("@DEPS_FILE_CONTENTS@", depsFile(moduleKey, dependencies));
		retval.put("@BUILD_EXEC_FILES@", buildExecFiles(execClassesByToolName, generalInfos, mavenJar, moduleKey, extraShards));
		retval.put("@EXEC_PATHS@", execPaths(execClassesByToolName, moduleKey, mavenJar, extraShards));

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
							 MavenJar mavenJar, ModuleKey moduleKey,
							 Collection<SpecShard> extraShards
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
				replace(sb, "@TOOL_NAME@", maybeRemovePath(toolName));
				replace(sb, "@TOOL_PATH@", maybeAddPath(toolName));
				replace(sb, "@CLASS@", className);
				replace(sb, "@GRINDER_VERSION@", GrinderModule.FULL);

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

		for (SpecShard specShard : notNull(extraShards))
		{
			String name=specShard.getSubPackageName();
			retval.append("# The '").append(name).append("' sub package\n\n");

			Map<String, String> fileContentsByPath = specShard.getFileContentsByPath();

			if (fileContentsByPath!=null)
			{
				for (Map.Entry<String, String> me : fileContentsByPath.entrySet())
				{
					String path=me.getKey();
					String body=me.getValue();
					String eofMarker=beginLiteralFile(retval, path);

					retval.append(body);

					char lastChar=body.charAt(body.length()-1);

					if (lastChar!='\n') retval.append('\n');

					retval.append(eofMarker);
				}

			}

			final
			Map<String, String> scriptlets = specShard.getScriptletBodiesByType();

			if (scriptlets!=null && scriptlets.containsKey("install"))
			{
				retval.append("\necho 1>&2 \"install phase for ").append(specShard.getSubPackageName()).append(" subpackage\"\n\n");
				retval.append(scriptlets.get("install"));
			}
		}

		for (Map.Entry<String, Properties> me : mavenJar.getReactorPropertiesByPath(moduleKey).entrySet())
		{
			String path=me.getKey();
			Properties properties=me.getValue();

			String eofMarker=beginLiteralFile(retval, path);
			retval.append("# Written by maven-rpm-bridge::mrb-grinder::RPMSpec\n");

			//TODO: is there no standard way of dumping properties to a string buffer?
			for (String key : properties.stringPropertyNames())
			{
				String value=properties.getProperty(key);
				//Quote the value, in case it contains a space, or something...
				//Hopefully it does not contain any quotes or vertical whitespace characters.
				//TODO: check to see if the key or values contains illegal characters?
				retval.append(key);
				retval.append("=\"");
				retval.append(value);
				retval.append("\"\n");
			}

			retval.append(eofMarker);
		}

		return retval.toString();
	}

	private static
	String beginLiteralFile(StringBuilder retval, String path)
	{
		int nonce= ThreadLocalRandom.current().nextInt(100000);

		File file=new File(path);
		String dir=file.getParent();

		if (dir.startsWith("/"))
		{
			retval.append("\n\nmkdir -p .").append(dir).append("\n");
		}

		retval.append("cat   -> .").append(path).append(" <<\"EOF-").append(nonce).append("\"\n");

		/*
		All the globs of literal files can be hard to read, so try and provide a visually scannable
		barrier between them for "slightly better" readability.
		 */
		return ("EOF-"+nonce+"\n\n#-------------------------------------------------------\n\n");
	}

	private static
	String maybeAddPath(String toolName)
	{
		final
		char c=toolName.charAt(0);

		if (c=='/' || c=='%')
		{
			return toolName;
		}
		else
		{
			return "/usr/bin/"+toolName;
		}
	}

	private static
	String maybeRemovePath(String toolName)
	{
		return new File(toolName).getName();
	}

	private static
	String execPaths(
						Map<String, String> execClassesByToolName,
						ModuleKey moduleKey,
						MavenJar mavenJar,
						Collection<SpecShard> extraShards
	) throws IOException
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

		for (SpecShard specShard : notNull(extraShards))
		{
			String name=specShard.getSubPackageName();

			if (name!=null) continue;

			Map<String, String> fileContentsByPath = specShard.getFileContentsByPath();

			if (fileContentsByPath!=null)
			{
				for (String path : fileContentsByPath.keySet())
				{
					sb.append(path);
					sb.append('\n');
				}
			}
		}

		for (String path : mavenJar.getReactorPropertiesByPath(moduleKey).keySet())
		{
			sb.append("%config ");
			sb.append(path);
			sb.append('\n');
		}

		return sb.toString();
	}

	private static
	byte[] descriptionFromPomFile(MavenJar mavenJar) throws IOException
	{
		StringBuilder sb=new StringBuilder("\n%description\n");

		String descriptionFromPom=null;

		try
		{
			descriptionFromPom=mavenJar.getDescription();

			if (descriptionFromPom!=null)
			{
				descriptionFromPom=removeWhitespaceAfterNewlines(descriptionFromPom).trim();
			}
		}
		catch (JarHasNoPomException e)
		{
			log.debug("{}", e.toString());
		}

		if (descriptionFromPom==null || descriptionFromPom.length()==0)
		{
			sb.append("Upstream JAR/WAR conversion by Maven-RPM-Bridge (MrB).");
		}
		else
		{
			sb.append(descriptionFromPom);
		}

		{
			sb.append("\n\nmrb @ ");
			sb.append(GrinderModule.FULL);
		}

		sb.append("\n\n");
		return sb.toString().getBytes();
	}

	private static
	String removeWhitespaceAfterNewlines(String s)
	{
		final
		int l=s.length();

		final
		StringBuilder sb=new StringBuilder();

		boolean lastWasNewline=true;

		for (int i=0; i<l; i++)
		{
			final
			char c = s.charAt(i);

			if (c=='\n')
			{
				lastWasNewline=true;
				sb.append('\n');
			}
			else
			if (lastWasNewline && Character.isWhitespace(c))
			{
				//Do not append it, do not change lastWasNewLine
			}
			else
			{
				lastWasNewline=false;
				sb.append(c);
			}
		}

		return sb.toString();
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

		InputStream in=RPMSpec.class.getClassLoader().getResourceAsStream(name);

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

	private static final Logger log = LoggerFactory.getLogger(RPMSpec.class);

}
