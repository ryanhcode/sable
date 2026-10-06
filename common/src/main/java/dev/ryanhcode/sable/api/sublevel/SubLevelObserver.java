package dev.ryanhcode.sable.api.sublevel;

import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;

/**
 * Observes additions, removals, and ticking of sub-levels.
 */
public interface SubLevelObserver {

    /**
     * Called after a sub-level is added to a {@link SubLevelContainer}.
     *
     * @param subLevel the sub-level that was added
     */
    default void onSubLevelAdded(final SubLevel subLevel) {
    }

    /**
     * Called before a sub-level is removed from a {@link SubLevelContainer}.
     *
     * @param subLevel the sub-level that will be removed
     */
    default void onSubLevelRemoved(final SubLevel subLevel, final SubLevelRemovalReason reason) {
    }

    /**
     * Called after a server sub-level has moved between level containers.
     * Implementations must replace references to {@code source} with {@code destination}.
     *
     * @param source      the removed source instance
     * @param destination the replacement instance in the destination level
     */
    default void onSubLevelTransferred(final SubLevel source, final SubLevel destination) {
    }

    /**
     * Called every tick for each {@link SubLevelContainer}.
     *
     * @param subLevels the sub-level container that is ticking
     */
    default void tick(final SubLevelContainer subLevels) {
    }
}
