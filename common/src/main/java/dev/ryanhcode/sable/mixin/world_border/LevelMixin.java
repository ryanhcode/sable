package dev.ryanhcode.sable.mixin.world_border;

import dev.ryanhcode.sable.mixinterface.world_border.WorldBorderExtension;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/**
 * Mixin into {@link net.minecraft.world.level.Level} to bind the level to its world border extension for sub-level-aware border checks.
 */
@Mixin(Level.class)
public class LevelMixin {

    @Shadow @Final private WorldBorder worldBorder;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void sable$initializeWorldBorder(final WritableLevelData writableLevelData, final ResourceKey resourceKey, final RegistryAccess registryAccess, final Holder holder, final Supplier supplier, final boolean bl, final boolean bl2, final long l, final int i, final CallbackInfo ci) {
        ((WorldBorderExtension) this.worldBorder).sable$setLevel((Level) (Object) this);
    }

}
