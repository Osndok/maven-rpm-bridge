package javax.module;

/**
 * Created by robert on 10/29/14.
 */
public
interface Repo
{
	ModuleFile getModuleFile(ModuleKey moduleKey);
	ModuleFile getModuleFile(String moduleName, String majorVersion);
}
