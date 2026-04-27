package dev.ryanhcode.sable;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SableConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue SUB_LEVEL_SPLITTING;
    public static final ModConfigSpec.IntValue SUB_LEVEL_SPLITTING_HEATMAP_STEPS_PER_TICK;
    public static final ModConfigSpec.DoubleValue SUB_LEVEL_TRACKING_RANGE;
    public static final ModConfigSpec.DoubleValue SUB_LEVEL_REMOVE_MIN;
    public static final ModConfigSpec.DoubleValue SUB_LEVEL_REMOVE_MAX;
    public static final ModConfigSpec.DoubleValue VELOCITY_RETAINED_ON_LOAD;
    public static final ModConfigSpec.DoubleValue SUB_LEVEL_PUNCH_STRENGTH_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue SUB_LEVEL_PUNCH_DOWNWARD_STRENGTH_MULTIPLIER;
    public static final ModConfigSpec.IntValue SUB_LEVEL_PUNCH_COOLDOWN_TICKS;
    public static final ModConfigSpec.BooleanValue DISABLE_UDP_PIPELINE;
    public static final ModConfigSpec.BooleanValue ATTEMPT_UDP_NETWORKING;

    static {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        SUB_LEVEL_SPLITTING = builder
                .comment("Whether sub-levels can split when parts are separated")
                .define("sub_level_splitting", true);
        SUB_LEVEL_SPLITTING_HEATMAP_STEPS_PER_TICK = builder
                .comment("Sub-level splitting heatmap steps that take place per tick")
                .defineInRange("sub_level_splitting_heatmap_steps", 200, 1, Integer.MAX_VALUE);
        SUB_LEVEL_TRACKING_RANGE = builder
                .comment("The distance to network sub-levels to players at")
                .defineInRange("sub_level_tracking_range", 320.0, 1.0, Double.MAX_VALUE);
        SUB_LEVEL_REMOVE_MIN = builder
                .comment("The minimum y coordinate sub-levels can exist at")
                .defineInRange("sub_level_remove_min", -10_000, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        SUB_LEVEL_REMOVE_MAX = builder
                .comment("The maximum y coordinate sub-levels can exist at")
                .defineInRange("sub_level_remove_max", 100_000, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        VELOCITY_RETAINED_ON_LOAD = builder
                .comment("The fraction of velocity that is retained when a sub-level is loaded in. A value of 0.0 will " +
                        "indicate that no velocity should be carried over, while a value of 1.0 would carry over 100% of " +
                        "velocity on load.")
                .defineInRange("sub_level_velocity_retained_on_load", 0.9, 0.0, 1.0);
        SUB_LEVEL_PUNCH_STRENGTH_MULTIPLIER = builder
                .comment("The strength multiplier applied to sub-level punching impulses")
                .defineInRange("sub_level_punch_strength_multiplier", 2.1, 0.0, Double.POSITIVE_INFINITY);
        SUB_LEVEL_PUNCH_DOWNWARD_STRENGTH_MULTIPLIER = builder
                .comment("The strength multiplier applied to the vertical component of downward sub-level punching impulses (to prevent jumping by punching the ground while standing on something light)")
                .defineInRange("sub_level_punch_downward_strength_multiplier", 0.175, 0.0, Double.POSITIVE_INFINITY);
        SUB_LEVEL_PUNCH_COOLDOWN_TICKS = builder
                .comment("The cooldown in ticks between sub-level punches")
                .defineInRange("sub_level_punch_cooldown_ticks", 3, 0, Integer.MAX_VALUE);
        DISABLE_UDP_PIPELINE = builder
                .comment("If the entire Sable UDP Networking pipeline should be disabled. This can improve compatibility with certain mods like Replay mod and certain networking setups, but will have worse performance and latency for networking sub-levels.")
                .define("disable_udp_pipeline", false);
        ATTEMPT_UDP_NETWORKING = builder
                .comment("If Sable should attempt to authenticate with clients and send them sub-level data over UDP")
                .define("attempt_udp_networking", true);

        SPEC = builder.build();
    }
}
