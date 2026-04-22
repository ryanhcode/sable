package dev.ryanhcode.sable.sublevel.render;

import com.mojang.logging.LogUtils;
import foundry.veil.Veil;
import foundry.veil.api.compat.SodiumCompat;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;

import java.util.List;
import java.util.Set;

public abstract class AbstractSableMixinPlugin implements IMixinConfigPlugin {
    public static final Logger LOGGER = LogUtils.getLogger();
    private final Object2BooleanMap<String> modLoadedCache = new Object2BooleanOpenHashMap<>();
    private boolean sodiumPresent;
    private boolean lithiumPresent;

    @Override
    public void onLoad(final String mixinPackage) {
        this.sodiumPresent = SodiumCompat.isLoaded();
        this.lithiumPresent = Veil.platform().isModLoaded("lithium");

        if (this.sodiumPresent) {
            LOGGER.info("Using Sodium renderer mixins");
        } else {
            LOGGER.info("Using Vanilla renderer mixins");
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        // TODO: Housekeeping
        if (mixinClassName.startsWith("dev.ryanhcode.sable.mixin.sublevel_render.impl")) {
            return this.sodiumPresent ? mixinClassName.startsWith("dev.ryanhcode.sable.mixin.sublevel_render.impl.sodium") : mixinClassName.startsWith("dev.ryanhcode.sable.mixin.sublevel_render.impl.vanilla");
        }

        if (mixinClassName.startsWith("dev.ryanhcode.sable.mixin.plot.lighting.sodium")) {
            return this.sodiumPresent;
        }

        if (mixinClassName.startsWith("dev.ryanhcode.sable.mixin.compatibility.lithium")) {
            return this.lithiumPresent;
        }

        if (mixinClassName.startsWith("dev.ryanhcode.sable.mixin.compatibility.") ||
                mixinClassName.startsWith("dev.ryanhcode.sable.neoforge.mixin.compatibility.") ||
                mixinClassName.startsWith("dev.ryanhcode.sable.fabric.mixin.compatibility.")
        ) {
            final String[] parts = mixinClassName.split("\\.");
            if (parts.length < 5) {
                return true;
            }

            final String modid = parts[3].equals("mixin") ? parts[5] : parts[6];
            return this.modLoadedCache.computeIfAbsent(modid, x -> Veil.platform().isModLoaded(modid));
        }

        return true;
    }

    @Override
    public void acceptTargets(final Set<String> myTargets, final Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

}
