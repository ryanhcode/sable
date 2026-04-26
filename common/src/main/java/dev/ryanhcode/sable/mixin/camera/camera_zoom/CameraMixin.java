package dev.ryanhcode.sable.mixin.camera.camera_zoom;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.mixinhelpers.camera.new_camera_types.SableCameraTypes;
import dev.ryanhcode.sable.mixinterface.camera.camera_zoom.CameraZoomExtension;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

@Mixin(Camera.class)
public abstract class CameraMixin implements CameraZoomExtension {

    @Shadow
    private BlockGetter level;
    @Shadow
    private Vec3 position;
    @Shadow
    @Final
    private Vector3f forwards;
    @Shadow
    private Entity entity;
    @Unique
    private boolean sable$pushed = false;
    @Unique
    private float sable$zoomAmount;
    @Unique
    private float sable$interpolatedZoom;
    @Unique
    private float sable$lastInterpolatedZoom;
    @Shadow
    protected abstract void setPosition(double d, double e, double f);

    @Inject(method = "tick", at = @At("HEAD"))
    private void sable$preTick(final CallbackInfo ci) {
        this.sable$lastInterpolatedZoom = this.sable$interpolatedZoom;
        this.sable$interpolatedZoom = Mth.lerp(0.725f, this.sable$interpolatedZoom, this.sable$zoomAmount);
    }

    @Inject(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V", shift = At.Shift.AFTER))
    private void sable$setup(final BlockGetter blockGetter, final Entity entity, final boolean bl, final boolean bl2, final float f, final CallbackInfo ci) {
        final Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.options.getCameraType() == SableCameraTypes.SUB_LEVEL_VIEW || minecraft.options.getCameraType() == SableCameraTypes.SUB_LEVEL_VIEW_UNLOCKED) {
            final Entity cameraEntity = minecraft.cameraEntity;
            final Entity vehicle = cameraEntity.getVehicle();

            if (vehicle != null) {
                final SubLevel subLevel = Sable.HELPER.getContaining(minecraft.level, vehicle.position());

                if (subLevel instanceof final ClientSubLevel clientSubLevel) {
                    final Vector3dc pos = clientSubLevel.renderPose().position();
                    this.setPosition(pos.x(), pos.y(), pos.z());
                }
            }
        }
    }

    @Unique
    private float sable$clampZoom(final float maxZoom, final SubLevel ignoredSubLevel) {
        float zoom = maxZoom;

        final float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);

        final Level level = this.entity.level();
        final LevelPoseProviderExtension extension = ((LevelPoseProviderExtension) this.level);
        assert extension != null;

        final Collection<SubLevel> ignoredChain = SubLevelHelper.getConnectedChain(ignoredSubLevel);

        extension.sable$pushPoseSupplier((subLevel) -> ((ClientSubLevel) subLevel).renderPose(partialTick));

        for (int i = 0; i < 8; i++) {
            final float offsetX = (float) ((i & 1) * 2 - 1);
            final float offsetY = (float) ((i >> 1 & 1) * 2 - 1);
            final float offsetZ = (float) ((i >> 2 & 1) * 2 - 1);

            final Vec3 vec3 = this.position.add(offsetX * 0.1F, offsetY * 0.1F, offsetZ * 0.1F);
            final Vec3 vec32 = vec3.add(new Vec3(this.forwards).scale(-zoom));

            final ClipContext clipContext = new ClipContext(vec3, vec32, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this.entity);
            ((ClipContextExtension) clipContext).sable$setSubLevelIgnoring(ignoredChain::contains);
            final HitResult hitResult = this.level.clip(clipContext);

            if (hitResult.getType() != HitResult.Type.MISS) {
                final float l = (float) Sable.HELPER.distanceSquaredWithSubLevels(level, hitResult.getLocation(), this.position);
                if (l < Mth.square(zoom)) {
                    zoom = Mth.sqrt(l);
                }
            }
        }

        extension.sable$popPoseSupplier();

        return zoom;
    }

    @Inject(method = "getMaxZoom", at = @At(value = "HEAD"), cancellable = true)
    private void sable$getMaxZoomHead(final float f, final CallbackInfoReturnable<Float> cir) {
        final Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.options.getCameraType() == SableCameraTypes.SUB_LEVEL_VIEW || minecraft.options.getCameraType() == SableCameraTypes.SUB_LEVEL_VIEW_UNLOCKED) {
            final Entity cameraEntity = minecraft.cameraEntity;
            final Entity vehicle = cameraEntity.getVehicle();

            final boolean isTypeValid = vehicle != null;
            if (isTypeValid) {
                final SubLevel subLevel = Sable.HELPER.getContaining(minecraft.level, vehicle.position());

                if (subLevel != null) {
                    final float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
                    final float zoomAmount = Mth.lerp(partialTick, this.sable$lastInterpolatedZoom, this.sable$interpolatedZoom);

                    final BoundingBox3ic boundingBox = subLevel.getPlot().getBoundingBox();
                    final Vec3 extents = new Vec3(boundingBox.maxX() - boundingBox.minX(), boundingBox.maxY() - boundingBox.minY(), boundingBox.maxZ() - boundingBox.minZ());
                    final double maxDist = extents.scale(0.5).length();
                    final float desiredDistance = (float) Math.max(f, maxDist) * (1.75f + zoomAmount);
                    cir.setReturnValue(this.sable$clampZoom(desiredDistance, subLevel));
                    this.sable$pushed = false;
                    return;
                }
            }
        }

        final LevelPoseProviderExtension extension = ((LevelPoseProviderExtension) minecraft.level);
        assert extension != null;
        extension.sable$pushPoseSupplier((subLevel) -> ((ClientSubLevel) subLevel).renderPose(minecraft.getTimer().getGameTimeDeltaPartialTick(false)));
        this.sable$pushed = true;
    }

    @WrapOperation(method = "getMaxZoom", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private double sable$getMaxZoom(final Vec3 instance, final Vec3 vec3, Operation<Double> orignal) {
        return Sable.HELPER.distanceSquaredWithSubLevels((Level) this.level, instance, vec3, orignal);
    }

    @Inject(method = "getMaxZoom", at = @At(value = "RETURN"))
    private void sable$getMaxZoomTail(final float f, final CallbackInfoReturnable<Float> cir) {
        if (this.sable$pushed) {
            final LevelPoseProviderExtension extension = ((LevelPoseProviderExtension) Minecraft.getInstance().level);
            assert extension != null;
            extension.sable$popPoseSupplier();
            this.sable$pushed = false;
        }
    }

    @Override
    public float sable$getZoomAmount() {
        return this.sable$zoomAmount;
    }

    @Override
    public void sable$setZoomAmount(final float sable$zoomAmount) {
        this.sable$zoomAmount = Mth.clamp(sable$zoomAmount, 0.0f, 4.0f);
    }
}
