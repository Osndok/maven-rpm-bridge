package javax.module;

import javax.module.util.ModuleKey;

/**
 * Created by robert on 10/29/14.
 */
public
interface Repo
{
	Module getModuleFile(ModuleKey moduleKey);
	Module getModuleFile(String moduleName, String majorVersion);
}
