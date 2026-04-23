package dev.ryanhcode.sable.mixinhelpers.block_outline_render;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@ApiStatus.Internal
public class SubLevelCamera extends Camera {

    private Camera renderCamera;
    private final Quaterniond inverseOrientation = new Quaterniond();
    private final Quaternionf inverseOrientationf = new Quaternionf();
    private final Vector3f rotationYXZ = new Vector3f();

    private final BlockPos.MutableBlockPos blockPosition = new BlockPos.MutableBlockPos();
    private Vec3 pos = Vec3.ZERO;

    public void setCamera(final Camera renderCamera) {
        this.renderCamera = renderCamera;
    }

    public void setPose(@Nullable final Pose3dc pose) {
        if (pose != null) {
            final Vec3 pos = pose.transformPositionInverse(this.renderCamera.getPosition());

            final Quaternionf rotation = this.rotation();
            this.renderCamera.rotation().mul(this.inverseOrientationf.set(pose.orientation().invert(this.inverseOrientation)), rotation);

            this.blockPosition.set(pos.x, pos.y, pos.z);
            this.pos = pos;

            rotation.getEulerAnglesYXZ(this.rotationYXZ);

            this.getLookVector().set(0.0F, 0.0F, -1.0F).rotate(rotation);
            this.getUpVector().set(0.0F, 1.0F, 0.0F).rotate(rotation);
            this.getLeftVector().set(-1.0F, 0.0F, 0.0F).rotate(rotation);
        } else {
            this.pos = this.renderCamera.getPosition();
            this.blockPosition.set(this.pos.x, this.pos.y, this.pos.z);
            this.rotationYXZ.set(this.renderCamera.getXRot(), this.renderCamera.getYRot(), 0);

            final Quaternionf rotation = this.rotation();
            rotation.set(this.renderCamera.rotation());

            this.getLookVector().set(0.0F, 0.0F, -1.0F).rotate(rotation);
            this.getUpVector().set(0.0F, 1.0F, 0.0F).rotate(rotation);
            this.getLeftVector().set(-1.0F, 0.0F, 0.0F).rotate(rotation);
        }
    }

    public void clear() {
        this.renderCamera = null;
        this.pos = Vec3.ZERO;
    }

    @Override
    public @NotNull Vec3 getPosition() {
        return this.pos;
    }

    @Override
    public @NotNull BlockPos getBlockPosition() {
        return this.blockPosition;
    }

    @Override
    public float getXRot() {
        return (float) (180.0 / Math.PI * -this.rotationYXZ.x);
    }

    @Override
    public float getYRot() {
        return (float) (180.0 / Math.PI * -this.rotationYXZ.y + 180.0);
    }

    @Override
    public @NotNull Entity getEntity() {
        return this.renderCamera.getEntity();
    }

    @Override
    public boolean isInitialized() {
        return this.renderCamera.isInitialized();
    }

    @Override
    public boolean isDetached() {
        return this.renderCamera.isDetached();
    }

    @Override
    public @NotNull NearPlane getNearPlane() {
        return this.renderCamera.getNearPlane();
    }

    @Override
    public @NotNull FogType getFluidInCamera() {
        return this.renderCamera.getFluidInCamera();
    }

    @Override
    public void reset() {
        this.renderCamera.reset();
    }

    @Override
    public float getPartialTickTime() {
        return this.renderCamera.getPartialTickTime();
    }

    public Camera getRenderCamera() {
        return this.renderCamera;
    }
}
