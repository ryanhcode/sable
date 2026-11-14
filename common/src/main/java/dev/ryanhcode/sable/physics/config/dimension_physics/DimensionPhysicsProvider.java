package dev.ryanhcode.sable.physics.config.dimension_physics;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public abstract class DimensionPhysicsProvider implements DataProvider {
    protected final PackOutput.PathProvider pathProvider;
    private final CompletableFuture<HolderLookup.Provider> registries;
    protected final String modId;
    private final Map<ResourceLocation, DimensionPhysics> definitions = new HashMap<>();

    public DimensionPhysicsProvider(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> registries, String modId) {
        this.pathProvider = packOutput.createPathProvider(PackOutput.Target.DATA_PACK, "dimension_physics");
        this.registries = registries;
        this.modId = modId;
    }

    protected abstract void addProperties(HolderLookup.Provider holderLookup);

    public final CompletableFuture<?> run(CachedOutput output) {
        return this.registries.thenCompose((provider) -> {
            final Set<CompletableFuture<?>> list = new HashSet<>();
            this.addProperties(provider);
            this.definitions.forEach((id, definition) -> {
                list.add(DataProvider.saveStable(output, provider, DimensionPhysics.CODEC, definition, this.pathProvider.json(id)));
            });
            return CompletableFuture.allOf(list.toArray(CompletableFuture[]::new));
        });
    }

    public final DimensionPhysics define(final ResourceKey<Level> key, final Function<Builder, Builder> builderFunction) {
        return this.definitions.computeIfAbsent(key.location(), (k) -> builderFunction.apply(new Builder(k)).build());
    }

    public final DimensionPhysics define(final ResourceLocation id, final Function<Builder, Builder> builderFunction) {
        return this.definitions.computeIfAbsent(id, (k) -> builderFunction.apply(new Builder(k)).build());
    }

    @Override
    public @NotNull String getName() {
        return "Dimension Physics Properties for " + this.modId;
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static class Builder {
        final ResourceLocation dimension;
        int priority = 1000;
        Optional<Float> universalDrag = Optional.empty();
        Optional<Vector3f> baseGravity = Optional.empty();
        Optional<Double> basePressure = Optional.empty();
        Optional<BezierResourceFunction> pressureFunction = Optional.empty();
        Optional<Vector3f> magneticNorth = Optional.empty();
        boolean ignoreChunks = false;

        public Builder(ResourceLocation dimension) {
            this.dimension = dimension;
        }

        /**
         * @param priority the priority of the file, higher values will override
         * @return this
         */
        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        /**
         * @param drag universal drag of the dimension
         * @return this
         */
        public Builder drag(float drag) {
            this.universalDrag = Optional.of(drag);
            return this;
        }

        /**
         * @param gravity base gravity of the dimension
         * @return this
         */
        public Builder gravity(Vector3f gravity) {
            this.baseGravity = Optional.of(gravity);
            return this;
        }

        /**
         * @param pressure base pressure of the dimension
         * @return this
         */
        public Builder pressure(double pressure) {
            this.basePressure = Optional.of(pressure);
            return this;
        }

        /**
         * @param builder pressure function builder
         * @return this
         * @see BezierResourceFunction.Builder
         */
        public Builder pressureFunc(Function<BezierResourceFunction.Builder, BezierResourceFunction.Builder> builder) {
            this.pressureFunction = Optional.of(builder.apply(new BezierResourceFunction.Builder()).build());
            return this;
        }

        /**
         * @param north magnetic north of the dimension
         * @return this
         */
        public Builder north(Vector3f north) {
            this.magneticNorth = Optional.of(north);
            return this;
        }

        public Builder ignoreChunks() {
            this.ignoreChunks = true;
            return this;
        }

        public DimensionPhysics build() {
            return new DimensionPhysics(this.dimension, this.priority, this.universalDrag, this.baseGravity, this.basePressure, this.pressureFunction, this.magneticNorth, this.ignoreChunks);
        }
    }
}
