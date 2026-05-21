package dev.ryanhcode.sable.sublevel;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.*;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;

import java.util.UUID;

/**
 * A sub-level is a subdivision of a level, containing:
 * <ul>
 *     <li>An isolated chunk grid, stored in a {@link LevelPlot}</li>
 *     <li>An Isometry, or a 3D pose with translation, rotation, and scale.</li>
 * </ul>
 */
public abstract class SubLevel implements SubLevelAccess {

    /**
     * The parent level of this sub-level
     */
    private final Level level;

    /**
     * The current, logical pose
     */
    private final Pose3d pose;

    /**
     * The logical pose from the previous tick
     */
    protected final Pose3d lastPose;

    /**
     * The global bounding box of this sub-level.
     * Typically, updated per-tick to reflect the current pose.
     */
    protected final BoundingBox3d globalBounds = new BoundingBox3d(0, 0, 0, 0, 0, 0);

    /**
     * The last global bounding box of this sub-level.
     */
    protected final BoundingBox3d lastGlobalBounds = new BoundingBox3d(0, 0, 0, 0, 0, 0);

    /**
     *
     */
    private final Matrix4d globalBoundsTransform = new Matrix4d();

    /**
     * The plot of this sub-level
     */
    private final LevelPlot plot;

    /**
     * If this sub-level is removed / should be removed
     */
    private boolean isRemoved = false;

    /**
     * The unique ID of this sub-level. Will persist throughout lifetime, stay consistent between client and server,
     * and persist across serialization.
     */
    private UUID uniqueId = null;

    /**
     * The display name of this sub-level.
     * Can be set to null to indicate no name.
     */
    @Nullable
    private String name = null;

    /**
     * Creates a new sub-level with the given parent level and pose.
     *
     * @param level the parent level
     * @param plotX the global plot x coordinate
     * @param plotY the global plot y coordinate
     * @param pose  the initialization pose of the sub-level
     */
    protected SubLevel(final Level level, final int plotX, final int plotY, final Pose3d pose) {
        this.level = level;

        final SubLevelContainer plotContainer = SubLevelContainer.getContainer(this.level);
        if (plotContainer == null) {
            throw new IllegalStateException("Level does not have a plot container");
        }

        this.plot = this.createPlot(plotContainer, plotX, plotY, plotContainer.getLogPlotSize());
        this.pose = new Pose3d(pose);
        this.lastPose = new Pose3d(pose);
    }

    /**
     * Creates the plot for this sub-level.
     * @param plotContainer the parent plot container of this sub-level
     * @param plotX the global plot x coordinate
     * @param plotY the global plot y coordinate
     * @param logPlotSize the log_2 of the side length of a plot, in chunks
     * @return a new {@link LevelPlot} instance for this sub-level
     */
    protected abstract LevelPlot createPlot(SubLevelContainer plotContainer, int plotX, int plotY, int logPlotSize);

    /**
     * Called when the bounds of the inner plot have changed.
     */
    public void onPlotBoundsChanged() {
    }

    /**
     * Sets the last pose to the current pose.
     */
    public void updateLastPose() {
        this.lastPose.set(this.pose);
    }

    /**
     * Ticks this sub-level, updating the global bounding box and components.
     */
    public void tick() {
        this.plot.tick();
    }

    public void updateBoundingBox() {
        final BoundingBox3ic plotBounds = this.plot.getBoundingBox();
        assert plotBounds != null : "Plot bounds are null";

        this.lastGlobalBounds.set(this.globalBounds);
        this.globalBounds.set(plotBounds.minX(), plotBounds.minY(), plotBounds.minZ(), plotBounds.maxX() + 1.0, plotBounds.maxY() + 1.0, plotBounds.maxZ() + 1.0);
        this.globalBounds.transform(this.pose, this.globalBoundsTransform, this.globalBounds);
    }

    /**
     * @return the parent level of this sub-level
     */
    public Level getLevel() {
        return this.level;
    }

    /**
     * @return the current pose of this sub-level
     */
    @Override
    public Pose3d logicalPose() {
        return this.pose;
    }

    /**
     * @return the pose of this sub-level from the previous tick
     */
    @Override
    public Pose3dc lastPose() {
        return this.lastPose;
    }

    /**
     * @return the global bounding box of this sub-level
     */
    @Override
    public BoundingBox3dc boundingBox() {
        return this.globalBounds;
    }

    /**
     * @return the plot containing the contents of this sub-level
     */
    public LevelPlot getPlot() {
        return this.plot;
    }

    /**
     * Removes this sub-level from the parent level.
     */
    @ApiStatus.Internal
    public void onRemove() {
        this.plot.onRemove();
        this.markRemoved();
    }

    /**
     * If sub-level is removed / is marked for removal
     *
     * @return if sub-level is removed / marked for removal
     */
    public boolean isRemoved() {
        return this.isRemoved;
    }

    /**
     * Marks sub-level as removed / for removal.
     * The {@link SubLevelContainer} will remove it from on the next tick.
     */
    public void markRemoved() {
        this.isRemoved = true;
    }

    @ApiStatus.Internal
    public void setUniqueId(final UUID uniqueId) {
        this.uniqueId = uniqueId;
    }

    @Override
    public @NotNull UUID getUniqueId() {
        return this.uniqueId;
    }

    @Override
    public @Nullable String getName() {
        return this.name;
    }

    /**
     * Sets the display name of this sub-level.
     *
     * @param name the new name of this sub-level, or null to indicate no name
     */
    public void setName(@Nullable final String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "[name=" + this.name + ", id=" + this.uniqueId + ", global_plot=" + this.plot.plotPos.x + "," + this.plot.plotPos.z + "]";
    }
}
