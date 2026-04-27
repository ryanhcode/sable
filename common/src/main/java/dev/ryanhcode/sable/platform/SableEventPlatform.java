package dev.ryanhcode.sable.platform;

import dev.ryanhcode.sable.api.event.SablePostPhysicsTickEvent;
import dev.ryanhcode.sable.api.event.SablePrePhysicsTickEvent;
import dev.ryanhcode.sable.api.event.SableSubLevelContainerReadyEvent;

public interface SableEventPlatform {
    SableEventPlatform INSTANCE = SablePlatformUtil.load(SableEventPlatform.class);

    /**
     * Registers a listener for when Sable has finished initialization for a level and its sub-level container is ready
     * to use.
     * @param event The event to register
     */
    void onSubLevelContainerReady(final SableSubLevelContainerReadyEvent event);

    /**
     * Registers a listener for when Sable's {@link dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem} is
     * ticking physics.
     * </br>
     * Note that multiple physics ticks are completed per game tick, based on the amount of configured sub-steps.
     * Logic that needs to influence the physics world should occur on the physics tick, and not the game tick
     * due to this reason.
     */
    void onPhysicsTick(final SablePrePhysicsTickEvent event);

    /**
     * Registers a listener for when Sable's {@link dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem} is
     * complete with a physics tick.
     * </br>
     * Note that multiple physics ticks are completed per game tick, based on the amount of configured sub-steps.
     * Logic that needs to influence the physics world should occur on the physics tick, and not the game tick
     * due to this reason.
     */
    void onPostPhysicsTick(final SablePostPhysicsTickEvent event);

}
