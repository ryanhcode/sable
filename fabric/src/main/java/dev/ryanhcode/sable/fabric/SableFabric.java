package dev.ryanhcode.sable.fabric;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableCommonEvents;
import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.command.SableCommand;
import dev.ryanhcode.sable.command.argument.SubLevelSelectorModifiers;
import dev.ryanhcode.sable.index.SableAttributes;
import dev.ryanhcode.sable.index.SableItems;
import dev.ryanhcode.sable.item.SingleBlockSubLevelAreaAssemblerItem;
import dev.ryanhcode.sable.item.SubLevelAreaAssemblerItem;
import dev.ryanhcode.sable.item.SubLevelAssemblerItem;
import dev.ryanhcode.sable.item.SubLevelRemoverItem;
import dev.ryanhcode.sable.physics.config.FloatingBlockMaterialDataHandler;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertiesDefinitionLoader;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.fml.config.ModConfig;

public final class SableFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        Sable.init();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            SableCommand.register(dispatcher, registryAccess);
        });

        SubLevelSelectorModifiers.registerModifiers();

        SableAttributes.PUNCH_STRENGTH = Registry.registerForHolder(BuiltInRegistries.ATTRIBUTE, Sable.sablePath(SableAttributes.PUNCH_STRENGTH_NAME), SableAttributes.PUNCH_STRENGTH_ATTRIBUTE);
        SableAttributes.PUNCH_COOLDOWN = Registry.registerForHolder(BuiltInRegistries.ATTRIBUTE, Sable.sablePath(SableAttributes.PUNCH_COOLDOWN_NAME), SableAttributes.PUNCH_COOLDOWN_ATTRIBUTE);
        SableAttributes.register();

        SableItems.SUBLEVEL_ASSEMBLER = Registry.registerForHolder(BuiltInRegistries.ITEM, Sable.sablePath(SableItems.ASSEMBLER_NAME), new SubLevelAssemblerItem());
        SableItems.SUBLEVEL_AREA_ASSEMBLER = Registry.registerForHolder(BuiltInRegistries.ITEM, Sable.sablePath(SableItems.AREA_ASSEMBLER_NAME), new SubLevelAreaAssemblerItem());
        SableItems.SUBLEVEL_SINGLE_BLOCK_AREA_ASSEMBLER = Registry.registerForHolder(BuiltInRegistries.ITEM, Sable.sablePath(SableItems.SINGLE_BLOCK_AREA_ASSEMBLER_NAME), new SingleBlockSubLevelAreaAssemblerItem());
        SableItems.SUBLEVEL_REMOVER = Registry.registerForHolder(BuiltInRegistries.ITEM, Sable.sablePath(SableItems.REMOVER_NAME), new SubLevelRemoverItem());

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.OP_BLOCKS).register(tabEntries -> {
            tabEntries.accept(SableItems.SUBLEVEL_ASSEMBLER.value());
            tabEntries.accept(SableItems.SUBLEVEL_AREA_ASSEMBLER.value());
            tabEntries.accept(SableItems.SUBLEVEL_SINGLE_BLOCK_AREA_ASSEMBLER.value());
            tabEntries.accept(SableItems.SUBLEVEL_REMOVER.value());
        });

        final ResourceManagerHelper helper = ResourceManagerHelper.get(PackType.SERVER_DATA);
        helper.registerReloadListener(new ResourceReloadDelegate(PhysicsBlockPropertiesDefinitionLoader.ID, PhysicsBlockPropertiesDefinitionLoader.INSTANCE));
        helper.registerReloadListener(new ResourceReloadDelegate(DimensionPhysicsData.ReloadListener.ID, DimensionPhysicsData.ReloadListener.INSTANCE));
        helper.registerReloadListener(new ResourceReloadDelegate(FloatingBlockMaterialDataHandler.ReloadListener.ID, FloatingBlockMaterialDataHandler.ReloadListener.INSTANCE));

        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> SableCommonEvents.syncDataPacket(packet -> player.connection.send(packet)));

        NeoForgeConfigRegistry.INSTANCE.register(Sable.MOD_ID, ModConfig.Type.COMMON, SableConfig.SPEC);
    }
}
