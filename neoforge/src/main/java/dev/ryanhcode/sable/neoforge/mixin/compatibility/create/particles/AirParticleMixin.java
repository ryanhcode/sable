package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.particles;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
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

    @Shadow private float originZ;

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

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F", ordinal = 2))
    private float make(float delta, float start, float end, Operation<Float> original,
                       @Local(name = "x") final LocalFloatRef x, @Local(name = "y") final LocalFloatRef y) {
        final float z = original.call(delta, start, end);
        Vector3d desiredVec = Sable.HELPER.projectOutOfSubLevel(this.level, new Vector3d(x.get(), y.get(), z));
        x.set((float)desiredVec.x);
        y.set((float)desiredVec.y);
        return (float) desiredVec.z;
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
