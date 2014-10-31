package com.github.osndok.mrb.grinder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.module.ModuleKey;
import java.io.File;
import java.io.IOException;

/**
 * Given a JAR file (or eventually a WAR file) grind it (and it's dependencies) into
 * RPMs, suitable for the flat-file javax-module system.
 */
public
class Main
{
	public static final String JAVAX_MODULE_EXEC = "mrb-grinder";

	public static final Logger log = LoggerFactory.getLogger(Main.class);

	private final
	RPMRepo rpmRepo;

	public
	Main(String repoPath) throws IOException
	{
		this.rpmRepo=new RPMRepo(new File(repoPath));
	}

	public static
	void main(String[] args) throws IOException
	{
		Main main = new Main(getRepoProperty());

		int status = 0;

		for (String arg : args)
		{
			try
			{
				main.grind(new File(arg));
			}
			catch (Exception e)
			{
				log.error("unable to grind: {}", arg, e);
				status = 1;
			}
		}

		System.exit(status);
	}

	private static
	String getRepoProperty()
	{
		return System.getProperty("repo", "/repo/mrb");
	}

	private
	void grind(File file) throws IOException, ObsoleteJarException
	{
		if (isWarFile(file))
		{
			throw new UnsupportedOperationException("WARN files will be supported *soon*, but not yet");
		}
		else
		if (isJarFile(file))
		{
			grindJar(file);
		}
		else
		{
			throw new UnsupportedOperationException("unknown file type: "+file);
		}
	}

	private
	void grindJar(File file) throws IOException, ObsoleteJarException
	{
		MavenJar mavenJar=new MavenJar(file);
		MavenInfo mavenInfo=mavenJar.getInfo();
		ModuleKey moduleKey=rpmRepo.mostSpecificCompatibleAndPreExistingVersion(mavenInfo);
		File spec=Spec.write(moduleKey, mavenJar);
		File rpm=buildRpm(spec);
		addToRepository(rpm);
		rebuildRepository();
	}

	private
	boolean isJarFile(File file)
	{
		return file.getName().toLowerCase().endsWith(".jar");
	}

	private
	boolean isWarFile(File file)
	{
		return file.getName().toLowerCase().endsWith(".war");
	}
}
