package javax.module;

import java.lang.annotation.*;

/**
 * Annotation that explicitly identifies a particular method as being 'callable' from a command
 * line option/flag/switch. The number and type of arguments to that method dictate how many
 * (and what type) of parameters the flag requires.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public
@interface CommandLineOption
{
	char _short();
	String _long();
}
