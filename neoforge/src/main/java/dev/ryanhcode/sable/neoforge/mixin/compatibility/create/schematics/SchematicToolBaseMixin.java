package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.schematics;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.schematics.client.SchematicTransformation;
import com.simibubi.create.content.schematics.client.tools.SchematicToolBase;
import com.simibubi.create.foundation.utility.RaycastHelper;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

/**
 * Mixin into {@link com.simibubi.create.content.schematics.client.tools.SchematicToolBase} so schematic tool rays and selection account for sub-levels.
 */

@Mixin(SchematicToolBase.class)
public class SchematicToolBaseMixin {

    @Shadow
    protected Vec3 chasingSelectedPos;

    @Shadow
    protected Vec3 lastChasingSelectedPos;

    @Inject(method = "updateSelection", at = @At("TAIL"))
    public void sable$forceUpdateSelection(final CallbackInfo ci, @Local(ordinal = 0) final Vec3 target) {
        ActiveSableCompanion helper = Sable.HELPER;
        if (helper.getContainingClient(target) != helper.getContainingClient(this.lastChasingSelectedPos)) {
            this.lastChasingSelectedPos = this.chasingSelectedPos = target;
        }
    }

    @WrapOperation(method = "updateTargetPos", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/foundation/utility/RaycastHelper;rayTraceUntil(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Ljava/util/function/Predicate;)Lcom/simibubi/create/foundation/utility/RaycastHelper$PredicateTraceResult;"))
    public RaycastHelper.PredicateTraceResult sable$rayTraceSublevels(final Vec3 start, final Vec3 end, final Predicate<BlockPos> predicate, final Operation<RaycastHelper.PredicateTraceResult> original, @Local final LocalPlayer player, @Local final SchematicTransformation transformation) {
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(transformation.getAnchor());
        if (subLevel != null) {
            final Pose3dc pose = subLevel.renderPose();

            final Vec3 plotPlayerPos = pose.transformPositionInverse(player.getEyePosition());
            final Vec3 plotStart = transformation.toLocalSpace(plotPlayerPos);
            final Vec3 plotEnd = transformation.toLocalSpace(RaycastHelper.getTraceTarget(player, 70, plotPlayerPos));

            return original.call(plotStart, plotEnd, predicate);
        }

        return original.call(start, end, predicate);
    }
}
