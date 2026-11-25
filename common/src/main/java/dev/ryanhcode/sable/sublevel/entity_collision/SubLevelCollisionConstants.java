package dev.ryanhcode.sable.sublevel.entity_collision;

public class SubLevelCollisionConstants {

    public static final double VERTICAL_COLLISION_MIN_ANGLE_DEGREES = 40.0;
    public static final double VERTICAL_COLLISION_MIN_DOT = Math.cos(Math.toRadians(VERTICAL_COLLISION_MIN_ANGLE_DEGREES));

    public static final double WALKABLE_SLOPE_MIN_ANGLE_DEGREES = 40.0;
    public static final double WALKABLE_SLOPE_MIN_DOT = Math.cos(Math.toRadians(WALKABLE_SLOPE_MIN_ANGLE_DEGREES));

    /**
     * Largest volume, in blocks, that a single sub-level collision pass may consider.
     * <p>
     * The local bounds are the union of the entity's swept box seen from the sub-level's previous pose and
     * from its current one. Each of those is normally about the size of the entity, but when the two poses
     * are far apart the union spans the gap between them and stops describing anything real — the ship and
     * the entity are both small, the gap is not.
     */
    public static final double MAX_LOCAL_COLLISION_VOLUME = 500 * 500 * 500;

}
