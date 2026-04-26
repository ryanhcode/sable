package dev.ryanhcode.sable.mixin.particle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.mixinterface.particle.ParticleExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Before ticking a particle, try and kick it from a {@link LevelPlot}
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {

    @Shadow public abstract void add(Particle particle);

    @Shadow protected ClientLevel level;

    @Inject(method = "add", at = @At("TAIL"))
    private void sable$onParticleAdd(final Particle particle, final CallbackInfo ci) {
        ((ParticleExtension) particle).sable$initialKickOut();
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/Particle;tick()V"))
    private void sable$onParticleTick(final Particle instance, final Operation<Void> original) {
        final ParticleExtension extension = ((ParticleExtension) instance);

        extension.sable$initialKickOut();
        original.call(instance);
        extension.sable$moveWithInheritedVelocity();
    }

    @WrapOperation(method = "crack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/TerrainParticle;setPower(F)Lnet/minecraft/client/particle/Particle;"))
    private Particle sable$addCrackParticle(final TerrainParticle particle, final float v, Operation<Particle> original, @Local(argsOnly = true) final BlockPos pos, @Local final BlockState state) {
        final Vec3 particlePosition = new Vec3(particle.x, particle.y, particle.z);

        final SubLevel subLevel = Sable.HELPER.getContaining(this.level, particlePosition);
        if (subLevel != null) {
            final Vec3 velocity = new Vec3(particle.xd, particle.yd, particle.zd);
            final Vec3 globalVelocity = subLevel.logicalPose().transformNormal(velocity);

            particle.xd = globalVelocity.x;
            particle.yd = globalVelocity.y;
            particle.zd = globalVelocity.z;

            original.call(particle, v);

            final Vec3 localVelocity = subLevel.logicalPose().transformNormalInverse(new Vec3(particle.xd, particle.yd, particle.zd));

            particle.xd = localVelocity.x;
            particle.yd = localVelocity.y;
            particle.zd = localVelocity.z;
            ((ParticleExtension) particle).sable$setTrackingSubLevel((ClientSubLevel) subLevel, particlePosition);

            return particle;
        } else {
            return original.call(particle, v);
        }
    }

}
