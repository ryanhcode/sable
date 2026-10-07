package dev.ryanhcode.sable.mixin.block_placement;


import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.entity.TargetLocalInteractionEntity;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes the rotation of block placement to take into account orientation
 */
@Mixin(UseOnContext.class)
public abstract class UseOnContextMixin {

    @Shadow
    @Final
    private Level level;
    @Shadow
    @Final
    @Nullable
    private Player player;

    @Shadow
    public abstract BlockPos getClickedPos();

    @Unique
    private boolean sable$isAlreadyTargetLocal(
            final SubLevel targetSubLevel
    ) {
        return this.player instanceof
                final TargetLocalInteractionEntity localEntity
                && localEntity.sable$isAlreadyLocalTo(targetSubLevel);
    }

    @Inject(
            method = "getClickedFace",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sable$getClickedFace(
            final CallbackInfoReturnable<Direction> cir
    ) {
        if (this.player == null)
            return;

        final SubLevel targetSubLevel =
                Sable.HELPER.getContaining(
                        this.level,
                        this.getClickedPos()
                );

        if (targetSubLevel == null)
            return;

        /*
         * Create has already expressed the DeployerFakePlayer's look direction
         * in the target SubLevel's local coordinate system.
         */
        if (!this.sable$isAlreadyTargetLocal(targetSubLevel))
            return;

        final Vec3 localLook = this.player.getLookAngle();

        /*
         * The deployer looks along the ray toward the target. The clicked surface
         * normal therefore points in the opposite direction.
         */
        final Direction localClickedFace =
                Direction.getNearest(
                        localLook.x,
                        localLook.y,
                        localLook.z
                ).getOpposite();

        cir.setReturnValue(localClickedFace);
    }

    @Inject(method = "getHorizontalDirection", at = @At("HEAD"), cancellable = true)
    private void sable$getHorizontalDirection(final CallbackInfoReturnable<Direction> cir) {
        if (this.player == null) return;

        final SubLevel subLevel = Sable.HELPER.getContaining(this.level, this.getClickedPos());

        if (subLevel == null||this.sable$isAlreadyTargetLocal(subLevel)) return;

        SubLevelHelper.pushEntityLocal(subLevel, this.player);
        final Direction dir = this.player.getDirection();
        SubLevelHelper.popEntityLocal(subLevel, this.player);
        cir.setReturnValue(dir);
    }

    @Inject(method = "getRotation", at = @At("HEAD"), cancellable = true)
    private void sable$getRotation(final CallbackInfoReturnable<Float> cir) {
        if (this.player == null) return;

        final SubLevel subLevel = Sable.HELPER.getContaining(this.level, this.getClickedPos());

        if (subLevel != null) {
            SubLevelHelper.pushEntityLocal(subLevel, this.player);
            final float yRot = this.player.getYRot();
            SubLevelHelper.popEntityLocal(subLevel, this.player);
            cir.setReturnValue(yRot);
        }
    }

}
