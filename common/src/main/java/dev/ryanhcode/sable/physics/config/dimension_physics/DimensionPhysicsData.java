package dev.ryanhcode.sable.physics.config.dimension_physics;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundDimensionPhysicsPacket;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3fc;

import java.util.HashMap;
import java.util.Map;

public class DimensionPhysicsData {
    static final Map<ResourceKey<Level>, DimensionPhysics> DIMENSION_PHYSICS_DATA = new HashMap<>();
    static final Map<ResourceKey<Level>, DimensionPhysics> DEFAULT_DIMENSION_PHYSICS_DATA = new HashMap<>();

    public static DimensionPhysics of(final Level level) {
        final DimensionPhysics dimensionPhysics = DIMENSION_PHYSICS_DATA.get(level.dimension());
        if (dimensionPhysics == null) {
            return getDefault(level);
        }

        return dimensionPhysics;
    }

    public static DimensionPhysics getDefault(final Level level) {
        DimensionPhysics dimensionPhysics = DEFAULT_DIMENSION_PHYSICS_DATA.get(level.dimension());

        if (dimensionPhysics == null) {
            dimensionPhysics = DimensionPhysics.createDefault(level);
            DEFAULT_DIMENSION_PHYSICS_DATA.put(level.dimension(), dimensionPhysics);
        }

        return dimensionPhysics;
    }

    public static Vector3d getGravity(final Level level) {
        return getGravity(level, JOMLConversion.ZERO);
    }

    public static Vector3d getGravity(final Level level, final Vector3dc pos) {
        return getGravity(level, pos, new Vector3d());
    }

    public static Vector3d getGravity(final Level level, final Vector3dc pos, final Vector3d dest) {
        final DimensionPhysics physics = DimensionPhysicsData.of(level);
        final DimensionPhysics defaultPhysics = getDefault(level);

        final Vector3fc gravity = physics.baseGravity().orElseGet(defaultPhysics.baseGravity()::orElseThrow);
        return dest.set(gravity);
    }

    public static double getAirPressure(final Level level, final Vector3dc pos) {
        final DimensionPhysics physics = DimensionPhysicsData.of(level);
        final DimensionPhysics defaultPhysics = getDefault(level);

        final double pressure = physics.basePressure().orElseGet(defaultPhysics.basePressure()::orElseThrow);
        final BezierResourceFunction curve = physics.pressureFunction().orElseGet(defaultPhysics.pressureFunction()::orElseThrow);

        return pressure * curve.evaluateFunction(pos.y());
    }

    public static Vector3fc getMagneticNorth(final Level level) {
        final DimensionPhysics physics = DimensionPhysicsData.of(level);
        final DimensionPhysics defaultPhysics = getDefault(level);

        return physics.magneticNorth().orElseGet(defaultPhysics.magneticNorth()::orElseThrow);
    }

    public static double getUniversalDrag(final ServerLevel level) {
        final DimensionPhysics physics = DimensionPhysicsData.of(level);
        final DimensionPhysics defaultPhysics = getDefault(level);

        return physics.universalDrag().orElseGet(defaultPhysics.universalDrag()::orElseThrow);
    }

    public static void addPhysicsWithPriority(final ResourceKey<Level> key, final DimensionPhysics newProperties) {
        final DimensionPhysics existing = DIMENSION_PHYSICS_DATA.get(key);

        if (existing != null) {
            if (newProperties.priority() > existing.priority()) {
                DIMENSION_PHYSICS_DATA.put(key, newProperties);
            }
        } else {
            DIMENSION_PHYSICS_DATA.put(key, newProperties);
        }
    }

    public static void putPhysics(final ResourceKey<Level> key, final DimensionPhysics newProperties) {
        DIMENSION_PHYSICS_DATA.put(key, newProperties);
    }

    public static void clearPhysics() {
        DIMENSION_PHYSICS_DATA.clear();
    }

    public static ClientboundDimensionPhysicsPacket compilePacket() {
        return new ClientboundDimensionPhysicsPacket(DIMENSION_PHYSICS_DATA.values().stream().toList());
    }

    public static class ReloadListener extends SimpleJsonResourceReloadListener {

        private static final Gson GSON = new Gson();
        public static final ReloadListener INSTANCE = new ReloadListener();

        public static final String NAME = "dimension_physics";
        public static final ResourceLocation ID = Sable.sablePath(NAME);

        public ReloadListener() {
            super(ReloadListener.GSON, NAME);
        }

        @Override
        protected void apply(final Map<ResourceLocation, JsonElement> map, final ResourceManager resourceManager, final ProfilerFiller profiler) {
            clearPhysics();

            for (final Map.Entry<ResourceLocation, JsonElement> entry : map.entrySet()) {
                try {
                    final DataResult<DimensionPhysics> dataResult = DimensionPhysics.CODEC.parse(JsonOps.INSTANCE, entry.getValue());

                    if (dataResult.error().isPresent()) {
                        Sable.LOGGER.error(String.valueOf(dataResult.error().get()));
                    }

                    final DimensionPhysics dimensionPhysics = dataResult.getOrThrow();
                    final ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionPhysics.dimension());

                    addPhysicsWithPriority(dimension, dimensionPhysics);
                } catch (final Exception e) {
                    Sable.LOGGER.error("Error while loading dimension data \"{}\" : {} ", entry.getKey(), e.getMessage());
                }
            }
        }
    }
}
