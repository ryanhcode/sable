package dev.ryanhcode.sable.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MixinModVersionConstraint {
	/**
	 * The version range in maven format
	 */
	String value();
}
