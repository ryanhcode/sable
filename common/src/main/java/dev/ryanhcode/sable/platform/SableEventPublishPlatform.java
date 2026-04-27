package dev.ryanhcode.sable.platform;

import dev.ryanhcode.sable.api.event.SablePostPhysicsTickEvent;
import dev.ryanhcode.sable.api.event.SablePrePhysicsTickEvent;
import dev.ryanhcode.sable.api.event.SableSubLevelContainerReadyEvent;
import org.jetbrains.annotations.ApiStatus;

/**
 * Platform responsible for publishing sable events.
 */
@ApiStatus.Internal
public interface SableEventPublishPlatform extends SableSubLevelContainerReadyEvent, SablePrePhysicsTickEvent, SablePostPhysicsTickEvent {
    SableEventPublishPlatform INSTANCE = SablePlatformUtil.load(SableEventPublishPlatform.class);
}
