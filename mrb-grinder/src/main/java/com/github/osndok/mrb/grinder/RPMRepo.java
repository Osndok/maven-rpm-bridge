package com.github.osndok.mrb.grinder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	public
	RPMRepo(File dir) throws IOException
	{
		this.dir = dir;

		if (!this.dir.isDirectory() || !this.dir.canWrite())
		{
			throw new IOException("not a writable directory: " + dir);
		}

		File repodata=new File(dir, "repodata");

		if (!repodata.isDirectory())
		{
			throw new IOException(dir +": does not look like a yum RPM repo (see 'man createrepo')");
		}
	}

	/**
	 * We start *looking* for the most specific major version collision, but we
	 * default to the *least* specific; this way, we will leave room to declare
	 * new (more specific) major versions as we find them, and won't jump back
	 * to the least specific once old/incompatible rpms are removed.
	 *
	 * @param mavenInfo
	 * @return
	 */
	public
	ModuleKey mostSpecificCompatibleAndPreExistingVersion(MavenInfo mavenInfo) throws ObsoleteJarException
	{
		final
		Object[] bits= Version.split(mavenInfo.getVersion());

		ModuleKey guess=null;

		for (int i=bits.length; i>0; i++)
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
				throw new ObsoleteJarException("already have "+guess+" @ "+rpm.getModuleKey().getMinorVersion()+", so don't need to install @ "+guess.getMinorVersion());
			}
			else
			if (rpm.innerJarIsCompatibleWith(mavenInfo))
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
	RPM get(ModuleKey guess)
	{
		final
		String rpmPrefix = guess.toString();

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

		if (fileNames==null || fileNames.length==0) return null;
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
}
