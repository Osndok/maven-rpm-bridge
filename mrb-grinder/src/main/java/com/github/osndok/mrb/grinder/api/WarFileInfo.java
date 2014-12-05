package com.github.osndok.mrb.grinder.api;

import com.github.osndok.mrb.grinder.MavenInfo;
import com.github.osndok.mrb.grinder.MavenPom;
import com.github.osndok.mrb.grinder.RPM;
import com.github.osndok.mrb.grinder.RPMRepo;

import javax.module.Dependency;
import javax.module.Module;
import javax.module.ModuleKey;
import java.io.File;
import java.util.Collection;
import java.util.Map;

/**
 * Created by robert on 12/5/14.
 */
public
class WarFileInfo
{
	private final
	ModuleKey moduleKey;

	private final
	File warFile;

	private final
	File expandedWarDirectory;

	private final
	MavenPom mavenPom;

	private final
	MavenInfo mavenInfo;

	private final
	Collection<ModuleKey> explicitDependencies;

	private final
	Map<String, ModuleKey> libsDirectoryMapping;

	private final
	RPMRepo rpmRepo;

	public
	WarFileInfo(
				   ModuleKey moduleKey,
				   File warFile,
				   File expandedWarDirectory, MavenPom mavenPom, MavenInfo mavenInfo,
				   Collection<ModuleKey> explicitDependencies, Map<String, ModuleKey> libsDirectoryMapping,
				   RPMRepo rpmRepo
	)
	{
		this.moduleKey = moduleKey;
		this.warFile = warFile;
		this.expandedWarDirectory = expandedWarDirectory;
		this.mavenPom = mavenPom;
		this.mavenInfo = mavenInfo;
		this.explicitDependencies = explicitDependencies;
		this.libsDirectoryMapping = libsDirectoryMapping;
		this.rpmRepo = rpmRepo;
	}

	public
	ModuleKey getModuleKey()
	{
		return moduleKey;
	}

	public
	File getWarFile()
	{
		return warFile;
	}

	public
	MavenInfo getMavenInfo()
	{
		return mavenInfo;
	}

	public
	Map<String, ModuleKey> getLibsDirectoryMapping()
	{
		return libsDirectoryMapping;
	}

	public
	RPMRepo getRpmRepo()
	{
		return rpmRepo;
	}

	public
	File getExpandedWarDirectory()
	{
		return expandedWarDirectory;
	}

	public
	MavenPom getMavenPom()
	{
		return mavenPom;
	}

	public
	Collection<ModuleKey> getExplicitDependencies()
	{
		return explicitDependencies;
	}
}
