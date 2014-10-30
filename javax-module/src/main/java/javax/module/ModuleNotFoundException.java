package javax.module;

/**
 * Created by robert on 10/30/14.
 */
public
class ModuleNotFoundException extends Exception
{
	ModuleNotFoundException(String message) { super(message); }
	ModuleNotFoundException(String message, Throwable cause) { super(message, cause); }
}
