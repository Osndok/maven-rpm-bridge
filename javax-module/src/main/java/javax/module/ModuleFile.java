package javax.module;

import java.io.File;

/**
 * Created by robert on 10/29/14.
 */
public
class ModuleFile
{
	private final
	ModuleInfo moduleInfo;

	private final
	File file;

	public
	ModuleFile(ModuleInfo moduleInfo, File file)
	{
		this.moduleInfo = moduleInfo;
		this.file = file;
	}

	public
	ModuleInfo getModuleInfo()
	{
		return moduleInfo;
	}

	public
	ModuleKey getModuleKey()
	{
		return moduleInfo.getModuleKey();
	}

	public
	String getModuleName()
	{
		return moduleInfo.getModuleKey().getModuleName();
	}

	public
	String getMajorVersion()
	{
		return moduleInfo.getModuleKey().getMajorVersion();
	}

	public
	String getMinorVersion()
	{
		return moduleInfo.getModuleKey().getMinorVersion();
	}

	public
	File getFile()
	{
		return file;
	}
}
