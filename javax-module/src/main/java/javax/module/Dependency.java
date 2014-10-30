package javax.module;

/**
 * A Dependency is simply a ModuleKey that implicates another ModuleKey as having provoked
 * it's initial loading.
 */
public
class Dependency extends ModuleKey
{
	private final
	ModuleKey requestingModuleKey;

	public
	Dependency(String moduleName, String majorVersion, String minorVersion, ModuleKey requestingModuleKey)
	{
		super(moduleName, majorVersion, minorVersion);
		this.requestingModuleKey=requestingModuleKey;
	}

	public
	ModuleKey getRequestingModuleKey()
	{
		return requestingModuleKey;
	}
}
