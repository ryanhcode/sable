package dev.ryanhcode.sable.platform;

import java.util.ServiceLoader;

public class SablePlatformUtil {
	public static <T> T load(Class<T> clazz) {
		return ServiceLoader.load(clazz, SablePlatformUtil.class.getClassLoader())
				.findFirst()
				.orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
	}
}
