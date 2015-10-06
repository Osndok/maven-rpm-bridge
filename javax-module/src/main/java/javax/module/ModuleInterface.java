package javax.module;

import javax.module.util.ModuleKey;

/**
 * Provides a mechanism by which well-written & module-aware code can further interact
 * with the module system. To utilize these functionalities, write a class which
 * implements this interface with a no-args constructor and put it's class name in
 * module properties under "javax.module.interface"
 */
public
interface ModuleInterface
{
	public static final String PROPERTY_KEY="javax.module.interface";

	public void moduleLoaded();
	public void moduleUnloaded();

	public void checkAccess(Class c, ModuleKey foreignModule, boolean sameContext) throws ModuleAccessDeniedException;
}
