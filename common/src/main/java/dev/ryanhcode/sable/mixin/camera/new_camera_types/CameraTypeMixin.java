package dev.ryanhcode.sable.mixin.camera.new_camera_types;

import com.llamalad7.mixinextras.lib.apache.commons.ArrayUtils;
import dev.ryanhcode.sable.mixinhelpers.camera.new_camera_types.SableCameraTypes;
import net.minecraft.client.CameraType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * thank you thunder i love enum mixins
 */
@Mixin(CameraType.class)
public class CameraTypeMixin {
    @Shadow
    @Final
    @Mutable
    private static CameraType[] $VALUES;

    @Final
    @Shadow
    @Mutable
    private static CameraType[] VALUES;

    static {
        final var subLevelView = create("SUB_LEVEL_VIEW", $VALUES.length, false, false);

        $VALUES = ArrayUtils.add($VALUES, subLevelView);

        VALUES = ArrayUtils.add(VALUES, subLevelView);

        final var subLevelViewUnlocked = create("SUB_LEVEL_VIEW_UNLOCKED", $VALUES.length, false, false);

        $VALUES = ArrayUtils.add($VALUES, subLevelViewUnlocked);

        VALUES = ArrayUtils.add(VALUES, subLevelViewUnlocked);
    }

    @Invoker(value = "<init>")
    private static CameraType create(final String name, final int ordinal, final boolean firstPerson, final boolean mirrored) {
        throw new AssertionError("Unreachable");
    }

    /**
     * @author RyanH
     * @reason Sable camera type. TODO: Make this not as incompatible
     */
    @Overwrite
    public CameraType cycle() {
        if ((Object) this == SableCameraTypes.SUB_LEVEL_VIEW) {
            return SableCameraTypes.SUB_LEVEL_VIEW_UNLOCKED;
        }

        if ((Object) this == SableCameraTypes.SUB_LEVEL_VIEW_UNLOCKED) {
            return CameraType.THIRD_PERSON_FRONT;
        }

        return switch ((CameraType) (Object) this) {
            case CameraType.FIRST_PERSON -> CameraType.THIRD_PERSON_BACK;
            case CameraType.THIRD_PERSON_BACK -> SableCameraTypes.SUB_LEVEL_VIEW;
            case CameraType.THIRD_PERSON_FRONT -> CameraType.FIRST_PERSON;
            default -> null;
        };
    }
}