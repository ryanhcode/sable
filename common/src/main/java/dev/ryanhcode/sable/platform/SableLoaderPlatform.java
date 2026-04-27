package dev.ryanhcode.sable.platform;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface SableLoaderPlatform {
	SableLoaderPlatform INSTANCE = SablePlatformUtil.load(SableLoaderPlatform.class);
	
	String getModVersion(String modId);
}
