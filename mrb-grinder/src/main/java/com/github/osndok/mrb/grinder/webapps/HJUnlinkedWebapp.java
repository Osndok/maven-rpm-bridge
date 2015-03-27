package com.github.osndok.mrb.grinder.webapps;

import com.github.osndok.mrb.grinder.api.SpecShard;
import com.github.osndok.mrb.grinder.api.SpecSourceAllocator;
import com.github.osndok.mrb.grinder.api.WarFileInfo;
import com.github.osndok.mrb.grinder.api.WarProcessingPlugin;

import java.io.File;
import java.util.*;

/**
 * Created by robert on 3/23/15.
 */
public
class HJUnlinkedWebapp extends AbstractHyperjettyWebappFunctions implements WarProcessingPlugin, SpecShard
{
	private transient
	String warBaseFileName;

	private transient
	WarFileInfo warFileInfo;

	private transient
	SpecSourceAllocator specSourceAllocator;

	@Override
	public
	String getSubPackageName()
	{
		return "hj1";
	}

	@Override
	public
	String getSubPackageDescription()
	{
		return warFileInfo.getMavenInfo().getArtifactId() + " war file placed in a path that the Hyperjetty servlet container is likely to find it, without any special or unconventional modifications or modularizations (i.e. phase 1 of modularization).";
	}

	@Override
	public
	Collection<String> getRpmRequiresLines()
	{
		//NB: while technically correct, we do *not* want to require hyperjetty, as that
		//    would restrict and frustrate the sysadmins (conflicts with tomcat, etc.).
		//BUT... without requiring hyperjetty, the user might not be available at install time...
		return Collections.singleton("hyperjetty");
	}

	@Override
	public
	Collection<String> getRpmBuildRequiresLines()
	{
		return buildRequiresHyperjettyConfigurator();
	}

	@Override
	public
	Collection<String> getFilePathsToPackage()
	{
		final
		List<String> list=new ArrayList<>(2);

		list.add("%attr(644, hyperjetty, hyperjetty) "+getConfigFilePath());
		list.add(directory + "/" + warBaseFileName);

		return list;
	}

	@Override
	public
	Map<String, String> getFileContentsByPath()
	{
		return hyperJettyConfigFileContentsByPath(warFileInfo.getModuleKey(), warFileInfo.getUntouchedWarFile());
	}

	//TODO: cache result
	@Override
	public
	Map<String, String> getScriptletBodiesByType()
	{
		final
		Map<String, String> retval=new HashMap<>(1);

		retval.put("install", getInstallPhase());
		retval.put("post", getPostInstallPhase(warFileInfo.getModuleKey()));

		return retval;
	}

	private
	String getInstallPhase()
	{
		final
		StringBuilder sb=new StringBuilder();

		sb.append("mkdir -p .").append(directory).append('\n');
		sb.append("cp -v ").append(specSourceAllocator.getUntouchedWarFile()).append(" .").append(directory).append('/').append(warBaseFileName).append('\n');

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

		this.servicePort=warFileInfo.getDeploymentPortNumber();

		this.warBaseFileName=servicePort+".war";

		return this;
	}
}
