package dev.ryanhcode.sable.mixin.sublevel_sounds;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.sound.MovingSoundInstanceDelegate;
import dev.ryanhcode.sable.sound.SoundInstanceDelegated;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Iterator;

/**
 * Mixin into {@link net.minecraft.client.sounds.SoundEngine} to wrap sounds inside sub-levels in a moving delegate that follows the sub-level's motion.
 */
@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    @ModifyVariable(method = "play", at = @At("HEAD"), argsOnly = true)
    private SoundInstance sable$play(final SoundInstance instance) {
        final ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return instance;
        }

        final SubLevel subLevel = Sable.HELPER.getContaining(level, instance.getX(), instance.getZ());
        if (subLevel != null) {
            return new MovingSoundInstanceDelegate(instance, subLevel);
        }

        return instance;
    }

    @ModifyVariable(method = "stop(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"), argsOnly = true)
    private SoundInstance sable$stop(final SoundInstance instance) {
        if (instance instanceof final SoundInstanceDelegated delegated) {
            if (delegated.getDelegate() != null) {
                return delegated.getDelegate();
            }
        }

        return instance;
    }

    @Inject(method = "tickNonPaused", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V", shift = At.Shift.AFTER, ordinal = 0), locals = LocalCapture.CAPTURE_FAILEXCEPTION)
    private void sable$tick(final CallbackInfo ci, final Iterator<TickableSoundInstance> sounds, final TickableSoundInstance sound,
                            final float volume, final float pitch, final Vec3 pos, final ChannelAccess.ChannelHandle access) {
        if (sound instanceof final MovingSoundInstanceDelegate delegated) {
            access.execute(delegated::tickWithChannel);
        }
    }

    @Inject(method = "stop(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V", shift = At.Shift.AFTER), locals = LocalCapture.CAPTURE_FAILEXCEPTION)
    private void sable$clear(final SoundInstance sound, final CallbackInfo ci, final ChannelAccess.ChannelHandle access) {
        if (sound instanceof final MovingSoundInstanceDelegate delegated) {
            access.execute(delegated::unload);
        }
    }
}
