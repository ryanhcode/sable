package dev.ryanhcode.sable.mixin.sublevel_sounds;

import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor exposing the private source field of {@link com.mojang.blaze3d.audio.Channel}.
 */
@Mixin(Channel.class)
public interface ChannelAccessor {

    @Accessor
    int getSource();
}
