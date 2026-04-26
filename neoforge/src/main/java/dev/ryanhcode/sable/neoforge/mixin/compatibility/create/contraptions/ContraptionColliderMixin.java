package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.contraptions;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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
        final Matrix3dAccessor accessor = Matrix3dAccessor.class.cast(createMatrix);

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

    @WrapOperation(method = "collideEntities", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private static AABB sable$contraptionBounds(AbstractContraptionEntity instance, Operation<AABB> original, @Share("subLevel") final LocalRef<SubLevel> contraptionSubLevel) {
        final SubLevel subLevel = Sable.HELPER.getContaining(instance);
        contraptionSubLevel.set(subLevel);

        if (subLevel != null) {
            final BoundingBox3d globalBB = new BoundingBox3d(original.call(instance));
            globalBB.transform(subLevel.logicalPose(), globalBB);
            return globalBB.toMojang();
        }

        return instance.getBoundingBox();
    }

    @WrapOperation(method = "collideEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;expandTowards(DDD)Lnet/minecraft/world/phys/AABB;"))
    private static AABB sable$entityQueryBounds(final AABB instance, final double d, final double e, final double f, Operation<AABB> original, @Local(argsOnly = true) final AbstractContraptionEntity contraption, @Share("subLevel") final LocalRef<SubLevel> contraptionSubLevel) {
        final SubLevel subLevel = contraptionSubLevel.get();

        if (subLevel != null) {
            final BoundingBox3d globalBB = new BoundingBox3d(original.call(contraption.getBoundingBox(), d, e, f));
            globalBB.transform(subLevel.logicalPose(), globalBB);
            return globalBB.toMojang();
        }

        return original.call(instance, d, e, f);
    }

    @WrapOperation(method = "collideEntities", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;position()Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 sable$contraptionPosition(AbstractContraptionEntity instance, Operation<Vec3> original, @Share("subLevel") final LocalRef<SubLevel> contraptionSubLevel) {
        final SubLevel subLevel = contraptionSubLevel.get();

        if (subLevel != null) {
            return subLevel.logicalPose().transformPosition(original.call(instance));
        }

        return original.call(instance);
    }

    @WrapOperation(method = "collideEntities", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;getPrevPositionVec()Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 sable$getPrevPositionVec(AbstractContraptionEntity instance, Operation<Vec3> original, @Share("subLevel") final LocalRef<SubLevel> contraptionSubLevel) {
        final SubLevel subLevel = contraptionSubLevel.get();

        if (subLevel != null) {
            return subLevel.logicalPose().transformPosition(original.call(instance));
        }

        return original.call(instance);
    }

    @WrapOperation(method = "collideEntities", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;getAnchorVec()Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 sable$getAnchorVec(AbstractContraptionEntity instance, Operation<Vec3> original, @Share("subLevel") final LocalRef<SubLevel> contraptionSubLevel) {
        final SubLevel subLevel = contraptionSubLevel.get();

        if (subLevel != null) {
            return subLevel.logicalPose().transformPosition(original.call(instance).add(0.5, 0.5, 0.5)).subtract(0.5, 0.5, 0.5);
        }

        return original.call(instance);
    }


    @WrapOperation(method = "collideEntities", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity$ContraptionRotationState;asMatrix()Lcom/simibubi/create/foundation/collision/Matrix3d;"))
    private static Matrix3d sable$rotationMatrix(final AbstractContraptionEntity.ContraptionRotationState rotationState, Operation<Matrix3d> original, @Local(argsOnly = true) final AbstractContraptionEntity contraption, @Share("subLevel") final LocalRef<SubLevel> contraptionSubLevel) {
        final SubLevel subLevel = contraptionSubLevel.get();
        if (subLevel != null) {
            final Pose3d pose = subLevel.logicalPose();
            final org.joml.Matrix3d jomlMatrix = sable$toJOML(original.call(rotationState));

            jomlMatrix.rotateLocal(pose.orientation());
            return sable$toCreate(jomlMatrix);
        }

        return original.call(rotationState);
    }

    @WrapOperation(method = "collideEntities", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;toLocalVector(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 sable$toLocalVector(AbstractContraptionEntity instance, Vec3 localVec, float partialTicks, Operation<Vec3> original, @Share("subLevel") final LocalRef<SubLevel> contraptionSubLevel) {
        final SubLevel subLevel = contraptionSubLevel.get();

        if (subLevel != null) {
            final Pose3d pose = subLevel.logicalPose();
            return instance.toLocalVector(pose.transformPositionInverse(localVec), partialTicks);
        }

        return original.call(instance, localVec, partialTicks);
    }
    @WrapOperation(method = "collideEntities", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;getContactPointMotion(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 sable$getContactPointMotion(AbstractContraptionEntity instance, Vec3 globalContactPoint, Operation<Vec3> original, @Share("subLevel") final LocalRef<SubLevel> contraptionSubLevel) {
        final SubLevel subLevel = contraptionSubLevel.get();
        if (subLevel != null) {
            final Pose3d pose = subLevel.logicalPose();
            final Vec3 localContactPoint = pose.transformPositionInverse(globalContactPoint);
            final Vec3 motion = pose.transformNormal(original.call(instance, localContactPoint))
                    .add(globalContactPoint.subtract(subLevel.lastPose().transformPosition(localContactPoint)));
            return motion;
        }
        
        return original.call(instance, globalContactPoint);
    }
}
