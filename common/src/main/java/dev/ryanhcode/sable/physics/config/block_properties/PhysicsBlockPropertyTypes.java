package dev.ryanhcode.sable.physics.config.block_properties;

import com.mojang.serialization.Codec;
import dev.ryanhcode.sable.Sable;
import foundry.veil.platform.registry.RegistrationProvider;
import foundry.veil.platform.registry.RegistryObject;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * All default physics block properties
 */
public class PhysicsBlockPropertyTypes {
    public static final ResourceKey<Registry<PhysicsBlockPropertyType<?>>> REGISTRY_KEY = ResourceKey.createRegistryKey(Sable.sablePath("physics_block_properties"));
    private static final RegistrationProvider<PhysicsBlockPropertyType<?>> VANILLA_PROVIDER;
    private static final Registry<PhysicsBlockPropertyType<?>> REGISTRY;

    static {
        VANILLA_PROVIDER = RegistrationProvider.get(REGISTRY_KEY, Sable.MOD_ID);
        REGISTRY = VANILLA_PROVIDER.asVanillaRegistry();
    }

    /**
     * The mass of a block in [kpg]
     */
    public static final RegistryObject<PhysicsBlockPropertyType<Double>> MASS = register(Sable.sablePath("mass"), Codec.DOUBLE, 1.0);
    /**
     * The optional 3d vector representing the principal inertia of the block
     */
    public static final RegistryObject<PhysicsBlockPropertyType<Vec3>> INERTIA = register(Sable.sablePath("inertia"), Vec3.CODEC, null);
    /**
     * The volume of a block, used for buoyancy
     */
    public static final RegistryObject<PhysicsBlockPropertyType<Double>> VOLUME = register(Sable.sablePath("volume"), Codec.DOUBLE, 1.0);
    /**
     * The restitution of a block
     */
    public static final RegistryObject<PhysicsBlockPropertyType<Double>> RESTITUTION = register(Sable.sablePath("restitution"), Codec.DOUBLE, 0.0);
    /**
     * The friction multiplier of a block
     */
    public static final RegistryObject<PhysicsBlockPropertyType<Double>> FRICTION = register(Sable.sablePath("friction"), Codec.DOUBLE, 1.0);
    /**
     * If this block is fragile
     */
    public static final RegistryObject<PhysicsBlockPropertyType<Boolean>> FRAGILE = register(Sable.sablePath("fragile"), Codec.BOOL, false);
    /**
     * The floating material {@link ResourceLocation} this block should have
     */
    public static final RegistryObject<PhysicsBlockPropertyType<ResourceLocation>> FLOATING_MATERIAL = register(Sable.sablePath("floating_material"), ResourceLocation.CODEC, null);
    /**
     * The scale / multiplier of the effects caused by the floating material for this block
     */
    public static final RegistryObject<PhysicsBlockPropertyType<Double>> FLOATING_SCALE = register(Sable.sablePath("floating_scale"), Codec.DOUBLE, 1.0);
    /**
     * The buoyancy of a fluid
     */
    public static final RegistryObject<PhysicsBlockPropertyType<Double>> BUOYANCY = register(Sable.sablePath("buoyancy"), Codec.DOUBLE, 0.0);
    /**
     * The viscosity of a fluid
     */
    public static final RegistryObject<PhysicsBlockPropertyType<Double>> VISCOSITY = register(Sable.sablePath("viscosity"), Codec.DOUBLE, 0.0);

    public static void register() {
        // no-op
    }
    /**
     * Registers a physics block property.
     *
     * @param id    The id of the property
     * @param codec The codec defining serialization/deserialization for the property
     * @return The registered property
     */
    private static <T> RegistryObject<PhysicsBlockPropertyType<T>> register(final ResourceLocation id, final Codec<T> codec, final T defaultValue) {
        // Throw if the property is already registered
        if (REGISTRY.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate physics block property: %s".formatted(id));
        }

        return VANILLA_PROVIDER.register(id, () -> new PhysicsBlockPropertyType<>(REGISTRY.size(), codec, defaultValue));
    }

    /**
     * The count of registered properties
     */
    public static int count() {
        return REGISTRY.size();
    }

    /**
     * Gets the codec for a property.
     *
     * @param id The id of the property
     * @return The codec for the property
     */
    public static Codec<Object> getPropertyCodec(final ResourceLocation id) {
        final PhysicsBlockPropertyType<?> property = REGISTRY.get(id);

        if (property != null) {
            //noinspection unchecked
            return (Codec<Object>) property.codec;
        }

        throw new IllegalArgumentException("Unknown physics block property: %s".formatted(id));
    }

    /**
     * Gets a property type
     *
     * @param id The id of the property
     * @return The property type
     */
    public static PhysicsBlockPropertyType<?> getPropertyType(final ResourceLocation id) {
        final PhysicsBlockPropertyType<?> property = REGISTRY.get(id);

        if (property != null) {
            return property;
        }

        throw new IllegalArgumentException("Unknown physics block property: %s".formatted(id));
    }

    public record PhysicsBlockPropertyType<T>(int id, Codec<T> codec, T defaultValue) {
    }

}
