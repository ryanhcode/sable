package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.tracks;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import com.simibubi.create.content.trains.track.TrackBlockOutline;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.Translate;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TrackBlockOutline.class)
public class TrackBlockOutlineMixin {

    /**
     * Translating the render of curve sections, rotation is not needed
     * */
    @WrapOperation(method = "drawCurveSelection", at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/lib/transform/PoseTransformStack;translate(DDD)Ldev/engine_room/flywheel/lib/transform/Translate;", ordinal = 0))
    private static Translate<?> sable$translateCurveFactoringSubLevels(final PoseTransformStack ms,
                                                                       final double x,
                                                                       final double y,
                                                                       final double z,
                                                                       final Operation<Translate<?>> original,
                                                                       @Local(name = "result") final TrackBlockOutline.BezierPointSelection result,
                                                                       @Local(name = "camera") final Vec3 camera) {
        final Level level = Minecraft.getInstance().level;

        if (level == null) {
            return original.call(ms, x, y, z);
        }

        final Vec3 bezierPos = result.vec();
        final ClientSubLevel subLevel = (ClientSubLevel) Sable.HELPER.getContaining(level, bezierPos);

        if (subLevel == null) {
            return original.call(ms, x, y, z);
        }

        Vec3 worldPos = subLevel.renderPose().transformPosition(bezierPos);
        worldPos = worldPos.subtract(camera);
        return ms
                .translate(worldPos.x, worldPos.y, worldPos.z)
                .rotate(new Quaternionf(subLevel.renderPose().orientation()));
    }

    /**
     * Translating the render of normal block outlines, rotation is needed here
     * */
    @Redirect(method = "drawCustomBlockSelection", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"))
    private static void sable$translateBlockFactoringSubLevels(final PoseStack instance,
                                                               final double x,
                                                               final double y,
                                                               final double z,
                                                               @Local(name = "camPos") final Vec3 camPos,
                                                               @Local(name = "pos") final BlockPos pos) {
        final Level level = Minecraft.getInstance().level;

        if (level == null) {
            instance.translate(x, y, z);
            return;
        }

        final ClientSubLevel subLevel = (ClientSubLevel) Sable.HELPER.getContaining(level, pos);

        if (subLevel == null) {
            instance.translate(x, y, z);
            return;
        }

        final Vec3 localPos = subLevel.renderPose().transformPosition(Vec3.atLowerCornerOf(pos));
        instance.translate(localPos.x - camPos.x, localPos.y - camPos.y, localPos.z - camPos.z);
        instance.mulPose(new Quaternionf(subLevel.renderPose().orientation()));
    }

    /**
     * Provides the subLevel to the other 2 redirects so that the curve can translate its bounds and position to world space
     */
    @Inject(method = "pickCurves", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/trains/track/BezierConnection;isPrimary()Z"))
    private static void sable$findBlockEntitySubLevel(final CallbackInfo ci,
                                                      @Share("currentBlockEntitySubLevel") final LocalRef<ClientSubLevel> subLevel,
                                                      @Local(name = "be") final TrackBlockEntity be) {
        subLevel.set((ClientSubLevel) Sable.HELPER.getContaining(be));
    }

    @Redirect(method = "pickCurves", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/trains/track/BezierConnection;getBounds()Lnet/minecraft/world/phys/AABB;"))
    private static AABB sable$getWorldSpaceBounds(final BezierConnection instance,
                                                  @Share("currentBlockEntitySubLevel") final LocalRef<ClientSubLevel> subLevel,
                                                  @Local(name = "bc") final BezierConnection bc) {
        if (subLevel.get() == null) {
            return instance.getBounds();
        }

        final float partialTicks = AnimationTickHolder.getPartialTicks(Minecraft.getInstance().level);
        final BoundingBox3d localBounds = new BoundingBox3d(instance.getBounds()).transform(subLevel.get().renderPose(partialTicks));
        return localBounds.toMojang();
    }

    @Redirect(method = "pickCurves", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getEyePosition(F)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 sable$getEyePosition(final LocalPlayer entity, final float partialTicks) {
        return Sable.HELPER.getEyePositionInterpolated(entity, partialTicks);
    }

    @Redirect(method = "pickCurves", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private static double sable$distanceToHitSquared(final Vec3 vecA, final Vec3 vecB) {
        return Sable.HELPER.distanceSquaredWithSubLevels(Minecraft.getInstance().level, vecA, vecB);
    }

    @Redirect(method = "pickCurves", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;", ordinal = 1))
    private static Vec3 sable$getLocalOrigin(final Vec3 origin,
                                             final Vec3 anchor,
                                             @Share("currentBlockEntitySubLevel") final LocalRef<ClientSubLevel> subLevel,
                                             @Local(name = "bc") final BezierConnection bc) {
        if (subLevel.get() == null) {
            return origin.subtract(anchor);
        }

        final float partialTicks = AnimationTickHolder.getPartialTicks(Minecraft.getInstance().level);
        final Vec3 localOrigin = subLevel.get().renderPose(partialTicks).transformPositionInverse(origin);
        return localOrigin.subtract(anchor);
    }

    @Redirect(method = "pickCurves", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;", ordinal = 2))
    private static Vec3 sable$getLocalTarget(final Vec3 target,
                                             final Vec3 origin,
                                             @Share("currentBlockEntitySubLevel") final LocalRef<ClientSubLevel> subLevel,
                                             @Local(name = "bc") final BezierConnection bc) {
        if (subLevel.get() == null) {
            return target.subtract(origin);
        }

        final float partialTicks = AnimationTickHolder.getPartialTicks(Minecraft.getInstance().level);
        final Vec3 localTarget = subLevel.get().renderPose(partialTicks).transformPositionInverse(target);
        final Vec3 localOrigin = subLevel.get().renderPose(partialTicks).transformPositionInverse(origin);
        return localTarget.subtract(localOrigin);
    }

}