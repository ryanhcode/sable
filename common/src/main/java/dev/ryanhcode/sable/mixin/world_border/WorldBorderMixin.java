package dev.ryanhcode.sable.mixin.world_border;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.world_border.WorldBorderExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin into {@link net.minecraft.world.level.border.WorldBorder} to implement {@link dev.ryanhcode.sable.mixinterface.world_border.WorldBorderExtension}, treating sub-level plots as within bounds.
 */
@Mixin(WorldBorder.class)
public class WorldBorderMixin implements WorldBorderExtension {

    @Unique
    private Level sable$level;

    @ModifyReturnValue(method = "isWithinBounds(DDD)Z", at = @At("RETURN"))
    public boolean sable$isWithinBounds(final boolean original, final double x, final double z, final double offset) {
        if (original || this.sable$level == null) return original;
        final SubLevelContainer container = SubLevelContainer.getContainer(this.sable$level);
        return container != null && container.inBounds(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
    }

    @ModifyReturnValue(method = "clampToBounds(DDD)Lnet/minecraft/core/BlockPos;", at = @At("RETURN"))
    private BlockPos sable$clampToBounds(final BlockPos original, final double x, final double y, final double z) {
        if (this.sable$level == null) {
            return original;
        }

        final SubLevelContainer container = SubLevelContainer.getContainer(this.sable$level);

        if (container != null && container.inBounds(Mth.floor(x) >> 4, Mth.floor(z) >> 4)) {
            return BlockPos.containing(x, y, z);
        }

        return original;
    }

    @Inject(method = "isInsideCloseToBorder", at = @At("HEAD"), cancellable = true)
    public void sable$isInsideCloseToBorder(final Entity entity, final AABB aABB, final CallbackInfoReturnable<Boolean> cir) {
        if (this.sable$level == null) {
            return;
        }

        final SubLevelContainer container = SubLevelContainer.getContainer(this.sable$level);

        if (container != null && Sable.HELPER.getContaining(entity) != null) {
            cir.setReturnValue(false);
        }
    }

    @Override
    public void sable$setLevel(final Level level) {
        this.sable$level = level;
    }
}
