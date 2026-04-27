package dev.ryanhcode.sable.mixin.entity.entities_stick_sublevels.player;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin extends Entity {

    @Unique
    private Vec3 sable$oldPos;

    public LocalPlayerMixin(final EntityType<?> pEntityType, final Level pLevel) {
        super(pEntityType, pLevel);
    }

    @Inject(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getX()D", ordinal = 0, shift = At.Shift.BEFORE))
    private void sable$preSendPosition(final CallbackInfo ci) {
        this.sable$oldPos = null;

        final SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(this);
        if (trackingSubLevel != null && !trackingSubLevel.isRemoved()) {
            final Vec3 pos = this.position();
            this.sable$oldPos = new Vec3(pos.x, pos.y, pos.z);

            final Vec3 localPosition = trackingSubLevel.logicalPose().transformPositionInverse(pos);
            ((EntityMovementExtension) this).sable$setPosField(localPosition);
        }
    }

    @Inject(method = "sendPosition", at = @At(value = "RETURN"))
    private void sable$postSendPosition(final CallbackInfo ci) {
        if (this.sable$oldPos != null) {
            ((EntityMovementExtension) this).sable$setPosField(this.sable$oldPos);
            this.sable$oldPos = null;
        }
    }
}
