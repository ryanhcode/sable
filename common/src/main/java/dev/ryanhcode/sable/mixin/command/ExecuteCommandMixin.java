package dev.ryanhcode.sable.mixin.command;

import com.google.common.collect.Lists;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.ryanhcode.sable.api.command.SableCommandHelper;
import dev.ryanhcode.sable.api.command.SubLevelArgumentType;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.commands.ExecuteCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(ExecuteCommand.class)
public class ExecuteCommandMixin {

    /**
     * TODO: Better injection target here would be nice. And to split these out of the mixins.
     */
    @SuppressWarnings("unchecked")
    @ModifyExpressionValue(method = "register", at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/builder/LiteralArgumentBuilder;then(Lcom/mojang/brigadier/builder/ArgumentBuilder;)Lcom/mojang/brigadier/builder/ArgumentBuilder;", ordinal = 31, remap = false))
    private static ArgumentBuilder sable$then(final ArgumentBuilder original, @Local final LiteralCommandNode<CommandSourceStack> literalCommandNode) {
        return original
                .then(
                        Commands.literal("in_sub_level")
                                .then(
                                        Commands.argument("sub_levels", SubLevelArgumentType.subLevels()).fork(literalCommandNode, commandContext -> {
                                            final List<CommandSourceStack> list = Lists.newArrayList();

                                            final ServerSubLevelContainer container = SubLevelContainer.getContainer(commandContext.getSource().getLevel());
                                            for (final SubLevel subLevel : SubLevelArgumentType.getSubLevels(commandContext, "sub_levels")) {
                                                final Pose3d pose = subLevel.logicalPose();
                                                final Vec3 localPos = pose.transformPositionInverse(commandContext.getSource().getPosition());

                                                if (container.getPlot(new ChunkPos(BlockPos.containing(localPos))) != subLevel.getPlot()) {
                                                    throw SableCommandHelper.ERROR_NOT_INSIDE_SUB_LEVEL.create();
                                                }

                                                list.add(commandContext.getSource().withLevel((ServerLevel) subLevel.getLevel()).withPosition(localPos).withRotation(new Vec2(0, 0)));
                                            }

                                            return list;
                                        })))
                .then(
                        Commands.literal("out_sub_level")
                                .then(
                                        Commands.argument("sub_levels", SubLevelArgumentType.subLevels()).fork(literalCommandNode, commandContext -> {
                                            final List<CommandSourceStack> list = Lists.newArrayList();

                                            final ServerSubLevelContainer container = SubLevelContainer.getContainer(commandContext.getSource().getLevel());
                                            for (final SubLevel subLevel : SubLevelArgumentType.getSubLevels(commandContext, "sub_levels")) {
                                                final Pose3d pose = subLevel.logicalPose();
                                                final Vec3 sourcePosition = commandContext.getSource().getPosition();
                                                final Vec3 globalPos = pose.transformPosition(sourcePosition);

                                                if (container.getPlot(new ChunkPos(BlockPos.containing(sourcePosition))) != subLevel.getPlot()) {
                                                    throw SableCommandHelper.ERROR_NOT_INSIDE_SUB_LEVEL.create();
                                                }

                                                list.add(commandContext.getSource().withLevel((ServerLevel) subLevel.getLevel()).withPosition(globalPos).withRotation(new Vec2(0, 0)));
                                            }

                                            return list;
                                        })))
                .then(
                        Commands.literal("centered_in_sub_level")
                                .then(
                                        Commands.argument("sub_levels", SubLevelArgumentType.subLevels()).fork(literalCommandNode, commandContext -> {
                                            final List<CommandSourceStack> list = Lists.newArrayList();

                                            for (final SubLevel subLevel : SubLevelArgumentType.getSubLevels(commandContext, "sub_levels")) {
                                                final LevelPlot plot = subLevel.getPlot();
                                                final Vec3 center = plot.getCenterBlock().getCenter();
                                                list.add(commandContext.getSource().withLevel((ServerLevel) subLevel.getLevel()).withPosition(center).withRotation(new Vec2(0, 0)));
                                            }

                                            return list;
                                        })));
    }
}
