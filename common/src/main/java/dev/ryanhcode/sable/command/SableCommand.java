package dev.ryanhcode.sable.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ryanhcode.sable.api.command.SableCommandHelper;
import dev.ryanhcode.sable.api.command.SubLevelArgumentType;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundEnterGizmoPacket;
import dev.ryanhcode.sable.network.packets.udp.SableUDPEchoPacket;
import dev.ryanhcode.sable.network.udp.SableUDPServer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import foundry.veil.api.network.VeilPacketManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Collection;
import java.util.Formatter;
import java.util.Locale;

public class SableCommand {

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher, final CommandBuildContext buildContext) {
        final LiteralArgumentBuilder<CommandSourceStack> sableBuilder = Commands.literal("sable")
                .requires(commandSourceStack -> commandSourceStack.hasPermission(2));

        SablePhysicsCommands.register(sableBuilder, buildContext);
        SableSpawnCommands.register(sableBuilder, buildContext);
        SableSubLevelCommands.register(sableBuilder, buildContext);
        SableAssembleCommands.register(sableBuilder, buildContext);
        SableStorageCommands.register(sableBuilder, buildContext);

        final LiteralArgumentBuilder<CommandSourceStack> debugBuilder = Commands.literal("debug");

        SableJointCommands.register(debugBuilder, buildContext);
        SableConfigCommands.register(debugBuilder, buildContext);

        sableBuilder
                .then(debugBuilder
                        .then(Commands.literal("udp_test").executes(ctx -> {
                            final SableUDPServer server = SableUDPServer.getServer(ctx.getSource().getServer());

                            if (server != null) {
                                server.sendUDPPacket(ctx.getSource().getPlayerOrException(), new SableUDPEchoPacket("Skibidi Toilet"), true);
                            }

                            return 1;
                        }))
                );

        sableBuilder
                .then(Commands.literal("engage_gizmo")
                        .executes(SableCommand::executeEnableGizmoCommand))

                .then(Commands.literal("paused")
                        .executes(SableCommand::executeTogglePhysicsPausedCommand)
                        .then(Commands.argument("paused", BoolArgumentType.bool())
                                .executes(SableCommand::executeSetPhysicsPausedCommand)))

                .then(Commands.literal("info").then(Commands.argument("sub_level", SubLevelArgumentType.subLevels()).executes(ctx -> {
                    final CommandSourceStack source = ctx.getSource();
                    final ServerSubLevelContainer container = SableCommandHelper.requireSubLevelContainer(source);
                    final Collection<ServerSubLevel> subLevels = SubLevelArgumentType.getSubLevels(ctx, "sub_level");

                    if (subLevels.isEmpty()) {
                        throw SableCommandHelper.ERROR_NO_SUB_LEVELS_FOUND.create();
                    }

                    source.sendSuccess(() -> Component.translatable("commands.sable.info.count", subLevels.size()), false);
                    for (final ServerSubLevel subLevel : subLevels) {
                        final Pose3dc pose = subLevel.logicalPose();
                        source.sendSuccess(() -> {
                            final Vector3dc pos = pose.position();
                            final MutableComponent component = Component.translatable("commands.sable.info.name", Component.literal(subLevel.getName() != null ? subLevel.getName() : subLevel.getUniqueId().toString()));
                            final ResourceLocation dimension = subLevel.getLevel().dimension().location();
                            final GlobalSavedSubLevelPointer pointer = subLevel.getLastSerializationPointer();
                            final Component fileId = Component.translatable("commands.sable.info.name.tooltip", pointer != null ? pointer.toString() : "None yet");
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
                            final Quaterniondc orientation = pose.orientation();
                            return Component.translatable("commands.sable.info.orientation", orientation.x(), orientation.y(), orientation.z(), orientation.w());
                        }, false);
                        source.sendSuccess(() -> {
                            return Component.translatable("commands.sable.info.mass", subLevel.getMassTracker().getMass());
                        }, false);

                        final SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
                        final RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
                        source.sendSuccess(() -> {
                            final Vector3dc pos = handle.getLinearVelocity(new Vector3d());
                            return Component.translatable("commands.sable.info.linear_velocity",
                                    pos.x(), pos.y(), pos.z());
                        }, false);
                        source.sendSuccess(() -> {
                            final Vector3dc pos = handle.getAngularVelocity(new Vector3d());
                            return Component.translatable("commands.sable.info.angular_velocity", pos.x(), pos.y(), pos.z());
                        }, false);
                    }
                    return subLevels.size();
                })));


        dispatcher.register(sableBuilder);

    }

    private static int executeEnableGizmoCommand(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final CommandSourceStack source = ctx.getSource();
        final ServerPlayer player = source.getPlayerOrException();

        SableCommandHelper.requireSubLevelPhysicsSystem(ctx).setPaused(true);

        VeilPacketManager.player(player).sendPacket(new ClientboundEnterGizmoPacket());
        return 1;
    }

    private static int executeTogglePhysicsPausedCommand(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final boolean pause = !SableCommandHelper.requireSubLevelPhysicsSystem(ctx).getPaused();
        SableCommandHelper.requireSubLevelPhysicsSystem(ctx).setPaused(pause);

        ctx.getSource().sendSuccess(() -> Component.translatable("commands.sable.physics.paused_toggled.success", Boolean.toString(pause)), true);
        return 1;
    }

    private static int executeSetPhysicsPausedCommand(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final boolean pause = BoolArgumentType.getBool(ctx, "paused");

        SableCommandHelper.requireSubLevelPhysicsSystem(ctx).setPaused(pause);

        ctx.getSource().sendSuccess(() -> Component.translatable("commands.sable.physics.paused.success", Boolean.toString(pause)), true);
        return 1;
    }
}
