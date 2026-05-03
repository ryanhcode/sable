package dev.ryanhcode.sable.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ryanhcode.sable.api.command.SableCommandHelper;
import dev.ryanhcode.sable.api.command.SubLevelArgumentType;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.ryanhcode.sable.sublevel.teleport.SubLevelDimensionTeleport;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector2i;
import org.joml.Vector3d;

import java.util.Collection;
import java.util.Objects;

public class SableSubLevelCommands {

    /**
     * Adds the following commands:
     * <ul>
     *     <li>{@code /sable name set <targets> <name>}</li>
     *     <li>{@code /sable name clear <targets>}</li>
     *     <li>{@code /sable name get <target>}</li>
     *     <li>{@code /sable teleport <targets> <destination><angle><dimension>}</li>
     *     <li>{@code /sable remove <targets>}</li>
     * </ul>
     */
    public static void register(final LiteralArgumentBuilder<CommandSourceStack> sableBuilder, final CommandBuildContext buildContext) {
        sableBuilder
                .then(Commands.literal("name")
                        .then(Commands.literal("set")
                                .then(Commands.argument("sub_level", SubLevelArgumentType.subLevels())
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(SableSubLevelCommands::executeSetSubLevelNameCommand))))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("sub_level", SubLevelArgumentType.subLevels())
                                        .executes(SableSubLevelCommands::executeClearSubLevelNameCommand)))
                        .then(Commands.literal("get")
                                .then(Commands.argument("sub_level", SubLevelArgumentType.singleSubLevel())
                                        .executes(SableSubLevelCommands::executeGetSubLevelNameCommand))))

                .then(Commands.literal("teleport")
                        .then(Commands.argument("targets", SubLevelArgumentType.subLevels())
                                .then(Commands.argument("destination", Vec3Argument.vec3(false))
                                        .executes((ctx) -> SableSubLevelCommands.executeTeleportSubLevelCommand(ctx, null, null))
                                        .then(Commands.argument("angle", RotationArgument.rotation())
                                                .executes((ctx) -> SableSubLevelCommands.executeTeleportSubLevelCommand(
                                                        ctx, RotationArgument.getRotation(ctx, "angle"), null))
                                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                                        .executes((ctx) -> SableSubLevelCommands.executeTeleportSubLevelCommand(
                                                                ctx,
                                                                RotationArgument.getRotation(ctx, "angle"),
                                                                DimensionArgument.getDimension(ctx, "dimension")))
                                                )
                                        )
                                )))

                .then(Commands.literal("remove")
                        .then(Commands.argument("targets", SubLevelArgumentType.subLevels())
                                .executes(SableSubLevelCommands::executeRemoveSubLevelCommand)));
    }

    private static int setSubLevelNames(final Collection<ServerSubLevel> subLevels, @Nullable final String name) {
        int modifiedCount = 0;
        for (final SubLevel target : subLevels) {
            if (!Objects.equals(target.getName(), name)) {
                target.setName(name);
                modifiedCount++;
            }
        }
        return modifiedCount;
    }

    private static int executeSetSubLevelNameCommand(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final Collection<ServerSubLevel> subLevels = SubLevelArgumentType.getSubLevels(ctx, "sub_level");
        final String name = StringArgumentType.getString(ctx, "name");

        if (subLevels.isEmpty()) {
            throw SableCommandHelper.ERROR_NO_SUB_LEVELS_FOUND.create();
        }

        final int modifiedCount = setSubLevelNames(subLevels, name);

        if (modifiedCount == 0) {
            throw SableCommandHelper.ERROR_NO_SUB_LEVELS_MODIFIED.create();
        }

        if (modifiedCount == 1) {
            ctx.getSource().sendSuccess(() -> Component.translatable("commands.sable.sub_level.set_name.success_singular", name), true);
        } else {
            ctx.getSource().sendSuccess(() -> Component.translatable("commands.sable.sub_level.set_name.success_multiple", modifiedCount, name), true);
        }
        return modifiedCount;
    }

    private static int executeClearSubLevelNameCommand(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final Collection<ServerSubLevel> subLevels = SubLevelArgumentType.getSubLevels(ctx, "sub_level");

        if (subLevels.isEmpty()) {
            throw SableCommandHelper.ERROR_NO_SUB_LEVELS_FOUND.create();
        }

        final int modifiedCount = setSubLevelNames(subLevels, null);

        if (modifiedCount == 0) {
            throw SableCommandHelper.ERROR_NO_SUB_LEVELS_MODIFIED.create();
        }

        if (modifiedCount == 1) {
            ctx.getSource().sendSuccess(() -> Component.translatable("commands.sable.sub_level.clear_name.success_singular"), true);
        } else {
            ctx.getSource().sendSuccess(() -> Component.translatable("commands.sable.sub_level.clear_name.success_multiple", modifiedCount), true);
        }
        return modifiedCount;
    }

    private static int executeGetSubLevelNameCommand(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final SubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(ctx, "sub_level");

        if (subLevel.getName() == null) {
            throw SableCommandHelper.ERROR_SUB_LEVEL_UNNAMED.create();
        } else {
            ctx.getSource().sendSuccess(() -> Component.translatable("commands.sable.sub_level.get_name.success", subLevel.getName()), true);
            return 1;
        }
    }
    private static int executeTeleportSubLevelCommand(
            final CommandContext<CommandSourceStack> ctx,
            final @Nullable Coordinates angle,
            final @Nullable ServerLevel destinationLevel
    ) throws CommandSyntaxException {
        final Collection<ServerSubLevel> targets = SubLevelArgumentType.getSubLevels(ctx, "targets");

        if (targets.isEmpty()) {
            throw SableCommandHelper.ERROR_NO_SUB_LEVELS_FOUND.create();
        }

        final Vector3d destination = JOMLConversion.toJOML(Vec3Argument.getVec3(ctx, "destination"));

        final Quaterniond orientation = new Quaterniond();
        final Vec2 rotation = angle != null ? angle.getRotation(ctx.getSource()) : null;
        if (angle != null) {
            orientation.rotateY(-Math.toRadians(rotation.y));
            orientation.rotateX(Math.toRadians(rotation.x));
        }

        // Cross-dimensional path: route every target through SubLevelDimensionTeleport.
        if (destinationLevel != null) {
            int successes = 0;
            int failures = 0;
            for (final ServerSubLevel target : targets) {
                final ServerSubLevel result = SubLevelDimensionTeleport.teleport(
                        target,
                        destinationLevel,
                        destination,
                        angle != null ? orientation : null
                );
                if (result != null) successes++;
                else failures++;
            }

            if (successes == 0) {
                throw new SimpleCommandExceptionType(Component.literal("All sub-level teleports failed; check log.")).create();
            }

            final int finalSuccesses = successes;
            final int finalFailures = failures;
            ctx.getSource().sendSuccess(
                    () -> Component.literal("Teleported " + finalSuccesses + " sub-level(s) to "
                            + destinationLevel.dimension().location()
                            + (finalFailures > 0 ? " (" + finalFailures + " failed)" : "")),
                    true);
            return successes;
        }

        final PhysicsPipeline pipeline = SableCommandHelper.requireSubLevelPhysicsPipeline(ctx);
        for (final ServerSubLevel target : targets) {
            pipeline.resetVelocity(target);
            pipeline.teleport(target, destination, angle != null ? orientation : target.logicalPose().orientation());
        }

        if (angle != null) {
            SableCommandHelper.sendSuccessDescribingSubLevels(
                    "commands.sable.sub_level.teleport_with_orientation.success",
                    ctx, targets,
                    destination.x, destination.y, destination.z,
                    rotation.x, rotation.y
            );
        } else {
            SableCommandHelper.sendSuccessDescribingSubLevels(
                    "commands.sable.sub_level.teleport.success",
                    ctx, targets,
                    destination.x, destination.y, destination.z
            );
        }
        return 1;
    }

    private static int executeRemoveSubLevelCommand(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final SubLevelContainer container = SableCommandHelper.requireSubLevelContainer(ctx);

        final Collection<ServerSubLevel> targets = SubLevelArgumentType.getSubLevels(ctx, "targets");

        if (targets.isEmpty()) {
            throw SableCommandHelper.ERROR_NO_SUB_LEVELS_FOUND.create();
        }

        for (final SubLevel target : targets) {
            final LevelPlot plot = target.getPlot();
            final Vector2i origin = container.getOrigin();
            container.removeSubLevel(plot.plotPos.x - origin.x, plot.plotPos.z - origin.y, SubLevelRemovalReason.REMOVED);
        }

        SableCommandHelper.sendSuccessDescribingSubLevels("commands.sable.sub_level.remove.success", ctx, targets);
        return 1;
    }

}
