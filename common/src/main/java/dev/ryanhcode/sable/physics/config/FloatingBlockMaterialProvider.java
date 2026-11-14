package dev.ryanhcode.sable.physics.config;

import dev.ryanhcode.sable.physics.floating_block.FloatingBlockMaterial;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class FloatingBlockMaterialProvider implements DataProvider {
    protected final PackOutput.PathProvider pathProvider;
    private final CompletableFuture<HolderLookup.Provider> registries;
    protected final String modId;
    private final Map<ResourceLocation, FloatingBlockMaterial> definitions = new HashMap<>();

    public FloatingBlockMaterialProvider(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> registries, String modId) {
        this.pathProvider = packOutput.createPathProvider(PackOutput.Target.DATA_PACK, "floating_block_material");
        this.registries = registries;
        this.modId = modId;
    }

    protected abstract void addProperties(HolderLookup.Provider holderLookup);

    public final CompletableFuture<?> run(CachedOutput output) {
        return this.registries.thenCompose((provider) -> {
            final Set<CompletableFuture<?>> list = new HashSet<>();
            this.addProperties(provider);
            this.definitions.forEach((id, definition) -> {
                list.add(DataProvider.saveStable(output, provider, FloatingBlockMaterial.CODEC, definition, this.pathProvider.json(id)));
            });
            return CompletableFuture.allOf(list.toArray(CompletableFuture[]::new));
        });
    }

    public final FloatingBlockMaterial define(final Supplier<Block> block, double liftStrength, final Function<Builder, Builder> builderFunction) {
        return this.define(BuiltInRegistries.BLOCK.getKey(block.get()), liftStrength, builderFunction);
    }

    public final FloatingBlockMaterial define(final ResourceLocation id, double liftStrength, final Function<Builder, Builder> builderFunction) {
        return this.definitions.computeIfAbsent(id, (k) -> builderFunction.apply(new Builder(liftStrength)).build());
    }

    @Override
    public @NotNull String getName() {
        return "Floating Block Material Properties for " + this.modId;
    }

    public static class Builder {
        boolean preventSelfLift = false;
        boolean scaleWithPressure = false;
        boolean scaleWithGravity = false;
        final double liftStrength;
        double transitionSpeed = 0.0d;
        double slowVerticalFriction = 0.0d;
        double fastVerticalFriction = 0.0d;
        double slowHorizontalFriction = 0.0d;
        double fastHorizontalFriction = 0.0d;

        public Builder(double liftStrength) {
            this.liftStrength = liftStrength;
        }

        public Builder preventSelfLift() {
            this.preventSelfLift = true;
            return this;
        }

        public Builder scaleWithPressure() {
            this.scaleWithPressure = true;
            return this;
        }

        public Builder scaleWithGravity() {
            this.scaleWithGravity = true;
            return this;
        }

        public Builder transitionSpeed(double transitionSpeed) {
            this.transitionSpeed = transitionSpeed;
            return this;
        }

        public Builder slowVerticalFriction(double slowVerticalFriction) {
            this.slowVerticalFriction = slowVerticalFriction;
            return this;
        }

        public Builder fastVerticalFriction(double fastVerticalFriction) {
            this.fastVerticalFriction = fastVerticalFriction;
            return this;
        }

        public Builder slowHorizontalFriction(double slowHorizontalFriction) {
            this.slowHorizontalFriction = slowHorizontalFriction;
            return this;
        }

        public Builder fastHorizontalFriction(double fastHorizontalFriction) {
            this.fastHorizontalFriction = fastHorizontalFriction;
            return this;
        }

        public FloatingBlockMaterial build() {
            return new FloatingBlockMaterial(this.preventSelfLift, this.scaleWithPressure, this.scaleWithGravity, this.liftStrength, this.transitionSpeed, this.slowVerticalFriction, this.fastVerticalFriction, this.slowHorizontalFriction, this.fastHorizontalFriction);
        }
    }
}
