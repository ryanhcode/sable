package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.ejector;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.logistics.depot.EjectorBlockEntity;
import com.simibubi.create.content.logistics.depot.EntityLauncher;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.ejector.SubLevelScanResult;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes weighted ejectors apply an impulse to sub-levels above them
 */
@Mixin(EjectorBlockEntity.class)
public abstract class EjectorBlockEntityMixin extends SmartBlockEntity {

    @Unique
    private static final int SUB_LEVEL_SCAN_TIME = 2;
    @Shadow
    EjectorBlockEntity.State state;
    @Shadow
    boolean powered;
    @Shadow
    EntityLauncher launcher;
    @Unique
    private int sable$scanTimer = SUB_LEVEL_SCAN_TIME;
    @Unique
    private int sable$readyTimer = 0;

    public EjectorBlockEntityMixin(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @Shadow
    public abstract void activate();

    @Shadow
    protected abstract Direction getFacing();

    @Inject(method = "activateDeferred", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/logistics/depot/EjectorBlockEntity;launchItems()V"))
    public void sable$launchSubLevels(final CallbackInfo ci) {
        final SubLevelScanResult scanResult = this.sable$lookForLaunchableSubLevels();
        if (scanResult == null) return;

        final ServerSubLevel otherSubLevel = scanResult.serverSubLevel();
        final SubLevelPhysicsSystem physicsSystem = SubLevelContainer.getContainer((ServerLevel) this.level).physicsSystem();

        final BlockPos blockPos = this.getBlockPos();
        final SubLevel containingSubLevel = Sable.HELPER.getContaining(this.level, blockPos);

        // math to compute the impulse to launch a 1 kpg sub-level to the target
        // authored by Eriksonn
        final double c = 3.0 * Math.max(1.0, this.launcher.getHorizontalDistance() / 10.0); // velocity constraint [m/s]
        final double px = this.launcher.getHorizontalDistance();
        final double py = this.launcher.getVerticalDistance();

        final double g = -DimensionPhysicsData.getGravity(this.level, JOMLConversion.atCenterOf(blockPos)).y;

        double vx = c;
        if (py > 0) {
            vx = Math.min(c, px * Math.sqrt(0.5 * g / py));
        }

        final double vy = vx * py / px + 0.5 * g * px / vx;

        final Vec3 verticalImpulse = new Vec3(0.0, vy, 0.0);

        final Vec3 localHit = Vec3.atLowerCornerOf(this.getFacing().getNormal()).scale(vx)
                .add(verticalImpulse);

        final Vec3 globalHitDirection = containingSubLevel != null ?
                containingSubLevel.logicalPose().transformNormal(localHit) :
                localHit;

        final RigidBodyHandle otherHandle = physicsSystem.getPhysicsHandle(otherSubLevel);
        otherHandle.applyImpulseAtPoint(scanResult.result().getBlockPos().getCenter(), otherSubLevel.logicalPose().transformNormalInverse(globalHitDirection));

        if (containingSubLevel != null) {
            final RigidBodyHandle handle = physicsSystem.getPhysicsHandle((ServerSubLevel) containingSubLevel);
            handle.applyImpulseAtPoint(blockPos.getCenter(), containingSubLevel.logicalPose().transformNormalInverse(globalHitDirection).scale(-1.0));
        }
    }

    @WrapOperation(method = "activateDeferred", at= @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setPos(DDD)V"))
    public void setPos(final Entity instance, final double x, final double y, final double z, final Operation<Void> original) {
        final Vector3d projected = Sable.HELPER.projectOutOfSubLevel(instance.level(), new Vector3d(x, y, z));
        original.call(instance, projected.x, projected.y, projected.z);
    }

    @Unique
    private @Nullable SubLevelScanResult sable$lookForLaunchableSubLevels() {
        final ActiveSableCompanion helper = Sable.HELPER;
        final SubLevel containingSubLevel = helper.getContaining(this);
        final BlockPos blockPos = this.getBlockPos();
        final ClipContext clipContext = new ClipContext(blockPos.getCenter(), Vec3.upFromBottomCenterOf(blockPos, 1.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty());
        final ClipContextExtension extension = (ClipContextExtension) clipContext;

        // Ignore the main world. We only care about sub-levels.
        extension.sable$setIgnoreMainLevel(true);
        extension.sable$setIgnoredSubLevel(containingSubLevel);

        final BlockHitResult result = this.level.clip(clipContext);

        if (result.getType() == HitResult.Type.MISS) return null;

        final SubLevel subLevel = helper.getContaining(this.level, result.getLocation());
        if (!(subLevel instanceof final ServerSubLevel serverSubLevel)) {
            return null;
        }

        return new SubLevelScanResult(result, serverSubLevel);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    public void sable$tick(final CallbackInfo ci) {
        if (this.level.isClientSide && !this.isVirtual()) return;

        this.sable$scanTimer--;

        if (this.sable$scanTimer <= 0) {
            this.sable$scanTimer = SUB_LEVEL_SCAN_TIME;

            if (this.state == EjectorBlockEntity.State.RETRACTING ||
                    this.powered ||
                    this.launcher.getHorizontalDistance() == 0) {
                this.sable$readyTimer = 0;
                return;
            }

            final SubLevelScanResult result = this.sable$lookForLaunchableSubLevels();

            if (result != null) {
                this.sable$readyTimer++;
            } else {
                this.sable$readyTimer = 0;
            }

            if (this.sable$readyTimer > 3) {
                this.activate();
                this.notifyUpdate();
                this.sable$readyTimer = 0;
            }
        }
    }
}
