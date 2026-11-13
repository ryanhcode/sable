package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.contraptions;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionCollider;
import com.simibubi.create.foundation.collision.Matrix3d;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin into {@link com.simibubi.create.content.contraptions.ContraptionCollider} so Create contraptions interact with sub-levels.
 */

@Mixin(value = ContraptionCollider.class, remap = false)
public class ContraptionColliderMixin {

    @Unique
    private static org.joml.Matrix3d sable$toJOML(final Matrix3d createMatrix) {
        final org.joml.Matrix3d jomlMatrix = new org.joml.Matrix3d();

        final Matrix3dAccessor accessor = ((Matrix3dAccessor) createMatrix);
        jomlMatrix.set(
                accessor.getM00(), accessor.getM01(), accessor.getM02(),
                accessor.getM10(), accessor.getM11(), accessor.getM12(),
                accessor.getM20(), accessor.getM21(), accessor.getM22()
        );

        return jomlMatrix;
    }

    @Unique
    private static Matrix3d sable$toCreate(final org.joml.Matrix3d jomlMatrix) {
        final Matrix3d createMatrix = new Matrix3d();
        final Matrix3dAccessor accessor = ((Matrix3dAccessor) createMatrix);

        accessor.setM00(jomlMatrix.m00);
        accessor.setM01(jomlMatrix.m01);
        accessor.setM02(jomlMatrix.m02);

        accessor.setM10(jomlMatrix.m10);
        accessor.setM11(jomlMatrix.m11);
        accessor.setM12(jomlMatrix.m12);

        accessor.setM20(jomlMatrix.m20);
        accessor.setM21(jomlMatrix.m21);
        accessor.setM22(jomlMatrix.m22);

        return createMatrix;
    }

    @Redirect(method = "collideEntities", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private static AABB sable$contraptionBounds(final AbstractContraptionEntity instance, @Share("subLevel") final LocalRef<SubLevel> contraptionSubLevel) {
        final SubLevel subLevel = Sable.HELPER.getContaining(instance);
        contraptionSubLevel.set(subLevel);

        if (subLevel != null) {
            final BoundingBox3d globalBB = new BoundingBox3d(instance.getBoundingBox());
            globalBB.transform(subLevel.logicalPose(), globalBB);
            return globalBB.toMojang();
        }

        return instance.getBoundingBox();
    }

    @Redirect(method = "collideEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;expandTowards(DDD)Lnet/minecraft/world/phys/AABB;"))
    private static AABB sable$entityQueryBounds(final AABB instance, final double d, final double e, final double f, @Local(argsOnly = true) final AbstractContraptionEntity contraption, @Share("subLevel") final LocalRef<SubLevel> contraptionSubLevel) {
        final SubLevel subLevel = contraptionSubLevel.get();

        if (subLevel != null) {
            final BoundingBox3d globalBB = new BoundingBox3d(contraption.getBoundingBox().inflate(2.0).expandTowards(d, e, f));
            globalBB.transform(subLevel.logicalPose(), globalBB);
            return globalBB.toMojang();
        }

        return instance.expandTowards(d, e, f);
    }

    @Redirect(method = "collideEntities", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;position()Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 sable$contraptionPosition(final AbstractContraptionEntity instance, @Share("subLevel") final LocalRef<SubLevel> contraptionSubLevel) {
        final SubLevel subLevel = contraptionSubLevel.get();

        if (subLevel != null) {
            return subLevel.logicalPose().transformPosition(instance.position());
        }

        return instance.position();
    }

    @Redirect(method = "collideEntities", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;getPrevPositionVec()Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 sable$getPrevPositionVec(final AbstractContraptionEntity instance, @Share("subLevel") final LocalRef<SubLevel> contraptionSubLevel) {
        final SubLevel subLevel = contraptionSubLevel.get();

        if (subLevel != null) {
            return subLevel.logicalPose().transformPosition(instance.getPrevPositionVec());
        }

        return instance.getPrevPositionVec();
    }

    @Redirect(method = "collideEntities", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;getAnchorVec()Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 sable$getAnchorVec(final AbstractContraptionEntity instance, @Share("subLevel") final LocalRef<SubLevel> contraptionSubLevel) {
        final SubLevel subLevel = contraptionSubLevel.get();

        if (subLevel != null) {
            return subLevel.logicalPose().transformPosition(instance.getAnchorVec().add(0.5, 0.5, 0.5)).subtract(0.5, 0.5, 0.5);
        }

        return instance.getAnchorVec();
    }


    @Redirect(method = "collideEntities", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity$ContraptionRotationState;asMatrix()Lcom/simibubi/create/foundation/collision/Matrix3d;"))
    private static Matrix3d sable$rotationMatrix(final AbstractContraptionEntity.ContraptionRotationState rotationState, @Local(argsOnly = true) final AbstractContraptionEntity contraption, @Share("subLevel") final LocalRef<SubLevel> contraptionSubLevel) {
        final SubLevel subLevel = contraptionSubLevel.get();
        if (subLevel != null) {
            final Pose3d pose = subLevel.logicalPose();
            final org.joml.Matrix3d jomlMatrix = sable$toJOML(rotationState.asMatrix());

            jomlMatrix.rotateLocal(pose.orientation());
            return sable$toCreate(jomlMatrix);
        }

        return rotationState.asMatrix();
    }

    @Redirect(method = "collideEntities", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;toLocalVector(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 sable$toLocalVector(final AbstractContraptionEntity instance, final Vec3 localVec, final float partialTicks, @Share("subLevel") final LocalRef<SubLevel> contraptionSubLevel) {
        final SubLevel subLevel = contraptionSubLevel.get();

        if (subLevel != null) {
            final Pose3d pose = subLevel.logicalPose();
            return instance.toLocalVector(pose.transformPositionInverse(localVec), partialTicks);
        }

        return instance.toLocalVector(localVec, partialTicks);
    }
    @Redirect(method = "collideEntities", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;getContactPointMotion(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 sable$getContactPointMotion(final AbstractContraptionEntity instance, final Vec3 globalContactPoint, @Share("subLevel") final LocalRef<SubLevel> contraptionSubLevel) {
        final SubLevel subLevel = contraptionSubLevel.get();
        if (subLevel != null) {
            final Pose3d pose = subLevel.logicalPose();
            final Vec3 localContactPoint = pose.transformPositionInverse(globalContactPoint);
            final Vec3 motion = pose.transformNormal(instance.getContactPointMotion(localContactPoint))
                    .add(globalContactPoint.subtract(subLevel.lastPose().transformPosition(localContactPoint)));
            return motion;
        }
        
        return instance.getContactPointMotion(globalContactPoint);
    }
}
