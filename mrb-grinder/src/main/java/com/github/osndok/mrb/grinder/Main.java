package com.github.osndok.mrb.grinder;

import com.github.osndok.mrb.grinder.api.SpecShard;
import com.github.osndok.mrb.grinder.api.WarFileInfo;
import com.github.osndok.mrb.grinder.api.WarProcessingPlugin;
import com.github.osndok.mrb.grinder.rpm.RPM;
import com.github.osndok.mrb.grinder.rpm.RPMManifold;
import com.github.osndok.mrb.grinder.rpm.RPMRegistry;
import com.github.osndok.mrb.grinder.rpm.RPMRepo;
import com.github.osndok.mrb.grinder.rpm.RPMSpec;
import com.github.osndok.mrb.grinder.util.Exec;
import com.github.osndok.mrb.grinder.util.SpecSourceAllocatorImpl;
import com.github.osndok.mrb.grinder.webapps.HJLinkedWebapp;
import com.github.osndok.mrb.grinder.webapps.HJUnlinkedWebapp;
import com.github.osndok.mrb.grinder.webapps.TomcatUnlinkedWebapp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.module.CommandLineTool;
import javax.module.util.ModuleKey;
import javax.module.Plugins;
import javax.module.util.SystemPropertyOrEnvironment;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Given a JAR file (or eventually a WAR file) grind it (and it's dependencies) into
 * RPMs, suitable for the flat-file javax-module system.
 */
@CommandLineTool(name = "mrb-grinder")
public
class Main
{
	public static final  Logger  log       = LoggerFactory.getLogger(Main.class);
	public static final  boolean RECURSIVE = SystemPropertyOrEnvironment.getBoolean("RECURSIVE", true);
	private static final boolean DEBUG     = SystemPropertyOrEnvironment.getBoolean("DEBUG", true);

	//TODO: atm, "force" may be construed two ways... specific to the top-level grinding (replace this jar), or global ("I just want it to work"). Maybe split it?
	public static boolean FORCE = false;

	//TODO: let us "eat our own dog food" and rid ourselves of this main method!!! and 'Main' class name!!!
	public static
	void main(String[] args) throws IOException
	{
		final
		Main main = new Main();

		int status = 0;

		for (String arg : args)
		{
			try
			{
				if (arg.equals("--force"))
				{
					FORCE = true;
				}
				else if (arg.equals("tools"))
				{
					main.getSunTools();
				}
				else if (arg.indexOf(':') > 0)
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

	private
	void grind(File file) throws IOException, ObsoleteJarException
	{
		if (!file.exists())
		{
			throw new FileNotFoundException(file.toString());
		}

		final
		File tempDirectory;
		{
			File parentFile = file.getParentFile();

			if (parentFile == null)
			{
				log.warn("parentFile is null");
				parentFile = new File(".");
			}

			if (isWritableDirectory(parentFile))
			{
				//Not needed...
				tempDirectory = null;
			}
			else
			{
				log.debug("not a writable directory: {}", parentFile);

				tempDirectory = new File(Exec.toString("mktemp", "-d", "/tmp/mrb-grinder-XXXXXXXX").trim());

				log.debug("created: {}", tempDirectory);

				if (isWritableDirectory(tempDirectory))
				{
					final
					File newFile = new File(tempDirectory, file.getName());
					{
						Exec.andWait("cp", "-v", file.getAbsolutePath(), newFile.getAbsolutePath());
					}

					file = newFile;
				}
				else
				{
					throw new IllegalStateException("unable to create a writable temporary directory");
				}
			}
		}

		try
		{
			if (isWarFile(file))
			{
				grindWar(file);
			}
			else if (isJarFile(file))
			{
				grindJar(file);
			}
			else
			{
				throw new UnsupportedOperationException("unknown file type: " + file);
			}
		}
		finally
		{
			if (tempDirectory!=null)
			{
				Exec.andWait("rm", "-rf", tempDirectory.getAbsolutePath());
			}
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
		MavenInfo mavenInfo = mavenJar.getInfo();

		return grindJar(jar, mavenJar, mavenInfo, null, null);
	}

	private
	ModuleKey grindJar(File jar, MavenJar mavenJar, MavenInfo mavenInfo, File warFile, Collection<SpecShard> extraShards) throws IOException, ObsoleteJarException
	{
		final
		RPMRepo rpmRepo= RPMManifold.getRepoFor(mavenInfo);

		final
		RPMRegistry rpmRegistry = rpmRepo.getRpmRegistry();

		boolean avoidCompatibilityCheck=(mavenInfo.isSnapshot() || FORCE);

		if (!avoidCompatibilityCheck)
		{
			rpmRegistry.shouldNotContain(mavenInfo);
		}

		ModuleKey moduleKey=rpmRepo.mostSpecificCompatibleAndPreExistingVersion(mavenJar, avoidCompatibilityCheck);

		File spec= RPMSpec.write(moduleKey, mavenJar, this, warFile, extraShards);

		Exec.andWait("cat", spec.getAbsolutePath());

		File[] rpms= RPM.buildMany(spec, jar, warFile);

		for (File rpm : rpms)
		{
			rpmRepo.add(rpm);
		}

		if (avoidCompatibilityCheck)
		{
			//TODO: when force-adding a jar, shouldn't we remove (or overwrite) the entry instead of dropping it? e.g. it surly has a different jar-hash?
			//We need to check first, to avoid reduplicated entries...
			if (!rpmRegistry.contains(mavenInfo))
			{
				rpmRegistry.append(mavenInfo, moduleKey, jar);
			}
		}
		else
		{
			//We already checked via the shouldNotContain() call... albiet, a bit racy.
			rpmRegistry.append(mavenInfo, moduleKey, jar);
		}

		spec.delete();

		for (File rpm : rpms)
		{
			rpm.delete();
		}

		rpmRepo.rebuildMetadata();

		return moduleKey;
	}

	/*
	private
	ModuleKey grindWar(File jar) throws IOException, ObsoleteJarException
	{
		MavenJar mavenJar = new MavenJar(jar);
		MavenInfo mavenInfo = mavenJar.getInfo();

		return grindWar(jar, mavenJar, mavenInfo);
	}
	*/

	/**
	 * TODO: BUG: when grinding a war file with dependencies in it's parent-pom (which we don't have acces too), those will not be linked up correctly.
	 *
	 * @param warFile
	 * @return
	 * @throws IOException
	 * @throws ObsoleteJarException
	 */
	private
	ModuleKey grindWar(File warFile /*, MavenJar mavenJar, MavenInfo mavenInfo*/) throws IOException, ObsoleteJarException
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

		MavenPom mavenPom;
		FileInputStream fis=new FileInputStream(pom);
		try
		{
			mavenPom = new MavenPom(fis);

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

		MavenInfo mavenInfo=mavenPom.getMavenInfo();

		String warGroupId=mavenInfo.getGroupId();
		String warArtifactId=mavenInfo.getArtifactId();
		String warVersion=mavenInfo.getVersion();


		//(3) convert every jar in the libs directory to a *POSSIBLE* module dependency (ignore javax-servlet?)
		final
		File lib=new File(dir, "WEB-INF/lib");

		final
		Map<String,ModuleKey> libDependencyMapping = new HashMap<String, ModuleKey>();
		//Set<ModuleKey> additionalDepsFromLibs=new HashSet<ModuleKey>();

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

					libDependencyMapping.put(jarFile.getName(), moduleKey);
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

		log.info("got {} module deps from libs directory", libDependencyMapping.size());

		//(4) convert the classes inside the war to a new jar that will become the outer module

		final
		File classes=new File(dir, "WEB-INF/classes");

		final
		File jar=File.createTempFile("mrb-war-classes-"+warArtifactId+"-", ".jar");
		{
			final
			List<String> command=new ArrayList<String>();

			command.add("/usr/bin/jar");
			command.add("cf");
			command.add(jar.getAbsolutePath());
			command.add("-C");
			command.add(dir.getAbsolutePath());
			command.add("META-INF");

			for (String s : classes.list())
			{
				command.add("-C");
				command.add(classes.getAbsolutePath());
				command.add(s);
			}

			Exec.andWait(command.toArray(new String[command.size()]));

			log.info("built classes-only jar: {}", jar);
		}

		//(5) try and guess (in a heavily race-prone way) what the final module key will be :-(

		final
		boolean avoidCompatibilityCheck=(mavenInfo.isSnapshot() || FORCE);

		final
		RPMRepo rpmRepo=RPMManifold.getRepoFor(mavenInfo);

		final
		MavenJar mavenJar=new MavenJar(jar, mavenInfo);

		final
		ModuleKey moduleKey=rpmRepo.mostSpecificCompatibleAndPreExistingVersion(mavenJar, avoidCompatibilityCheck);


		//(5) Delegate to the war plugins to generate deployment-specific sub-packages.
		List<SpecShard> shards=new ArrayList<SpecShard>();
		{
			SpecSourceAllocatorImpl sourceAllocator = new SpecSourceAllocatorImpl();
			WarFileInfo warFileInfo=new WarFileInfo(moduleKey, warFile, dir, mavenPom, mavenInfo, declaredDependencies,
													   libDependencyMapping, rpmRepo);

			for (WarProcessingPlugin plugin : getDefaultPlugins())
			{
				shards.add(plugin.getSpecShard(warFileInfo, sourceAllocator));
			}

			Collection<WarProcessingPlugin> modularPlugins;

			try
			{
				modularPlugins=Plugins.load(WarProcessingPlugin.class);
			}
			catch (IllegalArgumentException e)
			{
				log.warn("mrb is probably running from a non-modular context", e);
				modularPlugins=Collections.emptyList();
			}

			for (WarProcessingPlugin plugin : modularPlugins)
			{
				shards.add(plugin.getSpecShard(warFileInfo, sourceAllocator));
			}

			if (sourceAllocator.hasAnyEntries())
			{
				sourceAllocator.addWarFileEntry(warFile);
				shards.add(sourceAllocator.asSpecShard());
			}
			else
			if (shards.isEmpty())
			{
				shards=null;
			}
		}

		//(6)  write the spec/rpm to the repo, hoping that the repo has not changed and that the redundant compat check will be swift.

		return grindJar(jar, mavenJar, mavenInfo, warFile, shards);
	}

	private static
	List<WarProcessingPlugin> defaultPlugins;

	static
	List<WarProcessingPlugin> getDefaultPlugins()
	{
		if (defaultPlugins==null)
		{
			defaultPlugins=new ArrayList<WarProcessingPlugin>(4);

			if (!Boolean.getBoolean("NO_TOMCAT"))
			{
				int tomcatVersion = Integer.getInteger("TOMCAT_VERSION", 7);

				if (!Boolean.getBoolean("NO_UNLINKED"))
				{
					//Phase 0 - tomcat fallback
					//The mechanism that DV currently prefers, and the most conventional & basic
					defaultPlugins.add(new TomcatUnlinkedWebapp(tomcatVersion));
				}

				if (!Boolean.getBoolean("NO_LINKED"))
				{
					//Phase 3
					//Once the HJ1/Linked is ready, it should be easy to create a linked tomcat version.
					//NB: might require a config tweak to allow symlinked jar dependency files
					//defaultPlugins.add(new TomcatLinkedWebapp(tomcatVersion));
				}
			}

			if (!Boolean.getBoolean("NO_HJ"))
			{
				if (!Boolean.getBoolean("NO_UNLINKED"))
				{
					//Phase 1 - feature parity
					defaultPlugins.add(new HJUnlinkedWebapp());
				}

				if (!Boolean.getBoolean("NO_LINKED"))
				{
					//Phase 2 - non-modular enhanced dependency linkage
					defaultPlugins.add(new HJLinkedWebapp());
				}

				if (!Boolean.getBoolean("NO_MODULAR_WEBAPP"))
				{
					//Phase 4 - modular dependency linkage (requires an ounce of HJ help)
					//defaultPlugins.add(new HJModularWebapp());
				}
			}
		}

		return defaultPlugins;
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
			Exec.andWait("mvn","dependency:copy","-Dartifact="+mavenInfo.getDependencyCopyString(), "-DoutputDirectory="+dir.getAbsolutePath());

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

				if (mavenPom==null)
				{
					/*
					If we don't already have a pom, and if there is not one in the JAR file, then we
					will probably have runtime issues. What's more... we are currently in the best place
					to deal with this situation.
					 */
					try
					{
						mavenPom=mavenJar.getMavenPom();
					}
					catch (JarHasNoPomException e)
					{
						try
						{
							mavenPom=MavenPom.fetchedFromMavenCentral(mavenInfo);
						}
						catch (RuntimeException e2)
						{
							if (FORCE || acceptablyFunctionalWithoutPomDependencyInfo(mavenInfo))
							{
								log.error("no pom available, will likely have runtime issues: {}", mavenInfo, e2);
							}
							else
							{
								throw e2;
							}
						}
						catch (Exception e2)
						{
							if (FORCE || acceptablyFunctionalWithoutPomDependencyInfo(mavenInfo))
							{
								log.error("no pom available, will likely have runtime issues: {}", mavenInfo, e2);
							}
							else
							{
								throw new RuntimeException(e2);
							}
						}
					}
				}
				else
				{
					log.debug("injecting pre-existing mavenPom");
					mavenJar.setMavenPom(mavenPom);
				}

				retval = grindJar(file, mavenJar, mavenInfo, null, null);
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
				/*
				final
				MavenJar mavenJar = new MavenJar(file, mavenInfo);

				if (mavenPom!=null)
				{
					mavenJar.setMavenPom(mavenPom);
				}

				retval = grindWar(file, mavenJar, mavenInfo);
				*/
				throw new UnsupportedOperationException("grinding a war file by maven coordinates is not supported at this time");
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

	private
	boolean acceptablyFunctionalWithoutPomDependencyInfo(MavenInfo mavenInfo)
	{
		//TODO: probably sun tools, asm, and a few others... probably calls for regrind-the-world
		return false;
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

		log.debug("local pom? {}", path);
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

		return (groupId.equals("com.sun") || groupId.equals("sun.jdk")) && artifactId.equals("tools");
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
		final
		MavenInfo mavenInfo=new MavenInfo("com.sun", "sun-tools", "1");

		final
		ModuleKey retval=new ModuleKey("sun-tools", "1", null);

		final
		RPMRepo rpmRepo=RPMManifold.getRepoFor(mavenInfo);

		final
		RPM rpm=rpmRepo.get(retval);

		if (rpm==null)
		{
			log.info("call for sun's tools.jar");

			File spec = RPMSpec.writeSunTools(retval, rpmRepo);

			File rpmFile = RPM.buildOne(spec, null, null);
			try
			{
				rpmRepo.add(rpmFile);
			}
			finally
			{
				rpmFile.delete();
			}

			//NB: only deletes the spec on success...
			spec.delete();
		}

		return retval;
	}
}
