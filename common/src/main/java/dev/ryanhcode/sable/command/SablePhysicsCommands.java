package dev.ryanhcode.sable.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ryanhcode.sable.api.command.SableCommandHelper;
import dev.ryanhcode.sable.api.command.SubLevelArgumentType;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.Collection;
import java.util.function.Function;

public class SablePhysicsCommands {

    /**
     * Adds the following commands:
     * <ul>
     *     <li>{@code /sable physics impulse <sub_level> <linear|angular> <impulse> <global>}</li>
     *     <li>{@code /sable physics rotation <sub_level> <add> <axis/entity> <rotation> <global>}</li>
     *     <li>{@code /sable physics rotation <sub_level> <set> <axis/entity> <rotation>}</li>
     *     <li>{@code /sable physics translation <sub_level> <add> <rotation> <global>}</li>
     *     <li>{@code /sable physics translation <sub_level> <set> <rotation>}</li>
     * </ul>
     */
    public static void register(final LiteralArgumentBuilder<CommandSourceStack> sableBuilder, final CommandBuildContext buildContext) {
        sableBuilder.then(Commands.literal("physics")
                .then(Commands.literal("impulse")
                        .then(Commands.argument("sub_level", SubLevelArgumentType.subLevels())
                                .then(Commands.literal("linear")
                                        .then(Commands.argument("impulse", Vec3ArgumentAbsolute.vec3())
                                                .executes((ctx) ->
                                                        SablePhysicsCommands.executeLinearImpulseCommand(ctx, true))
                                                .then(Commands.literal("global")
                                                        .executes((ctx) ->
                                                                SablePhysicsCommands.executeLinearImpulseCommand(ctx, true)))
                                                .then(Commands.literal("local")
                                                        .executes((ctx) ->
                                                                SablePhysicsCommands.executeLinearImpulseCommand(ctx, false)))
                                        ))

                                .then(Commands.literal("angular")
                                        .then(Commands.argument("impulse", Vec3ArgumentAbsolute.vec3())
                                                .executes((ctx) ->
                                                        SablePhysicsCommands.executeAngularImpulseCommand(ctx, true))
                                                .then(Commands.literal("global")
                                                        .executes((ctx) ->
                                                                SablePhysicsCommands.executeAngularImpulseCommand(ctx, true)))
                                                .then(Commands.literal("local")
                                                        .executes((ctx) ->
                                                                SablePhysicsCommands.executeAngularImpulseCommand(ctx, false)))
                                        ))
                        ))
                .then(Commands.literal("rotation")
                        .then(Commands.argument("sub_level", SubLevelArgumentType.subLevels())
                                .then(wrapRotationWithMode(true))
                                .then(wrapRotationWithMode(false))
                        ))

                .then(Commands.literal("translation")
                        .then(Commands.argument("sub_level", SubLevelArgumentType.subLevels())

                                .then(Commands.literal("add")
                                        .then(Commands.argument("translation", Vec3ArgumentAbsolute.vec3())
                                                .executes((ctx) ->
                                                        SablePhysicsCommands.executeAddTranslationCommand(ctx, true))
                                                .then(Commands.literal("global")
                                                        .executes((ctx) ->
                                                                SablePhysicsCommands.executeAddTranslationCommand(ctx, true)))
                                                .then(Commands.literal("local")
                                                        .executes((ctx) ->
                                                                SablePhysicsCommands.executeAddTranslationCommand(ctx, false)))
                                        ))

                                .then(Commands.literal("set")
                                        .then(Commands.argument("translation", Vec3Argument.vec3(false))
                                                .executes(SablePhysicsCommands::executeSetTranslationCommand))))
                )
        );

    }

    private static Component getGlobalComponent(final boolean global) {
        return Component.translatable("commands.sable.physics." + (global ? "global" : "local"));
    }

