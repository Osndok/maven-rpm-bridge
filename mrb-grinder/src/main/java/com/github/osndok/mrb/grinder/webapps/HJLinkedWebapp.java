package com.github.osndok.mrb.grinder.webapps;

import com.github.osndok.mrb.grinder.api.SpecShard;
import com.github.osndok.mrb.grinder.api.SpecSourceAllocator;
import com.github.osndok.mrb.grinder.api.WarFileInfo;
import com.github.osndok.mrb.grinder.api.WarProcessingPlugin;
import com.github.osndok.mrb.grinder.rpm.RPMSpec;

import javax.module.util.ModuleKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by robert on 3/23/15.
 */
public
class HJLinkedWebapp extends AbstractHyperjettyWebappFunctions implements WarProcessingPlugin, SpecShard
{
	private transient
	WarFileInfo warFileInfo;

	private transient
	SpecSourceAllocator specSourceAllocator;

	private transient
	String warBaseDirectoryName;

	@Override
	public
	String getSubPackageName()
	{
		return "hj2";
	}

	@Override
	public
	String getSubPackageDescription()
	{
		return warFileInfo.getMavenInfo().getArtifactId() + " war 'directory' in a place where hyperjetty can locate it with 1st-phase modularizations in place (i.e. links to rpm-packaged dependencies, phase 2 of modularization).";
	}

	@Override
	public
	Collection<String> getRpmRequiresLines()
	{
		//NB: while technically correct, we do *not* want to require hyperjetty, as that
		//    would restrict and frustrate the sysadmins (conflicts with tomcat, etc.).
		//BUT... without requiring hyperjetty, the user might not be available at install time...
		List<String> c=new ArrayList();
		c.add("hyperjetty");
		c.add(RPMSpec.RPM_NAME_PREFIX+warFileInfo.getModuleKey());
		return c;
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
		List<String> list=new ArrayList<String>(2);

		list.add("%attr(644, hyperjetty, hyperjetty) "+getConfigFilePath());
		list.add(directory + "/" + warBaseDirectoryName);

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
		Map<String, String> retval=new HashMap<String, String>(1);

		retval.put("install", getInstallPhase());
		retval.put("post", getPostInstallPhase(warFileInfo.getModuleKey()));
		retval.put("postun", getPostUninstallPhase(warFileInfo.getModuleKey()));

		return retval;
	}

	private
	String getInstallPhase()
	{
		final
		StringBuilder sb=new StringBuilder();

		sb.append("mkdir -p .").append(directory).append('/').append(warBaseDirectoryName).append('\n');
		sb.append("pushd    .").append(directory).append('/').append(warBaseDirectoryName).append('\n');
		sb.append("jar xf ").append(specSourceAllocator.getUntouchedWarFile()).append('\n');

		final
		Map<String, ModuleKey> modulesByLibName = warFileInfo.getLibsDirectoryMapping();

		if (!modulesByLibName.isEmpty())
		{
			//HERE: the primary difference for a 'linked' webapp... the dependencies are replaced by mrb linkages.
			sb.append("pushd WEB-INF/lib\n");

			for (Map.Entry<String, ModuleKey> moduleKeyEntry : modulesByLibName.entrySet())
			{
				final
				String libName=moduleKeyEntry.getKey();

				final
				ModuleKey moduleKey=moduleKeyEntry.getValue();

				sb.append("rm ").append(libName).append('\n');
				sb.append("ln -s ").append(specSourceAllocator.getActualModularJarFile(moduleKey)).append(' ').append(libName).append('\n');
			}

			sb.append("popd #WEB-INF/lib\n");
		}

		sb.append("popd\n"); //webapp directory

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

		this.warBaseDirectoryName = servicePort+".dir";

		return this;
	}
}
