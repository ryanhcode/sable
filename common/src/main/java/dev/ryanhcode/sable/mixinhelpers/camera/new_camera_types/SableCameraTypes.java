package dev.ryanhcode.sable.mixinhelpers.camera.new_camera_types;

import net.minecraft.client.CameraType;

public final class SableCameraTypes {

    public static final CameraType SUB_LEVEL_VIEW = Enum.valueOf(CameraType.class, "SUB_LEVEL_VIEW");
    public static final CameraType SUB_LEVEL_VIEW_UNLOCKED = Enum.valueOf(CameraType.class, "SUB_LEVEL_VIEW_UNLOCKED");

    private SableCameraTypes() {
    }
}
