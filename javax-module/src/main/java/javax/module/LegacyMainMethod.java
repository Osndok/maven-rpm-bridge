package javax.module;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This indicates that even though a main(String[] args) method is supplied, it is for backwards
 * compatibility only... and that the FuzzyEntryPoint mechanism is preferred.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
public
@interface LegacyMainMethod
{
}
