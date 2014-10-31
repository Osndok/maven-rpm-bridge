package com.github.osndok.mrb.grinder;

import com.github.osndok.mrb.grinder.util.Exec;
import org.osjava.jardiff.DiffCriteria;
import org.osjava.jardiff.PublicDiffCriteria;
import org.semver.Comparer;
import org.semver.Delta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.module.ModuleKey;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.text.ParseException;
import java.util.Collections;
import java.util.Set;

/**
 * Created by robert on 10/30/14.
 */
public
class RPM
{
	private static final String RPMBUILD_INDICATOR_PREFIX = "Wrote: ";

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
			String rpmName = Exec.toString("rpm", "--queryformat", "%{NAME}", "-qp", file.getAbsolutePath());
			try
			{
				moduleKey = ModuleKey.parseModuleKey(rpmName);
			}
			catch (ParseException e)
			{
				throw new RuntimeException("unparsable module key: " + rpmName);
			}
		}

		return moduleKey;
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

	public static
	File build(File spec, File jar) throws IOException
	{
		File writable = spec.getParentFile();

		String lines = Exec.toString("rpmbuild", "--define", "_rpmdir " + writable, "--define",
										"_sourcedir " + jar.getParentFile(), "-bb", spec.getAbsolutePath());

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
	}
}
