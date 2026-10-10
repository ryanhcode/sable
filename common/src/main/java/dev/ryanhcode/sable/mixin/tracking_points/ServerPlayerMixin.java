package dev.ryanhcode.sable.mixin.tracking_points;

import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Mixin into {@link net.minecraft.server.level.ServerPlayer} to generate and store a tracking point when saving player data.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Shadow public abstract ServerLevel serverLevel();

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void addAdditionalSaveData(final CompoundTag compoundTag, final CallbackInfo ci) {
        final SubLevelTrackingPointSavedData data = SubLevelTrackingPointSavedData.getOrLoad(this.serverLevel());
        final UUID loginPointUUID = data.generateTrackingPoint((ServerPlayer) (Object) this);
        if (loginPointUUID != null) {
            compoundTag.putUUID("LoginPoint", loginPointUUID);
        }
    }

}
