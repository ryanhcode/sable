package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.frogports;

import com.llamalad7.mixinextras.sugar.Local;
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
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin into {@link com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorInteractionHandler} so chain conveyors correctly interact and render in sub-levels.
 */

@Mixin(ChainConveyorInteractionHandler.class)
public class ChainConveyorInteractionHandlerMixin {

    @Shadow
    public static BlockPos selectedLift;

    @Shadow
    public static ChainConveyorShape selectedShape;

    @Redirect(method = "clientTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private static double sable$addParticleInternal(final Vec3 instance, final Vec3 vec3) {
        return Sable.HELPER.distanceSquaredWithSubLevels(Minecraft.getInstance().level, instance, vec3);
    }

    @Redirect(method = "clientTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;", ordinal = 0))
    private static Vec3 sable$fromSubLiftVec(final Vec3 from, final Vec3 liftVec, @Local(ordinal = 0) final ChainConveyorShape shape) {
        final SubLevel subLevel = Sable.HELPER.getContainingClient(liftVec);

        if (subLevel != null) {
            return subLevel.logicalPose().transformPositionInverse(from).subtract(liftVec);
        }

        return from.subtract(liftVec);
    }

    @Redirect(method = "clientTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;", ordinal = 1))
    private static Vec3 sable$toSubLiftVec(final Vec3 to, final Vec3 liftVec, @Local(ordinal = 0) final ChainConveyorShape shape) {
        final SubLevel subLevel = Sable.HELPER.getContainingClient(liftVec);

        if (subLevel != null) {
            return subLevel.logicalPose().transformPositionInverse(to).subtract(liftVec);
        }

        return to.subtract(liftVec);
    }

    /**
     * @author RyanH
     * @reason Take sub-levels into account
     */
    @Overwrite
    public static void drawCustomBlockSelection(final PoseStack ms, final MultiBufferSource buffer, final Vec3 camera) {
        if (selectedLift == null || selectedShape == null)
            return;

        final VertexConsumer vb = buffer.getBuffer(RenderType.lines());

        ms.pushPose();

        Vec3 pos = Vec3.atLowerCornerOf(selectedLift);

        final SubLevel subLevel = Sable.HELPER.getContainingClient(pos);

        if (subLevel instanceof final ClientSubLevel clientSubLevel) {
            final Pose3dc renderPose = clientSubLevel.renderPose();
            pos = renderPose.transformPosition(pos);
            ms.translate(pos.x() - camera.x, pos.y() - camera.y, pos.z() - camera.z);
            ms.mulPose(new Quaternionf(renderPose.orientation()));
        } else {
            ms.translate(pos.x() - camera.x, pos.y() - camera.y, pos.z() - camera.z);
        }

        ((ChainConveyorShapeAccessor) selectedShape).invokeDrawOutline(selectedLift, ms, vb);
        ms.popPose();
    }

}
