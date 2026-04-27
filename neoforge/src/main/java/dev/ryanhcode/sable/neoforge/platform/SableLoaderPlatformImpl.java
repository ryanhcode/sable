package dev.ryanhcode.sable.neoforge.platform;

import dev.ryanhcode.sable.platform.SableLoaderPlatform;
import net.neoforged.fml.loading.LoadingModList;

public class SableLoaderPlatformImpl implements SableLoaderPlatform {
	@Override
	public String getModVersion(final String modId) {
		return LoadingModList.get().getModFileById(modId).versionString();
	}
}
