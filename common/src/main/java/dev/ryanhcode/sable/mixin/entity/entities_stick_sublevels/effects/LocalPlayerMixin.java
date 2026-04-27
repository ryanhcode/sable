package dev.ryanhcode.sable.mixin.entity.entities_stick_sublevels.effects;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin extends Entity {

    public LocalPlayerMixin(final EntityType<?> entityType, final Level level) {
        super(entityType, level);
    }

    @Inject(method = "playSound", at = @At("HEAD"), cancellable = true)
    private void sable$playSound(final SoundEvent soundEvent, final float f, final float g, final CallbackInfo ci) {
        if (EntitySubLevelUtil.hasCustomEntityOrientation(this)) {
            final Vector3d feet = Sable.HELPER.getFeetPos(this, 0.0f);
            this.level().playSound(null, feet.x, feet.y, feet.z, soundEvent, this.getSoundSource(), f, g);
            ci.cancel();
        }
    }
}
