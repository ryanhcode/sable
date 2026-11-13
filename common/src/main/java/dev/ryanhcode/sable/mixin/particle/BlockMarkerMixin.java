package dev.ryanhcode.sable.mixin.particle;

import dev.ryanhcode.sable.api.particle.ParticleSubLevelKickable;
import net.minecraft.client.particle.BlockMarker;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mixin into {@link net.minecraft.client.particle.BlockMarker} to keep block marker particles from being kicked out of sub-level tracking and from colliding with the tracked sub-level.
 */
@Mixin(BlockMarker.class)
public class BlockMarkerMixin implements ParticleSubLevelKickable {

    @Override
    public boolean sable$shouldKickFromTracking() {
        return false;
    }

    @Override
    public boolean sable$shouldCollideWithTrackingSubLevel() {
        return false;
    }
}
