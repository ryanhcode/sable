package dev.ryanhcode.sable.network.packets.tcp;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.index.SableAttributes;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.util.SableBufferUtils;
import foundry.veil.api.network.handler.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Objects;

/**
 * Sends hit position relative to center of mass of target sublevel, and target angle relative to sublevel player is tracking
 *
 * @param punchedBlock blockpos that is being punched. used to get target sublevel
 * @param localPosition position relative to center of mass of hit sublevel in global space
 * @param direction direction in world space (or plot space of tracking sublevel)
 */
public record ServerboundPunchSubLevelPacket(BlockPos punchedBlock, Vector3dc localPosition,
                                             Vector3dc direction) implements SableTCPPacket {
    public static final Type<ServerboundPunchSubLevelPacket> TYPE = new Type<>(Sable.sablePath("punch_sub_level"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundPunchSubLevelPacket> CODEC = StreamCodec.of((buf, value) -> value.write(buf), ServerboundPunchSubLevelPacket::read);

    private static ServerboundPunchSubLevelPacket read(final FriendlyByteBuf buf) {
        return new ServerboundPunchSubLevelPacket(
                buf.readBlockPos(), SableBufferUtils.read(buf, new Vector3d()), SableBufferUtils.read(buf, new Vector3d()));
    }

    private void write(final FriendlyByteBuf buf) {
        buf.writeBlockPos(this.punchedBlock);
        SableBufferUtils.write(buf, this.localPosition);
        SableBufferUtils.write(buf, this.direction);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public void handle(final PacketContext context) {
        final ServerLevel level = (ServerLevel) context.level();

        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);

        if (container == null) {
            Sable.LOGGER.error("Received a sub-level punch packet for a level without a sub-level container");
            return;
        }

        final Player player = context.player();
        if (!player.onGround() && !player.isInWater() && !player.getAbilities().flying && !player.onClimbable()) return;

        final ServerSubLevel standingSubLevel = (ServerSubLevel) Sable.HELPER.getTrackingSubLevel(player);

        final SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        final SubLevel targetSubLevel = Sable.HELPER.getContaining(level, this.punchedBlock);

        // Let's not let people punch the same sub-level they're standing on
        if (standingSubLevel == targetSubLevel) return;

        final Vector3d localHitPosition = new Vector3d(this.localPosition);
        final Vector3d globalDirection = new Vector3d(this.direction).normalize();

        // If they're punching a sub-level, move the target into global space
        if (targetSubLevel != null) {
            localHitPosition.add(targetSubLevel.logicalPose().position());
            targetSubLevel.logicalPose().transformPositionInverse(localHitPosition);
        }

        // If they're standing on a sub-level, move the direction into global space
        if (standingSubLevel != null) {
            standingSubLevel.logicalPose().transformNormal(globalDirection);
        }

        final double attributeStrength = Objects.requireNonNull(player.getAttribute(SableAttributes.PUNCH_STRENGTH)).getValue();
        final int customCooldown = SableAttributes.getPushCooldownTicks(player);
        if (!physicsSystem.tryPunch(player.getGameProfile().getId(), customCooldown)) {
            return;
        }
        player.getCooldowns().addCooldown(player.getMainHandItem().getItem(), customCooldown);

        final double downwardStrengthMultiplier = SableConfig.SUB_LEVEL_PUNCH_DOWNWARD_STRENGTH_MULTIPLIER.getAsDouble();

        if (globalDirection.y < 0.0) {
            globalDirection.mul(1.0, downwardStrengthMultiplier, 1.0);
        }
        if (!(targetSubLevel instanceof final ServerSubLevel punchedSubLevel)) {
            // The player punched the ground. Are they standing on a sub-level?
            if (standingSubLevel != null) {
                // They're standing on a sub-level. Let's push the sub-level they're standing on back.

                final Pose3d pose = standingSubLevel.logicalPose();
                final Vector3d localPosition = pose.transformPositionInverse(JOMLConversion.toJOML(player.position()));
                final Vector3d localDirection = pose.transformNormalInverse(globalDirection);
                localDirection.negate(); // We're pushing the sub-level away

                final double strengthScalar = computeStrengthScalar(standingSubLevel, localPosition, localDirection);

                physicsSystem.getPipeline().applyImpulse(standingSubLevel, localPosition, localDirection.mul(attributeStrength * strengthScalar, new Vector3d()));
            }
        } else {
            // Punching a sub-level
            final double strengthScalar;
            final Vector3d localHitDirection = punchedSubLevel.logicalPose().transformNormalInverse(globalDirection, new Vector3d());

            if (standingSubLevel == null) {
                strengthScalar = computeStrengthScalar(punchedSubLevel, localHitPosition, localHitDirection);
            } else {
                final Vector3d localPosition = standingSubLevel.logicalPose().transformPositionInverse(JOMLConversion.toJOML(player.position()));
                final Vector3d localDirection = standingSubLevel.logicalPose().transformNormalInverse(new Vector3d(globalDirection));

                final double standingStrength = computeStrengthScalar(standingSubLevel, localPosition, localDirection);
                final double punchedSubLevelScale = computeStrengthScalar(punchedSubLevel, localHitPosition, localHitDirection);

                strengthScalar = Math.min(punchedSubLevelScale, standingStrength);

                localDirection.negate(); // We're pushing the sub-level away
                physicsSystem.getPipeline().applyImpulse(standingSubLevel, localPosition, localDirection.mul(attributeStrength * strengthScalar));
            }

            physicsSystem.getPipeline().applyImpulse(punchedSubLevel, localHitPosition, localHitDirection.mul(attributeStrength * strengthScalar));
        }

        final BlockState blockState = level.getBlockState(this.punchedBlock);
        if (blockState.getFluidState().isEmpty()) {
            final Vector3d particlePos = new Vector3d(localHitPosition).fma(-0.1, globalDirection);

            level.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, blockState),
                    particlePos.x(), particlePos.y(), particlePos.z(), (int) (Math.random() * 3.0), 0.0, 0.0, 0.0, 0.0
            );
        } else {
            this.sendFluidParticles(level, blockState, globalDirection);
        }
    }

    private void sendFluidParticles(final ServerLevel level, final BlockState blockState, final Vector3dc transformedDirection) {
        // TODO: make some sort of "punch effect" system or registry
        if (blockState.getFluidState().is(FluidTags.WATER)) {
            final Vector3d particlePos = new Vector3d(this.localPosition).fma(0.1, transformedDirection);
            level.sendParticles(
                    ParticleTypes.SPLASH,
                    particlePos.x(), particlePos.y(), particlePos.z(), 10, 0.2, 0.2, 0.2, 0.0
            );
            particlePos.fma(0.2, transformedDirection);
            level.sendParticles(
                    ParticleTypes.BUBBLE,
                    particlePos.x(), particlePos.y(), particlePos.z(), 5, 0.2, 0.1, 0.2, 0.0
            );
            level.playSound(null, particlePos.x(), particlePos.y(), particlePos.z(), SoundEvents.PLAYER_SWIM, SoundSource.BLOCKS, 0.2F, 1.0F);
        } else {
            final Vector3d particlePos = new Vector3d(this.localPosition).fma(0.1, transformedDirection);
            level.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, blockState),
                    particlePos.x(), particlePos.y(), particlePos.z(), (int) (Math.random() * 3.0), 0.2, 0.2, 0.2, 0.0
            );
        }
    }

    /**
     * <a href="https://www.desmos.com/calculator/ziovwc5a2v">Tuning Desmos page</a>
     * @author Eriksonn
     */
    public static double punchCurve(final double x) {
        // falloff scale when x >= 1
        final double S = 2;

        // falloff exponent when x >= 1
        final double E = 0.5;

        // slope at mass = 1
        final double k = 0.8;

        // velocity impulse at zero mass
        final double p = 1.75;

        final double u = x - 1;
        final double g = k / (S * E);

        if (x < 1) {
            return (((p + k - 2) * u + k - 1) * u + 1) * x;
        } else {
            final double inverseE = 1 / (E - 1);
            return S * (Math.pow(u + Math.pow(g, inverseE), E) - Math.pow(g, E * inverseE)) + 1;
        }
    }

    private static double computeStrengthScalar(final ServerSubLevel standingSubLevel, final Vector3dc localPosition, final Vector3dc localDirection) {
        final MassData massTracker = standingSubLevel.getMassTracker();

        final double generalizedInverseMass = massTracker.getInverseNormalMass(localPosition, localDirection);
        final double mass = 1.0 / generalizedInverseMass;
        final double strengthMultiplier = SableConfig.SUB_LEVEL_PUNCH_STRENGTH_MULTIPLIER.getAsDouble();

        return punchCurve(mass) * strengthMultiplier;
    }
}