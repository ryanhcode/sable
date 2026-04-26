package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.mechnical_arm;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointHandler;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmInteractionPointHandler.class)
public class MechanicalArmSublevelFailure {

	@Inject(method = "flushSettings", at = @At("HEAD"))
	private static void sable$gatherSublevelInformation(final BlockPos pos, final CallbackInfo ci, @Share("parentSublevel") final LocalRef<SubLevel> parentSublevel, @Share("pointsRemovedSublevel") final LocalRef<Integer> pointsRemovedSublevel) {
		parentSublevel.set(Sable.HELPER.getContainingClient(pos));
		pointsRemovedSublevel.set(0);
	}

	@Inject(method = "flushSettings", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;closerThan(Lnet/minecraft/core/Vec3i;D)Z"))
	private static void sable$removeDifferentSublevelPoints(final BlockPos pos, final CallbackInfo ci, @Local(name = "point") final ArmInteractionPoint point, @Share("pointsRemovedSublevel") final LocalRef<Integer> pointsRemovedSublevel, @Share("parentSublevel") final LocalRef<SubLevel> parentSublevel) {
		final SubLevel pointsublevel = Sable.HELPER.getContainingClient(point.getPos());
		if (parentSublevel.get() != pointsublevel) {
			pointsRemovedSublevel.set(pointsRemovedSublevel.get() + 1);
		}
	}

	@WrapOperation(method = "flushSettings", at = @At(value = "INVOKE", target = "Lnet/createmod/catnip/lang/LangBuilder;translate(Ljava/lang/String;[Ljava/lang/Object;)Lnet/createmod/catnip/lang/LangBuilder;"))
	private static LangBuilder sable$relayRemovedPoints(final LangBuilder instance, final String langKey, final Object[] args, Operation<LangBuilder> original, @Local(name = "removed") final int removed, @Share("pointsRemovedSublevel") final LocalRef<Integer> pointsRemovedSublevel) {

		final Integer arg = (Integer) args[0];
		Component errorComponent = Component.empty();
		if (pointsRemovedSublevel.get() == 0) {
			original.call(instance, langKey, args);
		} else if (arg - pointsRemovedSublevel.get() == 0) {
			errorComponent = Component.translatable("sable.create.remove.points_removed_sublevel", removed)
					.withStyle(ChatFormatting.RED);
		} else {
			errorComponent = Component.translatable("sable.create.mechanical_arm.points_removed_sublevel_and_range", removed)
					.withStyle(ChatFormatting.RED);
		}

		instance.add(errorComponent);
		return instance;
	}
}
