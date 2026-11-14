package dev.ryanhcode.sable.network.client;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;


/**
 * Manages snapshot interpolation for client sub-levels.
 */
public class SubLevelSnapshotInterpolator {

    /**
     * The buffer of snapshots to interpolate between
     */
    public final ObjectArrayList<Snapshot> buffer = new ObjectArrayList<>();
    /**
     * The current running interpolated snapshot
     */
    private final Pose3d runningSnapshot = new Pose3d();
    private boolean stopped;

    public SubLevelSnapshotInterpolator(final Pose3d pose) {
        this.runningSnapshot.set(pose);
    }

    public void getSampleAt(final double gameTick, final Pose3d dest) {
        // Find the two snapshots to interpolate between
        int beforeIndex = -1;
        Snapshot before = null;
        Snapshot after = null;

        for (int i = 0; i < this.buffer.size(); i++) {
            final Snapshot snapshot = this.buffer.get(i);
            if (snapshot.gameTick == gameTick) {
                dest.set(snapshot.pose);
                return;
            }

            if (snapshot.gameTick < gameTick) {
                beforeIndex = i;
                before = snapshot;
            } else if (snapshot.gameTick > gameTick) {
                after = snapshot;
                break;
            }
        }

        // If we don't have two snapshots to interpolate between, we can't interpolate
        if (before == null || after == null) {
            if (before != null) {
                dest.set(before.pose);

                // dead reckon for a single tick max
                final int beforeBeforeIndex = beforeIndex - 1;
                if (beforeBeforeIndex >= 0 && !this.stopped) {
                    final Snapshot beforeBefore = this.buffer.get(beforeBeforeIndex);

                    final double deadReckoningTicks = Mth.clamp(gameTick - before.gameTick, 0, 1);
                    final double fraction = deadReckoningTicks / (before.gameTick - beforeBefore.gameTick);

                    dest.set(beforeBefore.pose)
                            .lerp(before.pose, 1.0 + fraction);
                }
            } else if (after != null) {
                dest.set(after.pose);
            }
        } else {
            // Calculate the interpolation factor
            final double factor = (gameTick - before.gameTick) / (double) (after.gameTick - before.gameTick);

            // Apply the interpolated snapshot
            before.pose.lerp(after.pose, factor, dest);
        }
    }

    public void receiveSnapshot(final int gameTick, final Pose3dc data) {
        synchronized (this.buffer) {
            if (this.buffer.isEmpty() || this.buffer.getLast().gameTick != gameTick)
                this.buffer.add(new Snapshot(gameTick, data));
        }

        this.stopped = false;
    }

    public void setFirstPoses(final Pose3dc poseA, final Pose3dc poseB) {
        this.runningSnapshot.rotationPoint().set(poseA.rotationPoint());
        this.runningSnapshot.scale().set(poseA.scale());
        this.runningSnapshot.position().set(poseB.position());
    }

    public Pose3dc getInterpolatedPose() {
        return this.runningSnapshot;
    }

    public void receiveStop() {
        this.stopped = true;
    }

    public void splitFrom(final SubLevelSnapshotInterpolator other, @NotNull final Pose3dc pose) {
        for (final Snapshot otherSnapshot : other.buffer) {
            if (otherSnapshot.gameTick >= this.buffer.getFirst().gameTick) continue;
            final Pose3dc containingPose = otherSnapshot.pose;
            final Pose3d madeUpPastPose = new Pose3d(pose);

            madeUpPastPose.orientation().set(containingPose.orientation());
            containingPose.transformPosition(madeUpPastPose.position());
            madeUpPastPose.scale().mul(containingPose.scale());

            this.buffer.add(new Snapshot(otherSnapshot.gameTick, madeUpPastPose));
        }

        // sort the buffer by timestamp
        this.buffer.sort(Comparator.comparingDouble(a -> a.gameTick));
    }

    /**
     * Ticks the snapshot interpolator
     */
    public void tick(final double backTick) {
        // Remove old snapshots
        final int bufferStartTime = (int) (backTick - 6);
        while (!this.buffer.isEmpty() && this.buffer.getFirst().gameTick < bufferStartTime) {
            this.buffer.removeFirst();
        }

        // If we have no snapshots, we can't interpolate
        if (this.buffer.isEmpty()) {
            return;
        }

        this.getSampleAt(backTick, this.runningSnapshot);
    }

    public record Snapshot(int gameTick, Pose3dc pose) {
    }
}
