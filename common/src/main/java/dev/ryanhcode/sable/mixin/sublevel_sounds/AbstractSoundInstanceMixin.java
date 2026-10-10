package dev.ryanhcode.sable.mixin.sublevel_sounds;

import dev.ryanhcode.sable.sound.MovingSoundInstanceDelegate;
import dev.ryanhcode.sable.sound.SoundInstanceDelegated;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Mixin into {@link net.minecraft.client.resources.sounds.AbstractSoundInstance} to implement {@link dev.ryanhcode.sable.sound.SoundInstanceDelegated}, holding a moving sound delegate.
 */
@Mixin(AbstractSoundInstance.class)
public class AbstractSoundInstanceMixin implements SoundInstanceDelegated {

    @Unique
    private MovingSoundInstanceDelegate sable$delegate;


    @Override
    public MovingSoundInstanceDelegate getDelegate() {
        return this.sable$delegate;
    }

    @Override
    public void setDelegate(final MovingSoundInstanceDelegate delegate) {
        this.sable$delegate = delegate;
    }
}