    private static int executeLinearImpulseCommand(final CommandContext<CommandSourceStack> ctx, final boolean global) throws CommandSyntaxException {
        final SubLevelPhysicsSystem system = SableCommandHelper.requireSubLevelPhysicsSystem(ctx);

        final Collection<ServerSubLevel> subLevels = SubLevelArgumentType.getSubLevels(ctx, "sub_level");
        final Vec3 impulse = ctx.getArgument("impulse", Vec3.class);

        if (subLevels.isEmpty()) {
            throw SableCommandHelper.ERROR_NO_SUB_LEVELS_FOUND.create();
        }

        for (final ServerSubLevel subLevel : subLevels) {
            Vec3 subLevelImpulse = impulse;
            if (global) {
                subLevelImpulse = subLevel.logicalPose().transformNormalInverse(subLevelImpulse);
            }

            system.getPhysicsHandle(subLevel)
                    .applyLinearImpulse(
                            JOMLConversion.toJOML(subLevelImpulse)
                    );
        }

        SableCommandHelper.sendSuccessDescribingSubLevelsAtIndex("commands.sable.physics.impulse.linear.success", ctx, subLevels, 1,
                getGlobalComponent(global), impulse.x + ", " + impulse.y + ", " + impulse.z);
        return 0;
    }

    private static int executeAngularImpulseCommand(final CommandContext<CommandSourceStack> ctx, final boolean global) throws CommandSyntaxException {
        final SubLevelPhysicsSystem system = SableCommandHelper.requireSubLevelPhysicsSystem(ctx);

        final Collection<ServerSubLevel> subLevels = SubLevelArgumentType.getSubLevels(ctx, "sub_level");
        final Vec3 impulse = ctx.getArgument("impulse", Vec3.class);

        if (subLevels.isEmpty()) {
            throw SableCommandHelper.ERROR_NO_SUB_LEVELS_FOUND.create();
        }

        for (final ServerSubLevel subLevel : subLevels) {
            Vec3 subLevelImpulse = impulse;
            if (global) {
                subLevelImpulse = subLevel.logicalPose().transformNormalInverse(subLevelImpulse);
            }

            system.getPhysicsHandle(subLevel)
                    .applyAngularImpulse(
                            JOMLConversion.toJOML(subLevelImpulse)
                    );
        }

        SableCommandHelper.sendSuccessDescribingSubLevelsAtIndex("commands.sable.physics.impulse.angular.success", ctx, subLevels, 1,
                getGlobalComponent(global), impulse.x + ", " + impulse.y + ", " + impulse.z);
        return 0;
    }

