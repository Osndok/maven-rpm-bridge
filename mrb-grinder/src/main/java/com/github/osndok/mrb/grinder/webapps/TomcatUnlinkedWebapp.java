package com.github.osndok.mrb.grinder.webapps;

import com.github.osndok.mrb.grinder.api.SpecShard;
import com.github.osndok.mrb.grinder.api.SpecSourceAllocator;
import com.github.osndok.mrb.grinder.api.WarFileInfo;
import com.github.osndok.mrb.grinder.api.WarProcessingPlugin;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by robert on 3/23/15.
 */
public
class TomcatUnlinkedWebapp implements WarProcessingPlugin, SpecShard
{
	private final
	int tomcatMajorVersionNumber;

	private final
	String directory;

	private transient
	String warBaseFileName;

	private transient
	WarFileInfo warFileInfo;

	private transient
	SpecSourceAllocator specSourceAllocator;

	public
	TomcatUnlinkedWebapp(int tomcatMajorVersionNumber)
	{
		this.tomcatMajorVersionNumber = tomcatMajorVersionNumber;
		this.directory="/usr/share/tomcat"+tomcatMajorVersionNumber+"/webapps";
	}

	@Override
	public
	String getSubPackageName()
	{
		return "tc" + tomcatMajorVersionNumber;
	}

	@Override
	public
	String getSubPackageDescription()
	{
		return warFileInfo.getMavenInfo().getArtifactId()+" war file placed in a path that the Apache Tomcat "+tomcatMajorVersionNumber+" servlet container is likely to find it, without any special or unconventional modifications or modularizations (i.e. a good fallback).";
	}

	@Override
	public
	Collection<String> getRpmRequiresLines()
	{
		//NB: while technically correct, we do *not* want to require tomcat+tomcatMajorVersionNumber, as that
		//    would restrict and frustrate the sysadmins (different versions of tomcat conflict with each other,
		//    they may want this webapp as a fallback, etc).
		return null;
	}

	@Override
	public
	Collection<String> getRpmBuildRequiresLines()
	{
		return null;
	}

	@Override
	public
	Collection<String> getFilePathsToPackage()
	{
		return Collections.singleton(directory+"/"+warBaseFileName);
	}

	@Override
	public
	Map<String, String> getFileContentsByPath()
	{
		return null;
	}

	@Override
	public
	Map<String, String> getScriptletBodiesByType()
	{
		final
		Map<String, String> retval=new HashMap<>(1);

		retval.put("install", getInstallPhase());

		return retval;
	}

	private
	String getInstallPhase()
	{
		final
		StringBuilder sb=new StringBuilder();

		sb.append("mkdir -p .").append(directory).append('\n');
		sb.append("cp -v  ").append(specSourceAllocator.getUntouchedWarFile()).append(" .").append(directory).append('\n');

		return sb.toString();
	}

	@Override
	public
	SpecShard getSpecShard(
							  WarFileInfo warFileInfo, SpecSourceAllocator specSourceAllocator
	)
	{
		this.warFileInfo=warFileInfo;
		this.specSourceAllocator=specSourceAllocator;

		this.warBaseFileName=warFileInfo.getModuleKey().getModuleName()+".war";

		return this;
	}
}
