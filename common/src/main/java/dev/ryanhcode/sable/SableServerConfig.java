package dev.ryanhcode.sable;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SableServerConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue SUB_LEVEL_SUBSTEPS_PER_TICK;

    static {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        SUB_LEVEL_SUBSTEPS_PER_TICK = builder
                .comment("How many times the physics simulation is stepped in every second. Higher values will be significantly more performance intensive, but will have higher accuracy.")
                .defineInRange("sub_level_substeps_per_tick", 2, 1, 10);

        SPEC = builder.build();
    }
}
