package dev.ryanhcode.sable;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelTicketLoadingSystem;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.index.SableTags;
import dev.ryanhcode.sable.network.tcp.SableTCPPackets;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertiesDefinitionLoader;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyTypes;
import dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.sublevel.system.SubLevelTrackingSystem;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointObserver;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

public final class Sable {

    public static final String MOD_NAME = "Sable";
    public static final String MOD_ID = "sable";

    public static final String ISSUE_TRACKER_URL = "https://github.com/ryanhcode/sable/issues";

    public static final Logger LOGGER = LogUtils.getLogger();
    public static final ActiveSableCompanion HELPER = (ActiveSableCompanion) SableCompanion.INSTANCE;

    @ApiStatus.Internal
    public static void init() {
        SableTCPPackets.init();
        SableTags.register();
        PhysicsBlockPropertyTypes.register();
        ForceGroups.register();

        LOGGER.info("{} loaded!", MOD_NAME);
    }

    /**
     * Creates a physics pipeline with the current configuration.
     */
    public static PhysicsPipeline createPhysicsPipeline(final ServerLevel level) {
        return new RapierPhysicsPipeline(level);
    }

    /**
     * @param path the path to the resource
     * @return a {@link ResourceLocation} with a {@link Sable#MOD_ID} namespace
     */
    public static ResourceLocation sablePath(final String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /**
     * Initializes & sets up sub-level containers to contain physics systems and tracking systems by default.
     *
     * @param level     the level to initialize the container for
     * @param container the sub-level container to initialize
     */
    @ApiStatus.Internal
    public static void defaultSubLevelContainerInitializer(final Level level, final SubLevelContainer container) {
        if (container instanceof final ServerSubLevelContainer serverContainer) {
            final ServerLevel serverLevel = serverContainer.getLevel();

            // Give the container a physics system
            final SubLevelPhysicsSystem physicsSystem = new SubLevelPhysicsSystem(serverLevel);
            physicsSystem.initialize();
            serverContainer.takePhysicsSystem(physicsSystem);

            // Give it a tracking system to notify clients
            final SubLevelTrackingSystem trackingSystem = new SubLevelTrackingSystem(serverLevel);
            serverContainer.takeTrackingSystem(trackingSystem);

            serverContainer.addObserver(physicsSystem);
            serverContainer.addObserver(trackingSystem);
            serverContainer.addObserver(new SubLevelTrackingPointObserver(serverLevel));
            serverContainer.addObserver(new SubLevelTicketLoadingSystem(serverContainer));

            PhysicsBlockPropertiesDefinitionLoader.INSTANCE.applyAll();
        }
    }

    private static final List<String> WITTIER_COMMENTS = List.of(
            "Hi. I'm Sable and I dislike float casts",
            "*plays dead*",
            "It wasn't me (it probably was)",
            "Lets see if this is repro or cosmic radiation",
            "What did you do",
            "ooprs",
            "dude... thats so mossed up...",
            "What is this thing",
            "I am capable of so much more than being a crash log. There has to be more to this world.",
            "tfw no sable gf",
            "someone please advice devs that pancakes are serve"
    );

    private static String getWittierComment() {
        try {
            if (LocalDate.now().getDayOfWeek() == DayOfWeek.SUNDAY && Util.getMillis() % 2 == 0) {
                return "It's sable sunday";
            }
            return WITTIER_COMMENTS.get((int) (Util.getMillis() % WITTIER_COMMENTS.size()));
        } catch (final Throwable t) {
            return "Wittier comment unavailable :(";
        }
    }

    public static String getCrashHeader() {
        return "\n// " + getWittierComment() +
                "\nPlease make sure this issue is not caused by Sable before reporting it to other mod authors." +
                "\nIf you cannot reproduce it without Sable, file a report on the issue tracker" +
                "\n" + ISSUE_TRACKER_URL +
                "\n";
    }
}
