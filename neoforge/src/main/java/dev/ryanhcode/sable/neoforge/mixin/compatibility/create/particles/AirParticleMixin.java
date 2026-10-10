package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.particles;

import com.simibubi.create.foundation.particle.AirParticle;
import com.simibubi.create.foundation.particle.AirParticleData;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.particle.ParticleSubLevelKickable;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link com.simibubi.create.foundation.particle.AirParticle} so air particles can track target positions across sub-levels.
 */

@Mixin(AirParticle.class)
public abstract class AirParticleMixin extends SimpleAnimatedParticle implements ParticleSubLevelKickable {

    @Shadow
    private float twirlAngleOffset;

    @Shadow
    private float twirlRadius;

    @Shadow
    private float drag;

    @Unique
    private double sable$originX;
    @Unique
    private double sable$originZ;
    @Unique
    private double sable$originY;
    @Unique
    private double sable$targetY;
    @Unique
    private double sable$targetX;
    @Unique
    private double sable$targetZ;

    @Shadow
    private Direction.Axis twirlAxis;

    protected AirParticleMixin(final ClientLevel arg, final double d, final double e, final double f, final SpriteSet arg2, final float g) {
        super(arg, d, e, f, arg2, g);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void sable$postInit(final ClientLevel world, final AirParticleData data, final double x, final double y, final double z, final double dx, final double dy, final double dz, final SpriteSet sprite, final CallbackInfo ci) {
        this.sable$originX = x;
        this.sable$originY = y;
        this.sable$originZ = z;
        this.sable$targetX = x + dx;
        this.sable$targetY = y + dy;
        this.sable$targetZ = z + dz;
    }

    /**
     * @author RyanH
     * @reason Fix target / origin handling with sub-levels
     */
    @Overwrite
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        final float progress = (float) Math.pow(((float) this.age) / this.lifetime, this.drag);
        final float angle = (progress * 2 * 360 + this.twirlAngleOffset) % 360;
        final Vec3 twirl = VecHelper.rotate(new Vec3(0, this.twirlRadius, 0), angle, this.twirlAxis);

        final double desiredX = (Mth.lerp(progress, this.sable$originX, this.sable$targetX) + twirl.x);
        final double desiredY = (Mth.lerp(progress, this.sable$originY, this.sable$targetY) + twirl.y);
        final double desiredZ = (Mth.lerp(progress, this.sable$originZ, this.sable$targetZ) + twirl.z);
        final Vector3d desiredVec = Sable.HELPER.projectOutOfSubLevel(this.level, new Vector3d(desiredX, desiredY, desiredZ));

        this.xd = desiredVec.x - this.x;
        this.yd = desiredVec.y - this.y;
        this.zd = desiredVec.z - this.z;

        this.setSpriteFromAge(this.sprites);
        this.move(this.xd, this.yd, this.zd);
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
