package dev.ryanhcode.sable.api.block;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface BlockSubLevelLiftProvider {

    Direction[] DIRECTIONS = Direction.values();

    static List<LiftProviderGroup> groupLiftProviders(final Collection<LiftProviderContext> liftProviders) {
        final List<LiftProviderGroup> groups = new ObjectArrayList<>();
        final Set<BlockPos> positions = new ObjectOpenHashSet<>(liftProviders.size());

        for (final LiftProviderContext liftProvider : liftProviders) {
            positions.add(liftProvider.pos);
        }

        while (!positions.isEmpty()) {
            // run a flood-fill
            final Set<BlockPos> groupBlocks = new ObjectOpenHashSet<>();
            final List<BlockPos> toVisit = new ObjectArrayList<>();

            toVisit.add(positions.iterator().next());

            while (!toVisit.isEmpty()) {
                final BlockPos pos = toVisit.removeLast();

                if (groupBlocks.contains(pos)) {
                    continue;
                }

                groupBlocks.add(pos);
                positions.remove(pos);

                for (final Direction direction : DIRECTIONS) {
                    final BlockPos offsetPos = pos.relative(direction);

                    if (positions.contains(offsetPos)) {
                        toVisit.add(offsetPos);
                    }
                }
            }

            groups.add(new LiftProviderGroup(groupBlocks));
        }

        return groups;
    }

    /**
     * @param state The current blockstate of this lift provider
     * @return The normal of this lift provider
     */
    @NotNull
    Direction sable$getNormal(BlockState state);

    /**
     * Adjust {@link BlockSubLevelLiftProvider#sable$getDirectionlessDragScalar()} if this value is changed <br>
     * @return How effective this lift provider is at producing drag parallel to the normal.
     */
    default float sable$getParallelDragScalar() {
        return 0.75F;
    }

    /**
     * {@code parallelDragScalar = k1, liftScalar = k2 }<br>
     * Should be at minimum {@code (-k1 + sqrt(k1^2 + k2^2)) / 2} to prevent exponential velocity gain. <br>
     * @return How effective this lift provider is at producing directionless drag.
     */
    default float sable$getDirectionlessDragScalar() {
        return 0.06888202261f; // (-0.75 + sqrt(0.75^2 + 0.475^2)) / 2
    }

    /**
     * Adjust {@link BlockSubLevelLiftProvider#sable$getDirectionlessDragScalar()} if this value is changed <br>
     * @return How effective this lift provider is at producing lift.
     */
    default float sable$getLiftScalar() {
        return 0.475f;
    }

    /**
     * Called once per **physics** tick when this LiftProvider is on a {@link SubLevel}.
     * There may be multiple physics ticks per tick. <br/>
     *
     * @param ctx             The in world context of this lift provider.
     * @param subLevel        The sub-level this lift provider is on
     * @param localPose       The pose of the contraption this lift provider is in, if any
     * @param timeStep        The time step between physics ticks
     * @param linearVelocity  The linear velocity of the data
     * @param angularVelocity The angular velocity of the data
     * @param linearImpulse   Mutable vector to sum the linear impulse
     * @param angularImpulse  Mutable vector to sum the angular impulse
     */
    default void sable$contributeLiftAndDrag(final LiftProviderContext ctx, final ServerSubLevel subLevel,
                                             @NotNull final Pose3d localPose, final double timeStep,
                                             final Vector3dc linearVelocity, final Vector3dc angularVelocity,
                                             final Vector3d linearImpulse, final Vector3d angularImpulse,
                                             @Nullable final LiftProviderGroup group) {

        final Vector3d liftNormal = new Vector3d(ctx.dir.x(), ctx.dir.y(), ctx.dir.z());
        final Vector3d liftPos = new Vector3d(ctx.pos.getX() + 0.5, ctx.pos.getY() + 0.5, ctx.pos.getZ() + 0.5);
        final Vector3d liftForce = new Vector3d();
        final Vector3d transformedPos = new Vector3d();
        final Vector3d drag = new Vector3d();

        if (localPose != null) {
            localPose.transformNormal(liftNormal);
            localPose.transformPosition(liftPos);
        }

        final Pose3d pose = subLevel.logicalPose();
        final double pressure = DimensionPhysicsData.getAirPressure(subLevel.getLevel(), pose.transformPosition(liftPos, transformedPos));

        // transform VELO to be the local velocity at the center of the block
        // TEMP = transformed POS
        // VELO = linVel + angVel cross TEMP
        // VELO = inv transformed VELO
        pose.transformPosition(liftPos, transformedPos).sub(pose.position());
        final Vector3d liftVelo = new Vector3d(linearVelocity).add(angularVelocity.cross(transformedPos, transformedPos));
        pose.transformNormalInverse(liftVelo);

        liftForce.zero(); // optional, zero default

        if (this.sable$getParallelDragScalar() > 0) {
            // DRAG = NORMAL * (NORMAL dot VELO)
            // FORCE = DRAG * scalars
            final double dragStrength = liftNormal.dot(liftVelo) * this.sable$getParallelDragScalar() * pressure * timeStep;
            final Vector3d parallelDrag = liftNormal.mul(dragStrength, drag);
            liftForce.add(parallelDrag);

            if (group != null) {
                group.totalDrag.sub(parallelDrag);
                group.dragCenter.fma(Math.abs(dragStrength), liftPos);
                group.totalDragStrength += Math.abs(dragStrength);
            }
        }

        if (this.sable$getDirectionlessDragScalar() > 0) {
            final Vector3d scaledVelo = new Vector3d();
            // TEMP = VELO * scalars
            // FORCE += TEMP
            final double dragStrength = this.sable$getDirectionlessDragScalar() * pressure * timeStep;
            final Vector3d directionlessDrag = liftVelo.mul(dragStrength, scaledVelo);
            liftForce.add(directionlessDrag);

            if (group != null) {
                group.totalDrag.sub(directionlessDrag);
                group.dragCenter.fma(directionlessDrag.length(), liftPos);
                group.totalDragStrength += directionlessDrag.length();
            }
        }

        if (this.sable$getLiftScalar() > 0) {
            // TEMP = VELO - DRAG
            // TEMP = NORMAL * |TEMP| * scalars
            // FORCE += TEMP

            final Vector3d temp = new Vector3d();

            final double liftStrength = liftVelo.sub(drag, temp).length() * this.sable$getLiftScalar() * pressure * timeStep;
            final Vector3d lift = liftNormal.mul(liftStrength, temp);
            liftForce.add(lift);

            if (group != null) {
                group.totalLift.sub(lift);
                group.liftCenter.fma(Math.abs(liftStrength), liftPos);
                group.totalLiftStrength += liftStrength;
            }
        }

        // why is this all negative of what it should be?
        linearImpulse.sub(liftForce);
        final Vector3d liftOffset = new Vector3d();
        liftPos.sub(subLevel.getMassTracker().getCenterOfMass(), liftOffset);
        angularImpulse.sub(liftOffset.cross(liftForce));
    }

    record LiftProviderContext(BlockPos pos, BlockState state, Vec3 dir) {
    }

    final class LiftProviderGroup {
        private final Set<BlockPos> positions;
        private final Vector3d totalLift = new Vector3d();
        private final Vector3d liftCenter = new Vector3d();
        private final Vector3d totalDrag = new Vector3d();
        private final Vector3d dragCenter = new Vector3d();
        public double totalLiftStrength;
        public double totalDragStrength;

        public LiftProviderGroup(final Set<BlockPos> positions) {
            this.positions = positions;
        }

        public Set<BlockPos> positions() {
            return this.positions;
        }

        public Vector3d totalLift() {
            return this.totalLift;
        }

        public Vector3d liftCenter() {
            return this.liftCenter;
        }

        public Vector3d totalDrag() {
            return this.totalDrag;
        }

        public Vector3d dragCenter() {
            return this.dragCenter;
        }
    }
}