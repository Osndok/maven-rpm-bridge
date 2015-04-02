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
	/**
	 * If provided, this character will be included as a 'clumpable' shortcut that is often helpful
	 * for tools that are often used by humans. For example, this might allow one to run:
	 * "mytool -wxyz" (having the effect of four flags that would otherwise be verbose).
	 */
	char _short() default '\0';

	/**
	 * If provided, this string will be recognized when precedded by two hypens as a command line
	 * switch.
	 */
	String _long() default "";

	/**
	 * If provided, this string will be included in diagnostic messages surrounding the misuse of this
	 * field, or the overall "usage" help text (if it is automatically generated).
	 */
	String description() default "";
}
