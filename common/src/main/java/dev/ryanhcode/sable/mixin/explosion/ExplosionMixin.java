package dev.ryanhcode.sable.mixin.explosion;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.windcharge.AbstractWindCharge;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Optional;
import java.util.Set;

@Mixin(Explosion.class)
public class ExplosionMixin {


    @Shadow
    @Final
    private Level level;

    @Shadow
    @Final
    private double x;

    @Shadow
    @Final
    private double y;

    @Shadow
    @Final
    private double z;

    @Shadow
    @Final
    private ExplosionDamageCalculator damageCalculator;

    @Shadow @Final private @Nullable Entity source;

    @Inject(method = "explode", at = @At("HEAD"))
    private void sable$preExplode(final CallbackInfo ci, @Share("explodedSet") final LocalRef<Set<BlockPos>> explodedSet) {
        explodedSet.set(new ObjectOpenHashSet<>());
    }

    @Inject(method = "explode", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ExplosionDamageCalculator;getBlockExplosionResistance(Lnet/minecraft/world/level/Explosion;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)Ljava/util/Optional;"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void sable$redirectBlockExplosionResistance(final CallbackInfo ci,
                                                        final Set<BlockPos> set,
                                                        final int i,
                                                        final int j,
                                                        final int k,
                                                        final int l,
                                                        final double d0,
                                                        final double d1,
                                                        final double d2,
                                                        final double d3,
                                                        float f,
                                                        final double d4,
                                                        final double d6,
                                                        final double d8,
                                                        final float f1,
                                                        BlockPos blockpos,
                                                        BlockState blockstate,
                                                        FluidState fluidstate,
                                                        @Local(ordinal = 0) final LocalFloatRef fReference,
                                                        @Share("explodedSet") final LocalRef<Set<BlockPos>> explodedSet) {
        final Explosion self = (Explosion) (Object) this;

        if (!blockstate.isAir()) {
            return;
        }

        final BoundingBox3d globalBounds = new BoundingBox3d(blockpos);
        final Iterable<SubLevel> subLevels = Sable.HELPER.getAllIntersecting(this.level, globalBounds);
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);

        for (final SubLevel subLevel : subLevels) {
            final Pose3d pose = subLevel.logicalPose();
            final Vec3 localRayPosition = pose.transformPositionInverse(new Vec3(d4, d6, d8));
            final Vec3 localExplosionPosition = pose.transformPositionInverse(new Vec3(this.x, this.y, this.z));

            blockpos = BlockPos.containing(localRayPosition);
            blockstate = this.level.getBlockState(blockpos);
            fluidstate = this.level.getFluidState(blockpos);

            final boolean canExplodeBefore = f > 0.0;

            final Optional<Float> optional = this.damageCalculator.getBlockExplosionResistance(self, this.level, blockpos, blockstate, fluidstate);
            if (optional.isPresent()) {
                f -= (optional.get() + 0.3F) * 0.3F;
            }

            if (f > 0.0F && this.damageCalculator.shouldBlockExplode(self, this.level, blockpos, blockstate, f)) {
                set.add(blockpos);
            }

            final boolean wind = (this.source instanceof AbstractWindCharge || this.damageCalculator == AbstractWindCharge.EXPLOSION_DAMAGE_CALCULATOR) && !blockstate.isAir();
            if (canExplodeBefore && (f < 0.0f || wind) && explodedSet.get().add(blockpos)) {
                explodedSet.get().add(blockpos);

                if (subLevel instanceof final ServerSubLevel serverSubLevel) {
                    final SubLevelPhysicsSystem physicsSystem = ((ServerSubLevelContainer) container).physicsSystem();
                    final RigidBodyHandle handle = physicsSystem.getPhysicsHandle(serverSubLevel);

                    final Vec3 pos = blockpos.getCenter();
                    final Vec3 force = pos.subtract(localExplosionPosition).normalize().scale(5.0);
                    handle.applyImpulseAtPoint(pos, force);
                }
            }
        }

        fReference.set(f);
    }
}
