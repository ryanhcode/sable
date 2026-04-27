package dev.ryanhcode.sable.mixin.entity.falling_block;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockEntityMixin extends Entity {

    public FallingBlockEntityMixin(final EntityType<?> entityType, final Level level) {
        super(entityType, level);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/FallingBlockEntity;onGround()Z", shift = At.Shift.BEFORE))
    private void sable$beforeOnGroundCheck(final CallbackInfo ci, @Local(ordinal = 0) final LocalRef<BlockPos> blockPos) {
        final SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(this);

        if (trackingSubLevel != null) {
            blockPos.set(BlockPos.containing(trackingSubLevel.logicalPose().transformPositionInverse(this.position())));
        }

    }

}
