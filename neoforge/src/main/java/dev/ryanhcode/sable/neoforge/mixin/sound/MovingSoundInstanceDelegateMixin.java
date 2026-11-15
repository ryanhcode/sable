package dev.ryanhcode.sable.neoforge.mixin.sound;

import dev.ryanhcode.sable.sound.MovingSoundInstanceDelegate;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.concurrent.CompletableFuture;

/**
 * Mixin into {@link dev.ryanhcode.sable.sound.MovingSoundInstanceDelegate} to delegate audio stream loading for moving sound instances.
 */

@Mixin(MovingSoundInstanceDelegate.class)
public abstract class MovingSoundInstanceDelegateMixin implements SoundInstance {

    @Shadow
    public SoundInstance instance;

    @Override
    public @NotNull CompletableFuture<AudioStream> getStream(final SoundBufferLibrary soundBuffers, final Sound sound, final boolean looping) {
        return this.instance.getStream(soundBuffers, sound, looping);
    }

}
