package dev.ryanhcode.sable.physics.config.dimension_physics;

import com.mojang.datafixers.kinds.Applicative;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import java.util.Optional;

public record DimensionPhysics(ResourceLocation dimension, int priority, Optional<Float> universalDrag,
                               Optional<Vector3f> baseGravity, Optional<Double> basePressure,
                               Optional<BezierResourceFunction> pressureFunction, Optional<Vector3f> magneticNorth,
                               boolean ignoreChunks) {
    public static final Vector3f DEFAULT_GRAVITY = new Vector3f(0.0f, -11.0f, 0.0f);
    public static final Vector3f DEFAULT_MAGNETIC_NORTH = new Vector3f(0, 0, 0);
    public static final double DEFAULT_PRESSURE = 1.0;
    private static final float DEFAULT_UNIVERSAL_DRAG = 0.09f;

    public static final Codec<DimensionPhysics> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("dimension").forGetter(DimensionPhysics::dimension),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("priority", 1000).forGetter(DimensionPhysics::priority),
            Codec.optionalField("universal_drag", Codec.FLOAT, false).forGetter(DimensionPhysics::universalDrag),
            Codec.optionalField("base_gravity", ExtraCodecs.VECTOR3F, false).forGetter(DimensionPhysics::baseGravity),
            Codec.optionalField("base_pressure", Codec.DOUBLE, false).forGetter(DimensionPhysics::basePressure),
            Codec.optionalField("pressure_function", BezierResourceFunction.CODEC, false).forGetter(DimensionPhysics::pressureFunction),
            Codec.optionalField("magnetic_north", ExtraCodecs.VECTOR3F, false).forGetter(DimensionPhysics::magneticNorth),
            Codec.BOOL.optionalFieldOf("ignore_chunks", false).forGetter(DimensionPhysics::ignoreChunks)
    ).apply(Applicative.unbox(instance), DimensionPhysics::new));

    public static DimensionPhysics createDefault(final Level level) {
        // constructs a bezier air pressure curve approximating an exponential decay, centered around sea level
        // clamped to at most 1.5 pressure underground, and with a 40-meter smooth drop-off at the build limit
        final double seaLevel = level.getSeaLevel();

        double currentAltitude = level.dimensionType().minY();
        final double maxAltitude = currentAltitude + level.dimensionType().logicalHeight();

        final double baseSlope = -0.004;
        final double maxPressure = 1.5;
        final double maxStep = 200;

        final double smoothingAltitude = maxAltitude - 40;

        // clamps the bottom most point to have a pressure of 1.5 or less
        currentAltitude = Math.max(currentAltitude, Math.log(maxPressure) / baseSlope + seaLevel);

        final BezierResourceFunction pressureFunction = new BezierResourceFunction();

        while (true) {
            final double currentPressure = Math.exp(baseSlope * (currentAltitude - seaLevel));
            final double currentSlope = currentPressure * baseSlope;
            pressureFunction.addPoint(new BezierResourceFunction.BezierPoint(currentAltitude, currentPressure, currentSlope));

            if (currentAltitude < seaLevel && currentAltitude + maxStep >= seaLevel) {
                currentAltitude = seaLevel;
            } else if (currentAltitude < smoothingAltitude && currentAltitude + maxStep >= smoothingAltitude) {
                currentAltitude = smoothingAltitude;
            } else if (currentAltitude >= smoothingAltitude) {
                break;
            } else {
                currentAltitude += maxStep;
            }
        }

        final double smoothingPressure = pressureFunction.getPoints().get(pressureFunction.pointSize() - 1).value();
        final double finalSlope = -2 * smoothingPressure / (maxAltitude - smoothingAltitude);
        pressureFunction.addPoint(new BezierResourceFunction.BezierPoint(maxAltitude, 0, finalSlope));

        final Vector3f north = level.dimensionType().natural() ? DEFAULT_MAGNETIC_NORTH : new Vector3f(0, 0, 0);

        return new DimensionPhysics(level.dimension().location(),
                0,
                Optional.of(DEFAULT_UNIVERSAL_DRAG),
                Optional.of(DEFAULT_GRAVITY),
                Optional.of(DEFAULT_PRESSURE),
                Optional.of(pressureFunction),
                Optional.of(north),
                false);
    }
}
