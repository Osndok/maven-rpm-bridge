package javax.module;

import javax.module.util.ModuleKey;
import java.io.IOException;

/**
 * Created by robert on 10/30/14.
 */
public
class ModuleLoadException extends IOException
{
	ModuleLoadException(ModuleKey moduleKey, Throwable cause)
	{
		super(moduleKey.toString());
		initCause(cause);
	}

	ModuleLoadException(String message, Throwable cause)
	{
		super(message);
		initCause(cause);
	}
}
