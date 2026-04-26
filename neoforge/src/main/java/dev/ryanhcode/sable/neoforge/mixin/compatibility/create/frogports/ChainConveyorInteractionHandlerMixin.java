package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.frogports;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorInteractionHandler;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorShape;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChainConveyorInteractionHandler.class)
public class ChainConveyorInteractionHandlerMixin {

    @Shadow
    public static BlockPos selectedLift;

    @Shadow
    public static ChainConveyorShape selectedShape;

    @WrapOperation(method = "clientTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private static double sable$addParticleInternal(final Vec3 instance, final Vec3 vec3, Operation<Double> original) {
        return Sable.HELPER.distanceSquaredWithSubLevels(Minecraft.getInstance().level, instance, vec3, original);
    }

    @WrapOperation(method = "clientTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;", ordinal = 0))
    private static Vec3 sable$fromSubLiftVec(final Vec3 from, final Vec3 liftVec, Operation<Vec3> original, @Local(ordinal = 0) final ChainConveyorShape shape) {
        final SubLevel subLevel = Sable.HELPER.getContainingClient(liftVec);

        if (subLevel != null) {
            return original.call(subLevel.logicalPose().transformPositionInverse(from), liftVec);
        }

        return original.call(from, liftVec);
    }

    @WrapOperation(method = "clientTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;", ordinal = 1))
    private static Vec3 sable$toSubLiftVec(final Vec3 to, final Vec3 liftVec, Operation<Vec3> original, @Local(ordinal = 0) final ChainConveyorShape shape) {
        final SubLevel subLevel = Sable.HELPER.getContainingClient(liftVec);

        if (subLevel != null) {
            return original.call(subLevel.logicalPose().transformPositionInverse(to), liftVec);
        }

        return original.call(to, liftVec);
    }

    @WrapMethod(method = "drawCustomBlockSelection")
    private static void getPos(PoseStack ms, MultiBufferSource buffer, Vec3 camera, Operation<Void> original,
                               @Share("tranformedPos") LocalRef<BlockPos> tranformedPos, @Share("orientation") LocalRef<Quaternionf> orientation) {
        if (selectedLift != null) {
            Vec3 pos = Vec3.atLowerCornerOf(selectedLift);

            final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(pos);
            if (subLevel != null) {
                final Pose3dc renderPose = subLevel.renderPose();
                tranformedPos.set(BlockPos.containing(renderPose.transformPosition(pos)));
                orientation.set(new Quaternionf(renderPose.orientation()));
            }
        }
        original.call(ms, buffer, camera);
    }

    @WrapOperation(method = "drawCustomBlockSelection", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;getX()I"),
            @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;getY()I"),
            @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;getZ()I")
    })
    private static int useTranformedPos(BlockPos instance, Operation<Integer> original, @Share("tranformedPos") LocalRef<BlockPos> tranformedPos) {
        if (tranformedPos.get() != null)
            return original.call(tranformedPos.get());
        return original.call(instance);
    }

    @WrapOperation(method = "drawCustomBlockSelection", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"))
    private static void multiplyByRotation(PoseStack instance, double x, double y, double z, Operation<Void> original, @Share("orientation") LocalRef<Quaternionf> orientation) {
        original.call(instance, x, y, z);
        if (orientation.get() != null)
            instance.mulPose(orientation.get());
    }
}
