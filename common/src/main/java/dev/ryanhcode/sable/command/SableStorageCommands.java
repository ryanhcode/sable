package dev.ryanhcode.sable.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.storage.region.SubLevelRegionFile;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.io.File;
import java.util.Formatter;
import java.util.Locale;

public class SableStorageCommands {

    public static void register(final LiteralArgumentBuilder<CommandSourceStack> sableBuilder, final CommandBuildContext buildContext) {
        sableBuilder.then(Commands.literal("storage")
                .then(Commands.literal("find_all_sub_levels")
                        .executes(ctx -> {
                            final ServerLevel level = ctx.getSource().getLevel();
                            final ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
                            final SubLevelHoldingChunkMap holdingChunkMap = container.getHoldingChunkMap();
                            final SubLevelStorage storage = holdingChunkMap.getStorage();
                            final CommandSourceStack source = ctx.getSource();

                            final File[] regionFiles = storage.getFolder().toFile().listFiles((dir, name) -> name.endsWith(SubLevelRegionFile.FILE_EXTENSION));

                            if (regionFiles != null) {
                                for (final File regionFile : regionFiles) {
                                    final String fileName = regionFile.getName();
                                    final String withoutExtension = fileName.substring(0, fileName.length() - SubLevelRegionFile.FILE_EXTENSION.length());
                                    final String[] parts = withoutExtension.split("\\.");
                                    if (parts.length != 3) continue;

                                    final int regionX, regionZ;
                                    try {
                                        regionX = Integer.parseInt(parts[1]);
                                        regionZ = Integer.parseInt(parts[2]);
                                    } catch (final NumberFormatException e) {
                                        continue;
                                    }

                                    for (int localX = 0; localX < SubLevelRegionFile.SIDE_LENGTH; localX++) {
                                        for (int localZ = 0; localZ < SubLevelRegionFile.SIDE_LENGTH; localZ++) {
                                            final ChunkPos chunkPos = new ChunkPos(
                                                    regionX * SubLevelRegionFile.SIDE_LENGTH + localX,
                                                    regionZ * SubLevelRegionFile.SIDE_LENGTH + localZ
                                            );

                                            final SubLevelHoldingChunk holdingChunk = storage.attemptLoadHoldingChunk(chunkPos);
                                            if (holdingChunk == null) continue;

                                            for (final SavedSubLevelPointer pointer : holdingChunk.getSubLevelPointers()) {
                                                final SubLevelData data = storage.attemptLoadSubLevel(chunkPos, pointer);
                                                logFoundSubLevel(pointer, data, chunkPos, source, level);
                                            }
                                        }
                                    }
                                }
                            }
                            return 1;
                        }))
                .then(Commands.literal("find")
                        .then(Commands.argument("name", StringArgumentType.string())
                        .executes(ctx -> {
                            final ServerLevel level = ctx.getSource().getLevel();
                            final ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
                            final SubLevelHoldingChunkMap holdingChunkMap = container.getHoldingChunkMap();
                            final SubLevelStorage storage = holdingChunkMap.getStorage();
                            final CommandSourceStack source = ctx.getSource();
                            final String nameArgument = StringArgumentType.getString(ctx, "name");

                            final File[] regionFiles = storage.getFolder().toFile().listFiles((dir, name) -> name.endsWith(SubLevelRegionFile.FILE_EXTENSION));

                            if (regionFiles != null) {
                                for (final File regionFile : regionFiles) {
                                    final String fileName = regionFile.getName();
                                    final String withoutExtension = fileName.substring(0, fileName.length() - SubLevelRegionFile.FILE_EXTENSION.length());
                                    final String[] parts = withoutExtension.split("\\.");
                                    if (parts.length != 3) continue;

                                    final int regionX, regionZ;
                                    try {
                                        regionX = Integer.parseInt(parts[1]);
                                        regionZ = Integer.parseInt(parts[2]);
                                    } catch (final NumberFormatException e) {
                                        continue;
                                    }

                                    for (int localX = 0; localX < SubLevelRegionFile.SIDE_LENGTH; localX++) {
                                        for (int localZ = 0; localZ < SubLevelRegionFile.SIDE_LENGTH; localZ++) {
                                            final ChunkPos chunkPos = new ChunkPos(
                                                    regionX * SubLevelRegionFile.SIDE_LENGTH + localX,
                                                    regionZ * SubLevelRegionFile.SIDE_LENGTH + localZ
                                            );

                                            final SubLevelHoldingChunk holdingChunk = storage.attemptLoadHoldingChunk(chunkPos);
                                            if (holdingChunk == null) continue;

                                            for (final SavedSubLevelPointer pointer : holdingChunk.getSubLevelPointers()) {
                                                final SubLevelData data = storage.attemptLoadSubLevel(chunkPos, pointer);

                                                final String name = data.fullTag().contains("display_name")
                                                        ? data.fullTag().getString("display_name")
                                                        : data.uuid().toString();
                                                if (name != null && name.equals(nameArgument)) {
                                                    logFoundSubLevel(pointer, data, chunkPos, source, level);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            return 1;
                        })))

        );
    }

    private static void logFoundSubLevel(final SavedSubLevelPointer pointer, final SubLevelData data, final ChunkPos chunkPos, final CommandSourceStack source, final ServerLevel level) {
        if (data == null) return;

        final String name = data.fullTag().contains("display_name")
                ? data.fullTag().getString("display_name")
                : data.uuid().toString();
        final GlobalSavedSubLevelPointer globalPointer = new GlobalSavedSubLevelPointer(chunkPos, pointer.storageIndex(), pointer.subLevelIndex());

        final Pose3d pose = data.pose();

        source.sendSuccess(() -> {
            final Vector3dc pos =  pose.position();
            final MutableComponent component = Component.translatable("commands.sable.info.name", Component.literal(name));
            final ResourceLocation dimension = level.dimension().location();
            final Component fileId = Component.translatable("commands.sable.info.name.tooltip", globalPointer.toString());
            component.setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, new Formatter().format(Locale.ROOT, "/execute in %s run tp @s %.2f %.2f %.2f", dimension, pos.x(), pos.y(), pos.z()).toString()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, fileId))
                    .withColor(ChatFormatting.GRAY));
            return component;
        }, false);

        source.sendSuccess(() -> {
            final Vector3dc pos = pose.position();
            return Component.translatable("commands.sable.info.position", pos.x(), pos.y(), pos.z());
        }, false);

        source.sendSuccess(() -> {
            final Vector3d size = data.bounds().size();
            return Component.translatable("commands.sable.info.world_bounds", size.x, size.y, size.z);
        }, false);
    }
}
