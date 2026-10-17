package dev.ryanhcode.sable.mixin.entity.entity_sublevel_collision;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorStand.class)
public abstract class ArmorStandMixin extends Entity {

    public ArmorStandMixin(final EntityType<?> entityType, final Level level) {
        super(entityType, level);
    }

    @Unique
    private int sable$lastAttachedTick;

    @Inject(method = "tick", at = @At("TAIL"))
    private void sable$postTick(final CallbackInfo ci) {
        if (this.level().isClientSide) return;

        final SubLevel containingSubLevel = Sable.HELPER.getContaining(this);

        if (containingSubLevel != null) {
            if (this.tickCount - this.sable$lastAttachedTick > 1 && !this.onGround()) {
                EntitySubLevelUtil.kickEntity(containingSubLevel, this);
            }
        } else if (this.onGround()) {
            final SubLevel landed = Sable.HELPER.getTrackingSubLevel(this);
            if (landed != null) {
                final Vec3 shipyardPos = landed.logicalPose().transformPositionInverse(this.position());
                final BlockPos belowPos = BlockPos.containing(shipyardPos.x, shipyardPos.y - 0.5, shipyardPos.z);
                final BlockState belowState = this.level().getBlockState(belowPos);
                if (belowState.isFaceSturdy(this.level(), belowPos, Direction.UP)
                        && Math.abs(shipyardPos.y - (belowPos.getY() + 1)) < 0.1) {
                    final Vec3 shipyardVel = landed.logicalPose().transformNormalInverse(this.getDeltaMovement());
                    this.moveTo(shipyardPos.x, shipyardPos.y, shipyardPos.z);
                    this.setDeltaMovement(shipyardVel);
                    this.sable$lastAttachedTick = this.tickCount;
                }
            }
        }
    }
}
