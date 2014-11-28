package com.github.osndok.mrb.grinder;

import com.github.osndok.mrb.grinder.util.Exec;
import org.osjava.jardiff.DiffCriteria;
import org.osjava.jardiff.PublicDiffCriteria;
import org.semver.Comparer;
import org.semver.Delta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.module.Dependency;
import javax.module.ModuleInfo;
import javax.module.ModuleKey;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

/**
 * Created by robert on 10/30/14.
 */
public
class RPM
{
	private static final String RPMBUILD_INDICATOR_PREFIX = "Wrote: ";
	private static final String REGEX_SAFE_PIPING_SYMBOL  = Pattern.quote("|");

	private final
	File file;

	private
	ModuleKey moduleKey;

	private final
	DiffCriteria diffCriteria = new PublicDiffCriteria();

	public
	RPM(File file)
	{
		this.file = file;
	}

	public
	ModuleKey getModuleKey() throws IOException
	{
		if (moduleKey == null)
		{
			String namePipeVersion = Exec.toString("rpm", "--queryformat", "%{NAME}|%{VERSION}", "-qp",
													  file.getAbsolutePath());
			log.debug("namePipeVersion='{}'", namePipeVersion);
			String[] bits = namePipeVersion.split(REGEX_SAFE_PIPING_SYMBOL);
			String rpmName = bits[0];
			String rpmVersion = bits[1];
			String noPrefix = maybeRemovePrefix(rpmName, Spec.RPM_NAME_PREFIX);
			log.debug("rpm name ('{}') -to-module-name-> '{}'", rpmName, noPrefix);
			try
			{
				moduleKey = addMinorVersion(ModuleKey.parseModuleKey(noPrefix), rpmVersion);
			}
			catch (ParseException e)
			{
				throw new RuntimeException("unparsable module key: " + rpmName);
			}
		}

		return moduleKey;
	}

	private
	ModuleKey addMinorVersion(ModuleKey moduleKey, String rpmVersion)
	{
		try
		{
			String majorVersion=moduleKey.getMajorVersion();

			if (rpmVersion.equals(majorVersion))
			{
				//e.g. "javax.inject-v1-1" rpmVersion does not include minor b/c it DNE.
				return moduleKey;
			}
			else
			{
				String minor = maybeRemovePrefix(rpmVersion, moduleKey.getMajorVersion() + ".");
				return new ModuleKey(moduleKey.getModuleName(), majorVersion, minor);
			}
		}
		catch (Exception e)
		{
			log.error("unable to add minor version to module key: {} / {}", moduleKey, rpmVersion, e);
			return moduleKey;
		}
	}

	private
	String maybeRemovePrefix(String s, String sPrefix)
	{
		if (s.startsWith(sPrefix))
		{
			return s.substring(sPrefix.length());
		}
		else
		{
			log.warn("'{}' does not start with '{}' prefix", s, sPrefix);
			return s;
		}
	}

	public
	boolean innerJarIsCompatibleWithNewer(MavenJar mavenJar) throws IOException
	{
		MavenInfo mavenInfo=mavenJar.getInfo();
		File innerJar=File.createTempFile(mavenInfo.getArtifactId(), ".jar");

		try
		{
			Exec.andWait("bash", "-c", "rpm2cpio "+file+" | cpio -i --to-stdout '*.jar' > "+innerJar);

			Set<String> includes = Collections.emptySet();
			Set<String> excludes = Collections.emptySet();
			Delta delta = new Comparer(diffCriteria, innerJar, mavenJar.getFile(), includes, excludes).diff();

			//Given no special information asto if the dependency is one of implementation or simple usage
			//(though, we might be able to discover most implemenattions by scanning the classes), we must
			//presume that they *do* implement interfaces, etc.
			switch(delta.computeCompatibilityType())
			{
				case BACKWARD_COMPATIBLE_IMPLEMENTER:
					return true;

				case BACKWARD_COMPATIBLE_USER:
				case NON_BACKWARD_COMPATIBLE:
					for (Delta.Difference difference : delta.getDifferences())
					{
						log.info("difference: {}", difference.getInfo());
					}
					return false;

				default:
					throw new AssertionError();
			}
		}
		finally
		{
			innerJar.delete();
		}
	}

	private static final Logger log = LoggerFactory.getLogger(RPM.class);

	//TODO: fix presumption that spec dir is writable, ATM we place the spec next to the jar, and 'out' can collide.
	public static
	File build(File spec, File jar) throws IOException
	{
		File writable = spec.getParentFile().getCanonicalFile();
		File out=new File(writable, "out");
		out.mkdir();

		String lines = Exec.toString("rpmbuild", "--define", "_rpmdir " + out, "--define",
										"_sourcedir " + jar.getParentFile().getCanonicalPath(), "-bb", spec.getAbsolutePath());

		log.info("rpmbuild output:\n{}", lines);

		/*
		BufferedReader br = new BufferedReader(new StringReader(lines));

		String line;

		while ((line = br.readLine()) != null)
		{
			if (line.startsWith(RPMBUILD_INDICATOR_PREFIX))
			{
				return new File(line.substring(RPMBUILD_INDICATOR_PREFIX.length()));
			}
		}

		throw new IOException("could not recover rpm name from rpmbuild output");
		*/

		File noarch=new File(out, "noarch");
		File[] onlyRpm = noarch.listFiles();

		if (onlyRpm==null || onlyRpm.length!=1)
		{
			throw new IOException("rpmbuild should have produced one RPM, but got: "+ Arrays.toString(onlyRpm));
		}

		return onlyRpm[0];
	}

	public
	boolean innerJarContainsEntry(ModuleKey moduleKey, String entryName) throws IOException
	{
		File innerJar=File.createTempFile(moduleKey.toString(), ".jar");

		try
		{
			Exec.andWait("bash", "-c", "rpm2cpio "+file+" | cpio -i --to-stdout '*.jar' > "+innerJar);

			JarFile jarFile=new JarFile(innerJar);
			ZipEntry entry = jarFile.getEntry(entryName);
			return (entry!=null);
		}
		finally
		{
			innerJar.delete();
		}
	}

	public
	Set<Dependency> listModuleDependencies(ModuleKey moduleKey) throws IOException
	{
		//NB: must match spec templates!
		String depsName=moduleKey.getModuleName()+".deps";

		File innerDeps=File.createTempFile(moduleKey.toString(), ".deps");

		try
		{
			String lines=Exec.toString("bash", "-c", "rpm2cpio "+file+" | cpio -i --to-stdout '*/"+depsName+"'");
			log.trace("listing {} deps:\n{}", moduleKey, lines);

			log.debug("MODULE_NAME={}", moduleKey.getModuleName());
			log.debug("MAJOR_VERSION={}", moduleKey.getMajorVersion());
			log.debug("MINOR_VERSION={}", moduleKey.getMinorVersion());
			log.debug("TO_STRING={}", moduleKey);

			return ModuleInfo.read(new ByteArrayInputStream(lines.getBytes()), moduleKey).getDependencies();
		}
		finally
		{
			innerDeps.delete();
		}
	}

	public
	boolean isSnapshot() throws IOException
	{
		return isSnapshot(getModuleKey());
	}

	public static
	boolean isSnapshot(ModuleKey moduleKey)
	{
		String minorVersion = moduleKey.getMinorVersion();
		return minorVersion!=null && minorVersion.contains("snap");
	}
}
