package com.github.osndok.mrb.grinder;

/**
 * Created by robert on 10/31/14.
 */
public
class DependencyNotProcessedException extends Exception
{
	private final
	MavenInfo mavenInfo;

	public
	DependencyNotProcessedException(MavenInfo mavenInfo)
	{
		this.mavenInfo = mavenInfo;
	}

	public
	MavenInfo getMavenInfo()
	{
		return mavenInfo;
	}
}
