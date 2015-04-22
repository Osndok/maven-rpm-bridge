package javax.module;

import java.lang.annotation.*;

/**
 * This annotation flags a particular class as implementing a tool that would be useful on the
 * command line. Thus, this class should always be packaged as a command line tool, and an
 * inability to do so represents a packaging error.
 *
 * TODO: make this annotation @Repeatable
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public
@interface CommandLineTool
{
	/**
	 * If provided, this overrides all of the automatic name processing to directly arrive at
	 * the provided tool name. For example, if name is provided as "mytool", then a tool is
	 * expected to finally appear at "/usr/bin/mytool".
	 *
	 * If the name begins with a forward-slash ('/') or a percent-sign ('%'), then it is
	 * interpreted as a path where the tool should reside (and can include RPM macros for
	 * such paths).
	 *
	 * NB: using this mechanism will usually create packages that cannot be overlapped by
	 * major version number, as they both will provide the same tool at the same path.
	 *
	 * @url https://fedoraproject.org/wiki/Packaging:RPMMacros#Other_macros_and_variables_for_paths
	 */
	String name() default "";

	/**
	 * If provided, this prefix will be directly appended with an indication of the major version
	 * (e.g. "v1", "v3", "snapshot") and possibly the provided suffix to arrive at the final tool
	 * name. For example, if "mytool-" is provided, then the system might generate "mytool-v2".
	 *
	 * If not provided, this defaults to the module's name and a hypen.
	 */
	String prefix() default "";

	/**
	 * If provided, this suffix will follow the major version indication in the computed tool name.
	 * For example, "-stop" might result in a tool name of "mytool-v3-stop".
	 *
	 * If not provided, this will default to the empty string (if there is only one) or the class's
	 * simple name (if there are no conflicts), or a best-effort (but undefined) behavior in any
	 * other case.
	 */
	String suffix() default "";

	/**
	 * If provided, this string will be included in the "documentation" for the tool.
	 *
	 * At the moment, this means that this string might appear in the initial comments
	 * of the tool (which is an introspectable shell script).
	 */
	String description() default "";
}
