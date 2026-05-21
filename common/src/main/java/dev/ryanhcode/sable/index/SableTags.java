package dev.ryanhcode.sable.index;

import dev.ryanhcode.sable.Sable;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class SableTags {
    public static final TagKey<EntityType<?>> RETAIN_IN_SUB_LEVEL = TagKey.create(
            Registries.ENTITY_TYPE,
            Sable.sablePath("retain_in_sub_level")
    );
    public static final TagKey<EntityType<?>> DESTROY_WITH_SUB_LEVEL = TagKey.create(
            Registries.ENTITY_TYPE,
            Sable.sablePath("destroy_with_sub_level")
    );
    public static final TagKey<EntityType<?>> DESTROY_WHEN_LEAVING_PLOT = TagKey.create(
            Registries.ENTITY_TYPE,
            Sable.sablePath("destroy_when_leaving_plot")
    );

    public static final TagKey<Block> ALWAYS_CHUNK_RENDERING = TagKey.create(
            Registries.BLOCK,
            Sable.sablePath("always_chunk_rendering")
    );

    public static final TagKey<Block> BOUNCY = TagKey.create(
            Registries.BLOCK,
            Sable.sablePath("bouncy")
    );

    public static final TagKey<Item> PADDLES = TagKey.create(
            Registries.ITEM,
            Sable.sablePath("paddles")
    );

    public static final TagKey<Block> SILENT_ASSEMBLY_REMOVAL = TagKey.create(
            Registries.BLOCK,
            Sable.sablePath("silent_assembly_removal")
    );

    public static void register() {
        // no-op
    }
}
