package com.github.osndok.mrb.grinder;

import javax.module.ModuleKey;
import java.io.*;

/**
 * The "Registry" is currently a flat file stashed in the target rpm repo that contains all the
 * maven artifact mappings (to major version numbers) that this software has successfully grinded
 * or obsoleted (b/c of a newer, pre-existing, but compatible version).
 *
 * This is required to solve an otherwise-insurmountable problem of "given a modules version number,
 * what is it's 'compatible' major version number [given the history of created rpms]".
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
	void shouldNotContain(MavenInfo mavenInfo) throws ObsoleteJarException, IOException
	{
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
}
