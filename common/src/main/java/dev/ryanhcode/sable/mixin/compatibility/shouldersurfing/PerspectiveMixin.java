package dev.ryanhcode.sable.mixin.compatibility.shouldersurfing;

import com.github.exopandora.shouldersurfing.api.client.IClientConfig;
import com.github.exopandora.shouldersurfing.api.model.CrosshairVisibility;
import com.github.exopandora.shouldersurfing.api.model.Perspective;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.lib.apache.commons.ArrayUtils;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.mixinhelpers.camera.new_camera_types.SableCameraTypes;
import dev.ryanhcode.sable.mixinhelpers.compatibility.shouldersurfing.SablePerspectives;
import net.minecraft.client.CameraType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Perspective.class)
public class PerspectiveMixin {

    @Shadow
    @Final
    @Mutable
    private static Perspective[] $VALUES;

    static {
        final var subLevelView = create("SUB_LEVEL_VIEW", $VALUES.length, SableCameraTypes.SUB_LEVEL_VIEW, CrosshairVisibility.NEVER);

        $VALUES = ArrayUtils.add($VALUES, subLevelView);

        final var subLevelViewUnlocked = create("SUB_LEVEL_VIEW_UNLOCKED", $VALUES.length, SableCameraTypes.SUB_LEVEL_VIEW_UNLOCKED, CrosshairVisibility.NEVER);

        $VALUES = ArrayUtils.add($VALUES, subLevelViewUnlocked);
    }

    @SuppressWarnings("SameParameterValue")
    @Invoker(value = "<init>")
    private static Perspective create(final String name, final int ordinal, final CameraType cameraType, final CrosshairVisibility defaultCrosshairVisibility) {
        throw new AssertionError("Unreachable");
    }

    @SuppressWarnings("ConstantValue")
    @WrapOperation(method = "next", at = @At(value = "INVOKE", target = "Lcom/github/exopandora/shouldersurfing/api/client/IClientConfig;replaceDefaultPerspective()Z"))
    public boolean nextPerspective(final IClientConfig instance, final Operation<Boolean> original) {
        if ((Object) this == SablePerspectives.SUB_LEVEL_VIEW || (Object) this == SablePerspectives.SUB_LEVEL_VIEW_UNLOCKED) {
            return false;
        }
        return original.call(instance);
    }

    @ModifyVariable(method = "next", at = @At(value = "STORE"), name = "next")
    public Perspective next(final Perspective next, @Local(argsOnly = true) final IClientConfig config) {
        if (config.replaceDefaultPerspective()) {
            if ((Object) this == Perspective.SHOULDER_SURFING) {
                return SablePerspectives.SUB_LEVEL_VIEW;
            }
        } else {
            // The normal logic will try to wrap around to our new values, but the next one should be first person
            if ((Object) this == Perspective.SHOULDER_SURFING) {
                return Perspective.FIRST_PERSON;
            }

            if ((Object) this == Perspective.THIRD_PERSON_BACK) {
                return SablePerspectives.SUB_LEVEL_VIEW;
            }
        }

        if ((Object) this == SablePerspectives.SUB_LEVEL_VIEW) {
            return SablePerspectives.SUB_LEVEL_VIEW_UNLOCKED;
        }
        if ((Object) this == SablePerspectives.SUB_LEVEL_VIEW_UNLOCKED) {
            return Perspective.THIRD_PERSON_FRONT;
        }

        return next;
    }

    @Inject(method = "next", at = @At("TAIL"), cancellable = true)
    public void getNext(final CallbackInfoReturnable<Perspective> cir, @Local(name = "next") final Perspective next) {
        if (next == SablePerspectives.SUB_LEVEL_VIEW) {
            cir.setReturnValue(SablePerspectives.SUB_LEVEL_VIEW);
        }
        if (next == SablePerspectives.SUB_LEVEL_VIEW_UNLOCKED) {
            cir.setReturnValue(SablePerspectives.SUB_LEVEL_VIEW_UNLOCKED);
        }
    }

    @SuppressWarnings("ConstantValue")
    @Inject(method = "isEnabled", at = @At("HEAD"), cancellable = true)
    public void isEnabled(final CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this == SablePerspectives.SUB_LEVEL_VIEW || (Object) this == SablePerspectives.SUB_LEVEL_VIEW_UNLOCKED) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "of", at = @At("HEAD"), cancellable = true)
    private static void of(final CameraType cameraType, final boolean shoulderSurfing, final CallbackInfoReturnable<Perspective> cir) {
        if (cameraType == SableCameraTypes.SUB_LEVEL_VIEW) {
            cir.setReturnValue(SablePerspectives.SUB_LEVEL_VIEW);
        }
        if (cameraType == SableCameraTypes.SUB_LEVEL_VIEW_UNLOCKED) {
            cir.setReturnValue(SablePerspectives.SUB_LEVEL_VIEW_UNLOCKED);
        }
    }
}
