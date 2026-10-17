package dev.ryanhcode.sable.sublevel.storage;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;

/**
 * The reason a sub-level was removed from a {@link SubLevelContainer}
 */
public enum SubLevelRemovalReason {
    /**
     * The sub-level was removed because it was unloaded, not clearing occupancy data
     */
    UNLOADED(false),

    /**
     * The sub-level was removed because it was removed from the container, clearing occupancy data
     */
    REMOVED(true),

    /**
     * The sub-level was replaced by an equivalent instance in another level.
     * Its source plot is released without deleting entities as destroyed content.
     */
    TRANSFERRED(true);

    private final boolean clearsOccupancy;

    SubLevelRemovalReason(final boolean clearsOccupancy) {
        this.clearsOccupancy = clearsOccupancy;
    }

    public boolean clearsOccupancy() {
        return this.clearsOccupancy;
    }
}
