package dev.ryanhcode.sable.mixin.particle;

import dev.ryanhcode.sable.api.particle.ParticleSubLevelKickable;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionContainer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SuspendedParticle;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mixin into {@link net.minecraft.client.particle.SuspendedParticle} to keep suspended particles from being kicked out of sub-level tracking and to remove them when occluded by water.
 */
@Mixin(SuspendedParticle.class)
public abstract class SuspendedParticleMixin extends Particle implements ParticleSubLevelKickable {

    protected SuspendedParticleMixin(final ClientLevel clientLevel, final double d, final double e, final double f) {
        super(clientLevel, d, e, f);
    }

    @Override
    public void move(final double d, final double e, final double f) {
        super.move(d, e, f);

        final WaterOcclusionContainer<?> container = WaterOcclusionContainer.getContainer(this.level);
        if (container != null && container.isOccluded(new Vec3(this.x, this.y, this.z))) {
            this.remove();
        }
    }

    @Override
    public boolean sable$shouldKickFromTracking() {
        return false;
    }

    @Override
    public boolean sable$shouldCollideWithTrackingSubLevel() {
        return false;
    }
}
