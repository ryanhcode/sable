package dev.ryanhcode.sable.sublevel.entity_collision;

public class SubLevelCollisionConstants {

    public static final double VERTICAL_COLLISION_MIN_ANGLE_DEGREES = 40.0;
    public static final double VERTICAL_COLLISION_MIN_DOT = Math.cos(Math.toRadians(VERTICAL_COLLISION_MIN_ANGLE_DEGREES));

    public static final double WALKABLE_SLOPE_MIN_ANGLE_DEGREES = 40.0;
    public static final double WALKABLE_SLOPE_MIN_DOT = Math.cos(Math.toRadians(WALKABLE_SLOPE_MIN_ANGLE_DEGREES));

}
