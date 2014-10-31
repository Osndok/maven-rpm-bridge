package com.github.osndok.mrb.grinder;

import com.github.osndok.mrb.grinder.util.Exec;

import javax.module.ModuleKey;
import java.io.File;
import java.text.ParseException;

/**
 * Created by robert on 10/30/14.
 */
public
class RPM
{
	private final
	File file;

	private
	ModuleKey moduleKey;

	public
	RPM(File file)
	{
		this.file = file;
	}

	public
	ModuleKey getModuleKey()
	{
		if (moduleKey==null)
		{
			String rpmName= Exec.toString("rpm", "--queryformat", "%{NAME}", "-qp", file.getAbsolutePath());
			try
			{
				moduleKey=ModuleKey.parseModuleKey(rpmName);
			}
			catch (ParseException e)
			{
				throw new RuntimeException("unparsable module key: "+rpmName);
			}
		}

		return moduleKey;
	}

	public
	boolean innerJarIsCompatibleWith(MavenInfo mavenInfo)
	{

	}
}
