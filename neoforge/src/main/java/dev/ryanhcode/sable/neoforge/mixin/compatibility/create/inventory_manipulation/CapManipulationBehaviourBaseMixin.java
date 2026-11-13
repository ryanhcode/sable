package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.inventory_manipulation;

import com.google.common.base.Predicate;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.CapManipulationBehaviourBase;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin into {@link com.simibubi.create.foundation.blockEntity.behaviour.inventory.CapManipulationBehaviourBase} so inventory capability lookups account for sub-levels.
 */

@Mixin(CapManipulationBehaviourBase.class)
public class CapManipulationBehaviourBaseMixin {

    @Shadow protected Predicate<BlockEntity> filter;

    @Shadow protected boolean bypassSided;

    @Unique
    private BlockPos sable$caughtPos;

    @Redirect(method = "findNewCapability", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    public BlockEntity sable$findNewCapOnSubLevel(final Level level, final BlockPos blockPos) {
        final ActiveSableCompanion helper = Sable.HELPER;
        return helper.runIncludingSubLevels(level, blockPos.getCenter(), true, helper.getContaining(level, blockPos), (subLevel, internalPos) -> {
            final BlockEntity caughtBE = level.getBlockEntity(internalPos);
            if (this.filter.apply(caughtBE)) {
                this.sable$caughtPos = internalPos;
                return caughtBE;
            }

            return null;
        });
    }

    @Redirect(method = "findNewCapability", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getCapability(Lnet/neoforged/neoforge/capabilities/BlockCapability;Lnet/minecraft/core/BlockPos;Ljava/lang/Object;)Ljava/lang/Object;"))
    public <T> T sable$redirectPos(final Level instance, final BlockCapability<T, Direction> blockCapability, final BlockPos pos, final Object dir, @Local final BlockFace targetBlockFace) {
        return instance.getCapability(blockCapability, this.sable$caughtPos, this.bypassSided ? null : targetBlockFace.getFace());
    }
}
