package javax.module;

import java.lang.annotation.*;

/**
 * This annotation allows a module-aware java library to mute particular classes from being
 * made available on the command line (esp. Runnable & Callable).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public
@interface NoCommandLineUtility
{
}
