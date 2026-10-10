package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.schematics;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.schematics.SchematicItem;
import com.simibubi.create.content.schematics.SchematicPrinter;
import com.simibubi.create.content.schematics.packet.SchematicPlacePacket;
import com.simibubi.create.foundation.utility.BlockHelper;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.create.schematics.SchematicLevelExtension;
import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.create.schematics.SchematicPrinterExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import it.unimi.dsi.fastutil.Function;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mixin into {@link com.simibubi.create.content.schematics.packet.SchematicPlacePacket} to spawn sub-levels when placing schematics.
 */

@Mixin(SchematicPlacePacket.class)
public class SchematicPlacePacketMixin {

    @Shadow @Final private ItemStack stack;

    @Inject(method = "handle", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/schematics/SchematicPrinter;loadSchematic(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Z)V", shift = At.Shift.AFTER), cancellable = true)
    private void sable$preHandle(final ServerPlayer player,
                                 final CallbackInfo ci,
                                 @Local final SchematicPrinter printer) {
        final Mirror mirror = this.stack.get(AllDataComponents.SCHEMATIC_MIRROR);

        if (mirror != null && mirror != Mirror.NONE) {
            final SchematicLevel schematicLevel = ((SchematicPrinterExtension) printer).sable$getSchematicLevel();

            if (!((SchematicLevelExtension) schematicLevel).sable$getSubLevels().isEmpty()) {
                player.sendSystemMessage(Component.translatable("schematic.sable.mirror_not_supported").withStyle(ChatFormatting.RED));
                ci.cancel();
            }
        }
    }

    @Inject(method = "handle", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/infrastructure/config/AllConfigs;server()Lcom/simibubi/create/infrastructure/config/CServer;"))
    private void sable$handle(final ServerPlayer player,
                              final CallbackInfo ci,
                              @Local final Level level,
                              @Local final SchematicPrinter printer
    ) {
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        final SubLevelPhysicsSystem physicsSystem = ((ServerSubLevelContainer) container).physicsSystem();

        final SchematicLevel schematicLevel = ((SchematicPrinterExtension) printer).sable$getSchematicLevel();
        final List<SchematicLevelExtension.SchematicSubLevel> subLevels = ((SchematicLevelExtension) schematicLevel).sable$getSubLevels();
        final BlockPos minPos = printer.getAnchor();

        final SubLevelSchematicSerializationContext context = new SubLevelSchematicSerializationContext(SubLevelSchematicSerializationContext.Type.PLACE, null);

        final StructurePlaceSettings settings = SchematicItem.getSettings(this.stack, !player.canUseGameMasterBlocks());
        final StructureTransform transform = new StructureTransform(settings.getRotationPivot(), Direction.Axis.Y,
                settings.getRotation(), settings.getMirror());
        context.setSetupTransform((block) -> transform.apply((BlockPos) block));
        context.setPlaceTransform((block) -> ((BlockPos) block).offset(minPos));

        final Map<UUID, SubLevel> spawnedSubLevels = new Object2ObjectOpenHashMap<>();
        for (final SchematicLevelExtension.SchematicSubLevel schematicSubLevel : subLevels) {
            final Pose3d pose = new Pose3d();
            pose.orientation().set(schematicSubLevel.orientation());
            pose.position().set(schematicSubLevel.position());
            final SubLevel subLevel = container.allocateNewSubLevel(pose);

            final Function<BlockPos, BlockPos> blockFunction = block -> ((BlockPos) block).offset(subLevel.getPlot().getCenterBlock());

            final SubLevelSchematicSerializationContext.SchematicMapping mapping =
                    new SubLevelSchematicSerializationContext.SchematicMapping(null, null, subLevel.getUniqueId(), blockFunction);

            context.getMappings().put(schematicSubLevel.uuid(), mapping);
            spawnedSubLevels.put(schematicSubLevel.uuid(), subLevel);
        }

        SubLevelSchematicSerializationContext.setCurrentContext(context);

        for (final SchematicLevelExtension.SchematicSubLevel schematicSubLevel : subLevels) {
            final SubLevel subLevel = spawnedSubLevels.get(schematicSubLevel.uuid());

            schematicSubLevel.position().add(minPos.getX(), minPos.getY(), minPos.getZ());
            final SchematicLevel subSchematicLevel = schematicSubLevel.level();
            final BoundingBox schematicBounds = subSchematicLevel.getBounds();

            final LevelPlot plot = subLevel.getPlot();

            final BlockPos centerBlock = plot.getCenterBlock();
            final int minChunkX = (centerBlock.getX() + schematicBounds.minX()) >> 4;
            final int minChunkZ = (centerBlock.getZ() + schematicBounds.minZ()) >> 4;

            final int maxChunkX = (centerBlock.getX() + schematicBounds.maxX()) >> 4;
            final int maxChunkZ = (centerBlock.getZ() + schematicBounds.maxZ()) >> 4;

            for (int x = minChunkX; x <= maxChunkX; x++) {
                for (int z = minChunkZ; z <= maxChunkZ; z++) {
                    plot.newEmptyChunk(new ChunkPos(x, z));
                }
            }

            BlockPos.betweenClosedStream(schematicBounds).forEach(block -> {
                final BlockState state = subSchematicLevel.getBlockState(block);
                final BlockEntity blockEntity = subSchematicLevel.getBlockEntity(block);
                final CompoundTag data = BlockHelper.prepareBlockEntityData(level, state, blockEntity);
                BlockHelper.placeSchematicBlock(level, state, centerBlock.offset(block), null, data);
            });

            subLevel.logicalPose().position()
                    .add(schematicSubLevel.position().sub(subLevel.logicalPose().transformPosition(new Vector3d(
                            centerBlock.getX(),
                            centerBlock.getY(),
                            centerBlock.getZ()
                    ))));

            final SubLevel containingSubLevel = Sable.HELPER.getContaining(level, subLevel.logicalPose().position());
            final PhysicsPipeline pipeline = physicsSystem.getPipeline();

            if (containingSubLevel != null && level instanceof final ServerLevel serverLevel) {
                SubLevelAssemblyHelper.kickFromContainingSubLevel(serverLevel, physicsSystem, pipeline, (ServerSubLevel) subLevel, containingSubLevel);
                subLevel.logicalPose().orientation().premul(containingSubLevel.logicalPose().orientation());
            }

            pipeline.teleport((ServerSubLevel) subLevel, subLevel.logicalPose().position(), subLevel.logicalPose().orientation());
            subLevel.updateLastPose();

            for (final Entity entity : subSchematicLevel.getEntityList()) {
                entity.setPos(entity.position().add(centerBlock.getX(), centerBlock.getY(), centerBlock.getZ()));
                level.addFreshEntity(entity);
            }
        }
    }

    @Inject(method = "handle", at = @At(value = "TAIL"))
    private void sable$postHandle(final ServerPlayer player, final CallbackInfo ci) {
        SubLevelSchematicSerializationContext.setCurrentContext(null);
    }

}
