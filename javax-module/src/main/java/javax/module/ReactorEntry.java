package javax.module;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Similar to the Plugin annotation interface, but allows system operators to more easily effect
 * the files, as they are all grouped into one place. Having a directory of configuration files
 * is common in RPM packaging, because it is much easier and more correct to write a parallel
 * (non-conflicting) config file than to append or modify a config file from another package.
 *
 * When applied to a class, this annotation will cause the mrb-grinder to generate a properties
 * file (specific to this module) that includes basic module and class information and (if relevant)
 * the name of the class's shell-level entry point.
 *
 * The java properties file format was chosen because it is also easily readable by the common
 * unix shell (bash).
 *
 * A reactor is a somewhat general construct, in that the only mechanism supported is the discovery
 * of reactor entries (by the presence of a file in a directory). Everything else... including
 * which interfaces a client entry should implement, how the reactor actually loads the class, et
 * cetera... are all outside the scope of this project; and therefore (unlike the Plugin mechanism)
 * this ought to be usable even without the modular classloader being in use.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public
@interface ReactorEntry
{
	/**
	 * This is the primary place to specify *which* reactor this class takes part in; if left
	 * unspecified, then it is presumed to be the most general (system-wide) reactor.
	 */
	String directory() default "/etc/reactor.d";

	/**
	 * @todo BUG: ReactorEntry::symver does not make it all the way to mrb-grinder::MavenJar or the properties file
	 * @return true if (and only if) the major version number should be in the file name.
	 */
	boolean symver() default true;

	/**
	 * When used in conjunction with a 'value', this allows the class to specify one extra
	 * key/value pair that will appear in the reactor entry file. It's not much, but often
	 * it's all you theoretically need (e.g. reference a different properties file).
	 *
	 * If 'key' is specified without a 'value', then "true" will be used as the value.
	 */
	String key() default "";

	/**
	 * When used in conjunction with a 'key', this allows the class to specify one extra
	 * key/value pair that will appear in the reactor entry file. It's not much, but often
	 * it's all you theoretically need (e.g. reference a different properties file).
	 *
	 * If 'key' is specified without a 'value', then "true" will be used as the value.
	 */
	String value() default "true";
}
