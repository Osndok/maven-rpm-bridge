package javax.module;

import java.io.File;

/**
 * Created by robert on 10/29/14.
 */
public
class ModuleFile
{
	private final
	ModuleKey moduleKey;

	private final
	File file;

	public
	ModuleFile(ModuleKey moduleKey, File file)
	{
		this.moduleKey = moduleKey;
		this.file = file;
	}

	public
	ModuleKey getModuleKey()
	{
		return moduleKey;
	}

	public
	File getFile()
	{
		return file;
	}
}
