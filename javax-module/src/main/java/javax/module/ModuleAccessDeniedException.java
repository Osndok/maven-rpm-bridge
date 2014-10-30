package javax.module;

/**
 * Created by robert on 10/30/14.
 */
public
class ModuleAccessDeniedException extends Exception
{
	ModuleAccessDeniedException(String message) { super(message); }
	ModuleAccessDeniedException(String message, Throwable cause) { super(message, cause); }
}