    private static ArgumentBuilder<CommandSourceStack, ?> wrapRotationWithMode(final boolean add) {
        return Commands.literal(add ? "add" : "set").then(wrapRotationWithReferenceFrame(add, false)).then(wrapRotationWithReferenceFrame(add, true));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> wrapRotationWithReferenceFrame(final boolean add, final boolean axis) {
        final Command<CommandSourceStack> c = (ctx) -> SablePhysicsCommands.executeRotationCommand(ctx, add, axis, true);
        final Function<ArgumentBuilder<CommandSourceStack, ?>, ArgumentBuilder<CommandSourceStack, ?>> f = (b) -> {
            if (add)
                b.then(wrapRotationWithGlobality(axis, true)).then(wrapRotationWithGlobality(axis, false));
            return b;
        };
        final ArgumentBuilder<CommandSourceStack, ?> b = axis ?
                Commands.argument("axis", Vec3ArgumentAbsolute.vec3()).then(f.apply(Commands.argument("angle", DoubleArgumentType.doubleArg()).executes(c))) :
                f.apply(Commands.argument("rotation", RotationArgument.rotation()).executes(c));

        return Commands.literal(axis ? "axis" : "entity").then(b);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> wrapRotationWithGlobality(final boolean axis, final boolean global) {
        return Commands.literal(global ? "global" : "local").executes((ctx) ->
                SablePhysicsCommands.executeRotationCommand(ctx, true, axis, global));
    }

    private static int executeRotationCommand(final CommandContext<CommandSourceStack> ctx, final boolean add, final boolean axis, final boolean global) throws CommandSyntaxException {
        final PhysicsPipeline pipeline = SableCommandHelper.requireSubLevelPhysicsPipeline(ctx);

        final Quaterniond orientation = new Quaterniond();

        Vec2 rotation2 = new Vec2(0, 0);
        Vec3 rotationAxis = new Vec3(0, 0, 0);
        double rotationAngle = 0;

        if (axis) {
            rotationAxis = ctx.getArgument("axis", Vec3.class);
            rotationAngle = ctx.getArgument("angle", Double.class);
            orientation.fromAxisAngleDeg(rotationAxis.x, rotationAxis.y, rotationAxis.z, rotationAngle);

            if (rotationAxis.lengthSqr() == 0) {
                throw SableCommandHelper.ERROR_NO_AXIS_FOR_ROTATION.create();
            }
        } else {
            rotation2 = RotationArgument.getRotation(ctx, "rotation").getRotation(ctx.getSource());
            orientation.rotateY(-Math.toRadians(rotation2.y));
            orientation.rotateX(Math.toRadians(rotation2.x));
        }

        final Collection<ServerSubLevel> subLevels = SubLevelArgumentType.getSubLevels(ctx, "sub_level");

        if (subLevels.isEmpty()) {
            throw SableCommandHelper.ERROR_NO_SUB_LEVELS_FOUND.create();
        }

        for (final ServerSubLevel subLevel : subLevels) {
            final Pose3d pose = subLevel.logicalPose();
            if (add) {
                if (global) {
                    pose.orientation().premul(orientation);
                } else {
                    pose.orientation().mul(orientation);
                }
            } else {
                pose.orientation().set(orientation);
            }
            pipeline.teleport(subLevel, pose.position(), pose.orientation());
        }

        if (axis) {
            SableCommandHelper.sendSuccessDescribingSubLevelsAtIndex(
                    add ? "commands.sable.physics.rotation.add.success"
                            : "commands.sable.physics.rotation.set.success",
                    ctx, subLevels, 1,
                    getGlobalComponent(global), rotationAxis.x + ", " + rotationAxis.y + ", " + rotationAxis.z + ", " + rotationAngle);
        } else {
            SableCommandHelper.sendSuccessDescribingSubLevelsAtIndex(
                    add ? "commands.sable.physics.rotation.add.success"
                            : "commands.sable.physics.rotation.set.success",
                    ctx, subLevels, 1,
                    getGlobalComponent(global), rotation2.x + ", " + rotation2.y);
        }
        return 0;
    }

    private static int executeAddTranslationCommand(final CommandContext<CommandSourceStack> ctx, final boolean global) throws CommandSyntaxException {
        final PhysicsPipeline pipeline = SableCommandHelper.requireSubLevelPhysicsPipeline(ctx);

        final Collection<ServerSubLevel> subLevels = SubLevelArgumentType.getSubLevels(ctx, "sub_level");

        if (subLevels.isEmpty()) {
            throw SableCommandHelper.ERROR_NO_SUB_LEVELS_FOUND.create();
        }

        final Vec3 translation = ctx.getArgument("translation", Vec3.class);
        final Vector3d sublevelTranslation = new Vector3d();
        for (final ServerSubLevel subLevel : subLevels) {
            JOMLConversion.toJOML(translation, sublevelTranslation);

            if (!global) {
                subLevel.logicalPose().transformNormal(sublevelTranslation);
            }

            pipeline.teleport(subLevel, subLevel.logicalPose().position().add(sublevelTranslation), subLevel.logicalPose().orientation());
        }

        SableCommandHelper.sendSuccessDescribingSubLevelsAtIndex("commands.sable.physics.translation.add.success", ctx, subLevels, 1,
                getGlobalComponent(global), translation.x + ", " + translation.y + ", " + translation.z);
        return 0;
    }

    private static int executeSetTranslationCommand(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final PhysicsPipeline pipeline = SableCommandHelper.requireSubLevelPhysicsPipeline(ctx);

        final Collection<ServerSubLevel> subLevels = SubLevelArgumentType.getSubLevels(ctx, "sub_level");

        if (subLevels.isEmpty()) {
            throw SableCommandHelper.ERROR_NO_SUB_LEVELS_FOUND.create();
        }

        final Vector3d translation = JOMLConversion.toJOML(Vec3Argument.getVec3(ctx, "translation"));
        for (final ServerSubLevel subLevel : subLevels) {
            pipeline.teleport(subLevel, translation, subLevel.logicalPose().orientation());
        }

        SableCommandHelper.sendSuccessDescribingSubLevels("commands.sable.physics.translation.set.success", ctx, subLevels, translation.x + ", " + translation.y + ", " + translation.z);
        return 0;
    }

}
