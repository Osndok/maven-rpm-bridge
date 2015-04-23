package javax.module;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation flags a particular class as implementing an interesting interface that should
 * be silently and implicitly included in any call for plugins that implement a certain interface.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public
@interface Plugin
{
}
