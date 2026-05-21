package dev.ryanhcode.sable.api.sublevel.ticket;

import com.mojang.serialization.Codec;
import dev.ryanhcode.sable.Sable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public record SubLevelLoadingTicketType<T>(ResourceLocation name, Codec<T> codec) {
    private static final Map<ResourceLocation, SubLevelLoadingTicketType<?>> REGISTRY = new HashMap<>();

    public static final SubLevelLoadingTicketType<Unit> COMMAND_FORCED = create(Sable.sablePath("command_forced"), Unit.CODEC);

    public static <T> SubLevelLoadingTicketType<T> create(final ResourceLocation name, final Codec<T> codec) {
        final SubLevelLoadingTicketType<T> type = new SubLevelLoadingTicketType<>(name, codec);
        REGISTRY.put(name, type);
        return type;
    }

    public static @Nullable SubLevelLoadingTicketType<?> byName(final ResourceLocation name) {
        return REGISTRY.get(name);
    }

    @Override
    public @NotNull String toString() {
        return "SubLevelTicketType{" + "name=" + this.name + "}";
    }
}
