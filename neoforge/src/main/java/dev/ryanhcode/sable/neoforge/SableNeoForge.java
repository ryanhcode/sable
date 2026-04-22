package dev.ryanhcode.sable.neoforge;

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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.CrashReportCallables;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(Sable.MOD_ID)
public final class SableNeoForge {
    public SableNeoForge(final ModContainer modContainer, final IEventBus modBus) {
        Sable.init();

        final IEventBus neoBus = NeoForge.EVENT_BUS;
        neoBus.addListener(this::registerCommand);
        neoBus.addListener(this::registerReloadListeners);
        modBus.addListener(this::serverSetup);
        neoBus.addListener(this::syncDataPack);

        SubLevelSelectorModifiers.registerModifiers();

        final DeferredRegister<Attribute> attributes = DeferredRegister.create(BuiltInRegistries.ATTRIBUTE, Sable.MOD_ID);
        SableAttributes.PUNCH_STRENGTH = attributes.register(SableAttributes.PUNCH_STRENGTH_NAME, () -> SableAttributes.PUNCH_STRENGTH_ATTRIBUTE);
        SableAttributes.PUNCH_COOLDOWN = attributes.register(SableAttributes.PUNCH_COOLDOWN_NAME, () -> SableAttributes.PUNCH_COOLDOWN_ATTRIBUTE);
        attributes.register(modBus);

        final DeferredRegister.Items items = DeferredRegister.createItems(Sable.MOD_ID);
        SableItems.SUBLEVEL_ASSEMBLER = items.register(SableItems.ASSEMBLER_NAME, SubLevelAssemblerItem::new);
        SableItems.SUBLEVEL_AREA_ASSEMBLER = items.register(SableItems.AREA_ASSEMBLER_NAME, SubLevelAreaAssemblerItem::new);
        SableItems.SUBLEVEL_SINGLE_BLOCK_AREA_ASSEMBLER = items.register(SableItems.SINGLE_BLOCK_AREA_ASSEMBLER_NAME, SingleBlockSubLevelAreaAssemblerItem::new);
        SableItems.SUBLEVEL_REMOVER =  items.register(SableItems.REMOVER_NAME, SubLevelRemoverItem::new);
        items.register(modBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, SableConfig.SPEC);

        modBus.addListener(this::addOperatorItems);

        CrashReportCallables.registerHeader(Sable::getCrashHeader);
    }


    public void addOperatorItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.OP_BLOCKS) {
            event.accept(SableItems.SUBLEVEL_ASSEMBLER.value());
            event.accept(SableItems.SUBLEVEL_AREA_ASSEMBLER.value());
            event.accept(SableItems.SUBLEVEL_SINGLE_BLOCK_AREA_ASSEMBLER.value());
            event.accept(SableItems.SUBLEVEL_REMOVER.value());
        }
    }

    public void registerReloadListeners(final AddReloadListenerEvent event) {
        event.addListener(PhysicsBlockPropertiesDefinitionLoader.INSTANCE);
        event.addListener(DimensionPhysicsData.ReloadListener.INSTANCE);
        event.addListener(FloatingBlockMaterialDataHandler.ReloadListener.INSTANCE);
    }

    private void serverSetup(final FMLCommonSetupEvent event) {
        SableAttributes.register();
    }

    private void registerCommand(final RegisterCommandsEvent event) {
        SableCommand.register(event.getDispatcher(), event.getBuildContext());
    }

    private void syncDataPack(final OnDatapackSyncEvent event) {
        SableCommonEvents.syncDataPacket(packet -> event.getRelevantPlayers().forEach(player -> player.connection.send(packet)));
    }
}
