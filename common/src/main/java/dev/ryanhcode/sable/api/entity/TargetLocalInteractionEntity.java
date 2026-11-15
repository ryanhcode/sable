package dev.ryanhcode.sable.api.entity;

import dev.ryanhcode.sable.sublevel.SubLevel;

/**
 * Implemented by synthetic interaction entities whose position and rotation
 * may already be expressed in a target SubLevel's local coordinate system.
 *
 * <p>When this returns {@code true}, Sable must not inverse-transform the
 * entity into the same SubLevel a second time.</p>
 */
public interface TargetLocalInteractionEntity {

    /**
     * @param targetSubLevel the SubLevel containing the interaction target
     * @return true when this entity is already expressed in the target's
     * local coordinate system
     */
    boolean sable$isAlreadyLocalTo(SubLevel targetSubLevel);
}