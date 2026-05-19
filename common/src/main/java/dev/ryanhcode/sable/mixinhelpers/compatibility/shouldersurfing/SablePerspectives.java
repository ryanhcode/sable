package dev.ryanhcode.sable.mixinhelpers.compatibility.shouldersurfing;

import com.github.exopandora.shouldersurfing.api.model.Perspective;

public class SablePerspectives {

    public static final Perspective SUB_LEVEL_VIEW = Enum.valueOf(Perspective.class, "SUB_LEVEL_VIEW");
    public static final Perspective SUB_LEVEL_VIEW_UNLOCKED = Enum.valueOf(Perspective.class, "SUB_LEVEL_VIEW_UNLOCKED");

    private SablePerspectives() {
    }
}
