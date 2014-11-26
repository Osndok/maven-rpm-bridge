package com.github.osndok.mrb.grinder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.module.ModuleKey;
import java.io.*;

/**
 * The "Registry" is currently a flat file stashed in the target rpm repo that contains all the
 * maven artifact mappings (to major version numbers) that this software has successfully grinded
 * or obsoleted (b/c of a newer, pre-existing, but compatible version).
 *
 * This is required to solve an otherwise-insurmountable problem of "given a modules version number,
 * what is it's 'compatible' major version number [given the history of created rpms]".
 *
 * TODO: improve Registry performance on large data-sets with an embedded database, rather than scanning a flat file.
 */
public
class Registry
{
	private final
	File file;

	public
	Registry(RPMRepo rpmRepo)
	{
		this.file=new File(rpmRepo.getDirectory(), "maven-rpms.map");
	}

	public
	boolean contains(MavenInfo mavenInfo) throws IOException
	{
		if (!file.exists())
		{
			//file.createNewFile();
			return false;
		}

		BufferedReader br=new BufferedReader(new FileReader(file));
		try
		{
			String line;
			while ((line=br.readLine())!=null)
			{
				if (mavenInfo.majorVersionFromParsableLineMatch(line)!=null)
				{
					return true;
				}
			}
		}
		finally
		{
			br.close();
		}

		return false;
	}

	public
	void shouldNotContain(MavenInfo mavenInfo) throws ObsoleteJarException, IOException
	{
		if (!file.exists())
		{
			//file.createNewFile();
			return;
		}

		BufferedReader br=new BufferedReader(new FileReader(file));
		try
		{
			String line;
			while ((line=br.readLine())!=null)
			{
				if (mavenInfo.majorVersionFromParsableLineMatch(line)!=null)
				{
					throw new ObsoleteJarException(mavenInfo+" is already in Registry: "+file);
				}
			}
		}
		finally
		{
			br.close();
		}
	}

	public
	void append(MavenInfo mavenInfo, ModuleKey moduleKey) throws IOException
	{
		final
		String line=mavenInfo.toParsableLine(moduleKey.getMajorVersion());

		final
		boolean append=true;

		final
		FileWriter out=new FileWriter(file, append);

		try
		{
			//We rely on the fact that short writes (<512-4096 bytes) are atomic if "O_APPEND" is true.
			out.write(line);
		}
		finally
		{
			out.close();
		}
	}

	public
	String getMajorVersionFor(MavenInfo mavenInfo, RPMRepo rpmRepo) throws DependencyNotProcessedException, IOException
	{
		String retval=majorFromFirstLineThatMatches(mavenInfo);

		if (retval!=null) return retval;

		log.warn("unable to locate dependency: {}", mavenInfo);

		//TODO: make this into a depth counter (e.g. to bomb out [eventually] on circular dependencies), and on by default?
		if (Main.RECURSIVE)
		{
			try
			{
				return new Main(rpmRepo).grindMavenArtifact(mavenInfo).getMajorVersion();
			}
			catch (ObsoleteJarException e)
			{
				//This can happen, for example, if we have a newer jar than the given dep... so it's not always an error.
				log.warn("did not find item in registry, but claimedly obsolete: {}", e.toString());

				//Is it in the registry now?
				retval=majorFromFirstLineThatMatches(mavenInfo);

				if (retval==null)
				{
					throw new IOException("did not find item in registry (before *or* after), but obsolete?", e);
				}
				else
				{
					return retval;
				}
			}
		}
		else
		{
			throw new DependencyNotProcessedException(mavenInfo);
		}
	}

	private
	String majorFromFirstLineThatMatches(MavenInfo mavenInfo) throws IOException
	{
		BufferedReader br=new BufferedReader(new FileReader(file));
		try
		{
			String line;
			while ((line=br.readLine())!=null)
			{
				String retval=mavenInfo.majorVersionFromParsableLineMatch(line);

				if (retval!=null)
				{
					return retval;
				}
			}
		}
		finally
		{
			br.close();
		}

		return null;
	}

	private static final Logger log = LoggerFactory.getLogger(Registry.class);
}
