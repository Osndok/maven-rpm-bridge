package com.github.osndok.mrb.grinder;

import com.github.osndok.mrb.grinder.util.Exec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import sun.tools.jar.resources.jar;

import javax.module.ModuleKey;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Given a JAR file (or eventually a WAR file) grind it (and it's dependencies) into
 * RPMs, suitable for the flat-file javax-module system.
 */
public
class Main
{
	public static final String JAVAX_MODULE_EXEC = "mrb-grinder";

	public static final  Logger  log       = LoggerFactory.getLogger(Main.class);
	public static final  boolean RECURSIVE = (Boolean.valueOf(System.getProperty("RECURSIVE", "true")));
	private static final boolean DEBUG     = true;

	private final
	RPMRepo rpmRepo;

	public
	Main(String repoPath) throws IOException
	{
		this.rpmRepo = new RPMRepo(new File(repoPath));
	}

	public
	Main(RPMRepo rpmRepo)
	{
		this.rpmRepo = rpmRepo;
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
				if (arg.equals("tools"))
				{
					main.getSunTools();
				}
				else
				if (arg.indexOf(':')>0)
				{
					main.grindMavenArtifact(MavenInfo.parse(arg));
				}
				else
				{
					main.grind(new File(arg));
				}
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
		File parentFile = file.getParentFile();

		if (parentFile==null)
		{
			parentFile=new File(".");
		}

		if (!isWritableDirectory(parentFile))
		{
			throw new IOException("not a writable directory: "+parentFile);
		}

		if (isWarFile(file))
		{
			grindWar(file);
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
	boolean isWritableDirectory(File file)
	{
		return file.isDirectory() && file.canWrite();
	}

	private
	ModuleKey grindJar(File jar) throws IOException, ObsoleteJarException
	{
		MavenJar mavenJar = new MavenJar(jar);
		MavenInfo mavenInfo = mavenJar.getInfo(rpmRepo.getRegistry());

		return grindJar(jar, mavenJar, mavenInfo);
	}

	private
	ModuleKey grindJar(File jar, MavenJar mavenJar, MavenInfo mavenInfo) throws IOException, ObsoleteJarException
	{
		final
		Registry registry=rpmRepo.getRegistry();

		if (!mavenInfo.isSnapshot())
		{
			registry.shouldNotContain(mavenInfo);
		}

		ModuleKey moduleKey=rpmRepo.mostSpecificCompatibleAndPreExistingVersion(mavenJar);

		File spec=Spec.write(moduleKey, mavenJar, this);
		File rpm=RPM.build(spec, jar);

		if (mavenInfo.isSnapshot())
		{
			rpmRepo.add(rpm);

			//We need to check first, to avoid reduplicated entries...
			if (!registry.contains(mavenInfo))
			{
				registry.append(mavenInfo, moduleKey, jar);
			}
		}
		else
		{
			//We already checked via the shouldNotContain() call... albiet, a bit racy.
			rpmRepo.add(rpm);
			registry.append(mavenInfo, moduleKey, jar);
		}

		spec.delete();
		rpm.delete();

		rpmRepo.rebuildMetadata();

		return moduleKey;
	}

	private
	ModuleKey grindWar(File jar) throws IOException, ObsoleteJarException
	{
		MavenJar mavenJar = new MavenJar(jar);
		MavenInfo mavenInfo = mavenJar.getInfo();

		return grindWar(jar, mavenJar, mavenInfo);
	}

	private
	ModuleKey grindWar(File warFile, MavenJar mavenJar, MavenInfo mavenInfo) throws IOException, ObsoleteJarException
	{
		//(1) Expand the war to a temporary directory (such that we can re-jar it, or rpmbuild it?)
		final
		File dir=new File(Exec.toString("/usr/bin/mktemp", "-d", "/tmp/mrb-grinder-war-XXXXXXXX").trim());

		assert(dir.isDirectory());

		Exec.andWait("/usr/bin/unzip", warFile.getAbsolutePath(), "-d", dir.getAbsolutePath());

		//(2) Process all the declared dependencies from maven (this will ensure we can do a sha match)
		String pomPath=Exec.toString("find", dir.getAbsolutePath(), "-name", "pom.xml").trim();

		final
		Set<ModuleKey> declaredDependencies=new HashSet<ModuleKey>();

		final
		File pom=new File(pomPath);

		if (pomPath.isEmpty() || !pom.canRead())
		{
			throw new IOException("dne, or unreadable pom.xml in war file");
		}

		FileInputStream fis=new FileInputStream(pom);
		try
		{
			MavenPom mavenPom = new MavenPom(fis);

			for (MavenInfo info : mavenPom.getDependencies())
			{
				try
				{
					declaredDependencies.add(grindMavenArtifact(info));
				}
				catch (ObsoleteJarException e)
				{
					declaredDependencies.add(e.getModuleKey());
				}
			}
		}
		catch (RuntimeException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new IOException(e);
		}
		finally
		{
			fis.close();
		}

		//(3) convert every jar in the libs directory to a *POSSIBLE* module dependency (ignore javax-servlet?)
		final
		File lib=new File(dir, "WEB-INF/lib");

		final
		Set<ModuleKey> additionalDepsFromLibs=new HashSet<ModuleKey>();

		if (lib.isDirectory())
		{
			for (File jarFile : notNull(lib.listFiles()))
			{
				log.debug("processing: {}", jarFile);

				try
				{
					ModuleKey moduleKey;
					{
						try
						{
							moduleKey=grindJar(jarFile);
						}
						catch (ObsoleteJarException e)
						{
							moduleKey = e.getModuleKey();
						}

						jarFile.delete();
					}

					additionalDepsFromLibs.add(moduleKey);
				}
				/*
				catch (Exception e)
				{
					log.error("unable to modularize {}", jarFile.getName(), e);
				}
				*/
				finally
				{

				}
			}
		}

		log.info("got {} module deps from libs directory", additionalDepsFromLibs.size());

		//(4) convert the classes inside the war to a new module
		//(5a) HOW: can we get the production (or development?) port number from the WAR? pom.xml? and the uncompliant ones?
		//(5b) MAYBE: just install them tomcat-style with the module/version prefix?
		//(6)  write the spec/rpm to the repo
		throw new UnsupportedOperationException("unimplemented");
	}

	private
	boolean isJarFile(File file)
	{
		return file.getName().toLowerCase().endsWith(".jar");
	}

	private
	boolean isPomFile(File file)
	{
		return file.getName().equals("pom.xml");
	}

	private
	boolean isWarFile(File file)
	{
		return file.getName().toLowerCase().endsWith(".war");
	}

	public
	ModuleKey grindMavenArtifact(MavenInfo mavenInfo) throws IOException, ObsoleteJarException
	{
		if (looksLikeSunTools(mavenInfo))
		{
			return getSunTools();
		}

		final
		File dir=new File(Exec.toString("mktemp", "-d", "/tmp/mrb-maven-dep-copy-XXXXXXXX").trim());

		boolean success=false;

		try
		{
			log.info("downloading {} to {}", mavenInfo, dir);
			Exec.andWait("mvn","dependency:copy","-Dartifact="+mavenInfo, "-DoutputDirectory="+dir.getAbsolutePath());

			final
			File[] onlyOne = dir.listFiles();

			if (onlyOne==null || onlyOne.length!=1)
			{
				throw new IOException("expecting only one maven download, but got: "+ Arrays.toString(onlyOne));
			}

			final
			File file=onlyOne[0];

			final
			File pomFile=guessLocalPomPath(mavenInfo, file);

			MavenPom mavenPom=null;
			{
				if (pomFile.exists())
				{
					log.info("parsing pom: {}", pomFile);

					FileInputStream fis=new FileInputStream(pomFile);
					try
					{
						mavenPom=new MavenPom(mavenInfo, fis);
					}
					catch (ParserConfigurationException e)
					{
						e.printStackTrace();
					}
					catch (SAXException e)
					{
						e.printStackTrace();
					}
					finally
					{
						fis.close();
					}
				}
				else
				{
					log.warn("dne: {}", pomFile);
				}
			}

			final
			ModuleKey retval;

			if (isJarFile(file))
			{
				//Perhaps there is a benefit to having mavenInfo available externally? Can we grind deps that were manually put into maven?
				final
				MavenJar mavenJar = new MavenJar(file, mavenInfo);

				if (mavenPom!=null)
				{
					mavenJar.setMavenPom(mavenPom);
				}

				retval = grindJar(file, mavenJar, mavenInfo);
			}
			else
			if (isPomFile(file))
			{
				//TODO: check: I think that one can specify a parent/pom a a dep, and it adds all it's modules? correct?
				throw new UnsupportedOperationException("unimplemented: can't process pom file deps");
			}
			else
			if (isWarFile(file))
			{
				final
				MavenJar mavenJar = new MavenJar(file, mavenInfo);

				if (mavenPom!=null)
				{
					mavenJar.setMavenPom(mavenPom);
				}

				retval = grindWar(file, mavenJar, mavenInfo);
			}
			else
			{
				throw new UnsupportedOperationException("don't know of (or have a process for): "+file.getName());
			}

			success=true;

			return retval;
		}
		finally
		{
			if (!DEBUG || success)
			{
				new File(dir, "out/noarch").delete();

				for (File file : notNull(dir.listFiles()))
				{
					log.debug("delete: {}", file);
					file.delete();
				}

				log.debug("rmdir: {}", dir);
				dir.delete();
			}
		}
	}

	/**
	 * @url http://docs.codehaus.org/display/MAVEN/Repository+Layout+-+Final
	 * @param mavenInfo
	 * @param file
	 * @return
	 */
	static
	File guessLocalPomPath(MavenInfo mavenInfo, File file)
	{
		String home=System.getenv("HOME");
		String group=mavenInfo.getGroupId().replace('.', '/');
		String artifactId=mavenInfo.getArtifactId();
		String version=mavenInfo.getVersion();
		String jar=file.getName();
		String pom=jar.substring(0,jar.length()-3)+"pom";
		String path=home+"/.m2/repository/"+group+"/"+artifactId+"/"+version+"/"+pom;

		if (path.contains(".."))
		{
			throw new SecurityException("invalid path: "+path);
		}

		return new File(path);
	}

	/**
	 * @url http://docs.codehaus.org/display/MAVEN/Repository+Layout+-+Final
	 * @param mavenInfo
	 * @return
	 */
	static
	File guessLocalPomPath(MavenInfo mavenInfo)
	{
		String home=System.getenv("HOME");
		String group=mavenInfo.getGroupId().replace('.', '/');
		String artifactId=mavenInfo.getArtifactId();
		String version=mavenInfo.getVersion();

		final
		String pom;
		{
			if (mavenInfo.getClassifier() == null)
			{
				pom = String.format("%s-%s.pom", artifactId, version);
			}
			else
			{
				pom = String.format("%s-%s-%s.pom", artifactId, version, mavenInfo.getClassifier());
			}
		}

		String path=home+"/.m2/repository/"+group+"/"+artifactId+"/"+version+"/"+pom;

		if (path.contains(".."))
		{
			throw new SecurityException("invalid path: "+path);
		}

		return new File(path);
	}

	public
	boolean looksLikeSunTools(MavenInfo mavenInfo)
	{
		String groupId=mavenInfo.getGroupId();
		String artifactId=mavenInfo.getArtifactId();

		return (groupId.equals("com.sun") || groupId.equals("sun.jdk")) && mavenInfo.getArtifactId().equals("tools");
	}

	private
	File[] notNull(File[] files)
	{
		if (files==null)
		{
			return new File[0];
		}
		else
		{
			return files;
		}
	}

	public
	ModuleKey getSunTools() throws IOException
	{
		ModuleKey retval=new ModuleKey("com.sun-tools", "1", null);

		RPM rpm=rpmRepo.get(retval);

		if (rpm==null)
		{
			log.info("call for sun's tools.jar");

			File spec = Spec.writeSunTools(retval, rpmRepo);
			File rpmFile = RPM.build(spec, null);
			rpmRepo.add(rpmFile);
		}

		return retval;
	}

	public
	RPMRepo getRPMRepo()
	{
		return rpmRepo;
	}
}
