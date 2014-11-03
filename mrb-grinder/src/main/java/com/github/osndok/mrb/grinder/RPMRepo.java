package com.github.osndok.mrb.grinder;

import com.github.osndok.mrb.grinder.util.Exec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.module.Dependency;
import javax.module.ModuleKey;
import javax.module.Version;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

/**
 * Created by robert on 10/30/14.
 */
public
class RPMRepo
{
	private final
	File dir;

	private
	Registry registry;

	public
	RPMRepo(File dir) throws IOException
	{
		this.dir = dir;

		if (!this.dir.isDirectory() || !this.dir.canWrite())
		{
			throw new IOException("not a writable directory: " + dir);
		}

		File repodata = new File(dir, "repodata");

		if (!repodata.isDirectory())
		{
			throw new IOException(dir + ": does not look like a yum RPM repo (see 'man createrepo')");
		}
	}

	/**
	 * We start *looking* for the most specific major version collision, but we
	 * default to the *least* specific; this way, we will leave room to declare
	 * new (more specific) major versions as we find them, and won't jump back
	 * to the least specific once old/incompatible rpms are removed.
	 *
	 * @param mavenJar
	 * @return
	 */
	public
	ModuleKey mostSpecificCompatibleAndPreExistingVersion(MavenJar mavenJar) throws ObsoleteJarException, IOException
	{
		MavenInfo mavenInfo=mavenJar.getInfo();
		final
		Object[] bits= Version.split(mavenInfo.getVersion());

		ModuleKey guess=null;

		log.debug("split into {} bits", bits.length);

		for (int i=bits.length-1; i>0; i--)
		{
			guess = deriveVersionGuess(bits, i, mavenInfo);

			RPM rpm=get(guess);

			if (rpm==null)
			{
				//keep going...
			}
			else
			if (equalOrOlder(guess, rpm.getModuleKey()))
			{
				getRegistry().append(mavenInfo, guess);
				throw new ObsoleteJarException("already have "+guess+" @ "+rpm.getModuleKey().getMinorVersion()+", so don't need to install @ "+guess.getMinorVersion());
			}
			else
			if (rpm.innerJarIsCompatibleWithNewer(mavenJar))
			{
				return guess;
			}
		}

		if (guess==null)
		{
			throw new IllegalArgumentException("not enough version info: "+mavenInfo.getVersion());
		}
		else
		{
			//Probably majorversion="1", or "2"...
			return guess;
		}
	}

	private
	boolean equalOrOlder(ModuleKey guess, ModuleKey existing)
	{
		//Both fetchable by the same key... (equal module name & major version)
		assert(guess.equals(existing));

		String mGuess=guess.getMinorVersion();
		String mExisting=existing.getMinorVersion();
		int i=Version.compare(mGuess, mExisting);
		boolean retval=(i<=0);

		log.debug("For {}... is {} equalTo or olderThan {}? {} -> {}", guess, mGuess, mExisting, i, retval);

		return retval;
	}

	private static final Logger log = LoggerFactory.getLogger(RPMRepo.class);

	private
	RPM get(ModuleKey moduleKey)
	{
		final
		String rpmPrefix = Spec.RPM_NAME_PREFIX+moduleKey.toString();

		final
		String[] fileNames=dir.list(new FilenameFilter()
		{
			@Override
			public
			boolean accept(File file, String s)
			{
				return s.startsWith(rpmPrefix);
			}
		});

		if (fileNames==null || fileNames.length==0)
		{
			log.info("found nothing for rpm prefix: {}", rpmPrefix);
			return null;
		}

		log.info("found {} rpms for prefix: {}", fileNames.length, rpmPrefix);

		if (fileNames.length==1) return new RPM(new File(dir, fileNames[0]));

		String bestFileName=null;
		Version bestVersion=null;

		for (String fileName : fileNames)
		{
			if (bestFileName==null)
			{
				bestFileName=fileName;
				bestVersion=new Version(bestFileName);
			}
			else
			{
				Version thisVersion=new Version(fileName);

				if (thisVersion.isNewerThan(bestVersion))
				{
					log.debug("{} is newer than {}", fileName, bestFileName);
					bestFileName=fileName;
					bestVersion=thisVersion;
				}
				else
				{
					log.debug("{} is older than {}", fileName, bestFileName);
				}
			}
		}

		assert(bestFileName!=null);
		return new RPM(new File(dir, bestFileName));
	}

	private
	ModuleKey deriveVersionGuess(Object[] bits, int transition, MavenInfo mavenInfo)
	{
		String moduleName = mavenInfo.getModuleNameCandidate();

		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < transition; i++)
		{
			if (i != 0) sb.append('.');
			log.info("so far: {}/{} -> {}", i, transition, sb);
			sb.append(bits[i].toString());
		}

		String majorVersion = sb.toString();

		sb.delete(0, sb.length());

		for (int i = transition; i < bits.length; i++)
		{
			if (i != transition) sb.append('.');
			sb.append(bits[i].toString());
		}

		if (sb.length() == 0)
		{
			return new ModuleKey(moduleName, majorVersion, null);
		}
		else
		{
			return new ModuleKey(moduleName, majorVersion, sb.toString());
		}
	}

	public
	File getDirectory()
	{
		return dir;
	}

	public
	Registry getRegistry()
	{
		if (registry==null)
		{
			registry=new Registry(this);
		}
		return registry;
	}

	public
	void rebuildMetadata() throws IOException
	{
		Exec.andWait("createrepo", "--update", dir.getAbsolutePath());
	}

	public
	void add(File rpm) throws IOException
	{
		Exec.andWait("cp", "-v", rpm.getAbsolutePath(), dir.getAbsolutePath());
	}

	public
	Dependency getFullModuleDependency(ModuleKey requestor, MavenInfo mavenInfo) throws DependencyNotProcessedException, IOException
	{
		String majorVersion = getRegistry().getMajorVersionFor(mavenInfo, this);
		//The guess might actually be enough, but it would be better to verify the RPM's presence.
		ModuleKey guess = new ModuleKey(mavenInfo.getModuleNameCandidate(), majorVersion, null);
		RPM rpm = get(guess);
		return rpm.getModuleKey().asDependencyOf(requestor);
	}

}
