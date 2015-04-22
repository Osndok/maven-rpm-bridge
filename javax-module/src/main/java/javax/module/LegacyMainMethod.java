package javax.module;

import java.lang.annotation.*;

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
