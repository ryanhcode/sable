package dev.ryanhcode.sable.sublevel;

import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.network.client.ClientSableInterpolationState;
import dev.ryanhcode.sable.network.client.SubLevelSnapshotInterpolator;
import dev.ryanhcode.sable.sublevel.plot.ClientLevelPlot;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * A sub-level in a {@link net.minecraft.client.multiplayer.ClientLevel}
 */
public class ClientSubLevel extends SubLevel implements ClientSubLevelAccess {

    /**
     * The renderer for this sub-level
     */
    private SubLevelRenderData renderData;

    /**
     * The latest networked velocity received from the server [m/s]
     */
    private final Vector3d latestNetworkedVelocity = new Vector3d();

    /**
     * The latest networked angular velocity received from the server [rad/s]
     */
    private final Vector3d latestNetworkedAngularVelocity = new Vector3d();

    /**
     * Storage pose for the current frame render pose
     */
    private final Pose3d renderPose = new Pose3d();

    /**
     * The interpolation buffer for the latest server snapshots
     */
    private final SubLevelSnapshotInterpolator interpolator;
    /**
     * Storage for swept bounds to not instantiate new bounding boxes for every {@link ClientSubLevel#boundingBox()} call
     */
    private final BoundingBox3d sweptBounds = new BoundingBox3d();
    /**
     * Last center of the bounds used for sky light calculation
     */
    private final Vector3d lastBoundsCenter = new Vector3d();

    /**
     * Latest sub-level sky light scaling
     */
    private int latestSkyLightScale = -1;

    /**
     * Last partial tick used for rendering interpolation
     */
    private float lastRenderPosePartialTick = -1;

    /**
     * Flywheel lighting scene ID
     */
    private int lightingSceneId = -1;

    /**
     * If we've received all initial data regarding this sub-level from the server (all chunks, bounds, data, etc.)
     */
    private boolean finalized = false;

    /**
     * Creates a new sub-level with the given parent level and pose.
     *
     * @param level the parent level
     * @param plotX the global plot x coordinate
     * @param plotY the global plot y coordinate
     * @param pose  the initialization pose of the sub-level
     */
    public ClientSubLevel(final Level level, final int plotX, final int plotY, final Pose3d pose) {
        super(level, plotX, plotY, pose);

        this.logicalPose().set(pose);
        this.interpolator = new SubLevelSnapshotInterpolator(pose);
    }

    /**
     * Creates the plot for this sub-level.
     *
     * @param plotContainer the parent plot container of this sub-level
     * @param plotX         the global plot x coordinate
     * @param plotY         the global plot y coordinate
     * @param logPlotSize   the log_2 of the side length of a plot, in chunks
     * @return a new {@link LevelPlot} instance for this sub-level
     */
    @Override
    protected LevelPlot createPlot(final SubLevelContainer plotContainer, final int plotX, final int plotY, final int logPlotSize) {
        return new ClientLevelPlot(plotContainer, plotX, plotY, plotContainer.getLogPlotSize(), this);
    }

    /**
     * Ticks this sub-level, updating the global bounding box and components.
     */
    @Override
    public void tick() {
        this.updateLastPose();
        super.tick();

        this.lastRenderPosePartialTick = -1.0f;
        final Pose3d logicalPose = this.logicalPose();

        final ClientSubLevelContainer container = ClientSubLevelContainer.getContainer(this.getLevel());
        assert container != null;
        this.interpolator.tick(container.getInterpolation().getTickPointer());
        final Pose3dc interpolatedPose = this.interpolator.getInterpolatedPose();

        logicalPose.set(interpolatedPose);

        this.updateBoundingBox();

        if (this.lastGlobalBounds.minX == 0 && this.lastGlobalBounds.maxX == 0) {
            // we can assume that we don't have a last bounds yet
            this.sweptBounds.set(this.globalBounds);
        } else {
            this.sweptBounds.set(this.lastGlobalBounds).expandTo(this.globalBounds, this.sweptBounds);
        }

        this.latestSkyLightScale = this.computeSubLevelSkyLight(this.logicalPose());
    }

    public void forceUpdateBounds() {
        this.updateBoundingBox();
        this.lastGlobalBounds.set(this.globalBounds);
        this.sweptBounds.set(this.globalBounds);
    }

    /**
     * Scales a sky light value by this sub-level sky light scale
     */
    public int scaleSkyLight(final int skyLight) {
        return (int) (skyLight * (this.getLatestSkyLightScale() / 15.0f));
    }

    /**
     * Scales a light color value by this sub-level sky light scale
     */
    public int scaleLightColor(int lightColor) {
        final int skyLightScale = this.getLatestSkyLightScale();

        final int newSkyLight = (int) ((lightColor >> 20) * (skyLightScale / 15.0f));
        lightColor = (lightColor & 0xfffff) | (newSkyLight << 20);

        return lightColor;
    }

    /**
     * @return the latest computed sky-light scaling of the sub-level
     */
    public int getLatestSkyLightScale() {
        if (this.latestSkyLightScale == -1) {
            this.latestSkyLightScale = this.computeSubLevelSkyLight(this.logicalPose());
        }
        return this.latestSkyLightScale;
    }

    /**
     * Computes the sky-light scaling of this sub-level
     */
    public int computeSubLevelSkyLight(final Pose3dc pose) {
        final Vector3dc pos = pose.position();
        final ClientLevel level = this.getLevel();

        if (this.boundingBox().volume() < 9) {
            int skyLight = level.getBrightness(LightLayer.SKY, BlockPos.containing(pos.x(), pos.y(), pos.z()));

            if (skyLight == 0)
                skyLight = level.getBrightness(LightLayer.SKY, BlockPos.containing(pos.x(), pos.y() + 1, pos.z()));

            if (skyLight == 0)
                skyLight = level.getBrightness(LightLayer.SKY, BlockPos.containing(pos.x(), pos.y() - 1, pos.z()));

            return skyLight;
        }

        final BoundingBox3dc box = this.boundingBox();
        final Vector3dc center = box.center(this.lastBoundsCenter);
        final double xMin = box.minX();
        final double xMax = box.maxX();
        final double zMin = box.minZ();
        final double zMax = box.maxZ();

        int maxLight = 0;

        final double sampleY = center.y() + 0.1;
        maxLight = Math.max(maxLight, level.getBrightness(LightLayer.SKY, BlockPos.containing(center.x(), sampleY, center.z())));

        maxLight = Math.max(maxLight, level.getBrightness(LightLayer.SKY, BlockPos.containing(xMin, sampleY, zMin)));
        maxLight = Math.max(maxLight, level.getBrightness(LightLayer.SKY, BlockPos.containing(xMax, sampleY, zMin)));
        maxLight = Math.max(maxLight, level.getBrightness(LightLayer.SKY, BlockPos.containing(xMin, sampleY, zMax)));
        maxLight = Math.max(maxLight, level.getBrightness(LightLayer.SKY, BlockPos.containing(xMax, sampleY, zMax)));

        return maxLight;
    }

    /**
     * @return the global bounding box of this sub-level
     */
    @Override
    public BoundingBox3dc boundingBox() {
        return this.sweptBounds;
    }

    /**
     * Called when the bounds of the inner plot have changed.
     */
    @Override
    public void onPlotBoundsChanged() {
        this.renderData = SubLevelRenderDispatcher.get().resize(this, this.renderData);
    }

    @Override
    public void onRemove() {
        if (this.lightingSceneId != -1) {
            SubLevelContainer.getContainer(this.getLevel())
                    .freeLightingScene(this.lightingSceneId);
            this.lightingSceneId = -1;
        }

        super.onRemove();
        this.renderData.close();
    }

    /**
     * Re-creates the render data using the current renderer.
     */
    public void updateRenderData() {
        try {
            if (this.renderData != null) {
                this.renderData.close();
            }
            this.renderData = SubLevelRenderDispatcher.get().createRenderData(this);
        } catch (final Throwable t) {
            final CrashReport crashreport = CrashReport.forThrowable(t, "Updating render data");
            final CrashReportCategory crashreportcategory = crashreport.addCategory("Render Dispatcher");
            crashreportcategory.setDetail("Class", () -> SubLevelRenderDispatcher.get().getClass().getName());
            throw new ReportedException(crashreport);
        }
    }

    /**
     * @return the renderer for this sub-level
     */
    public SubLevelRenderData getRenderData() {
        return this.renderData;
    }

    @Override
    public ClientLevel getLevel() {
        return (ClientLevel) super.getLevel();
    }

    /**
     * @return the plot containing the contents of this sub-level
     */
    @Override
    public ClientLevelPlot getPlot() {
        return (ClientLevelPlot) super.getPlot();
    }

    @ApiStatus.Internal
    public void setLightingSceneId(final int lightingSceneId) {
        this.lightingSceneId = lightingSceneId;
    }

    @ApiStatus.Internal
    public int getLightingSceneId() {
        return this.lightingSceneId;
    }

    /**
     * @return the pose for rendering with the current partialtick
     */
    @Override
    public Pose3dc renderPose() {
        final float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);

        if (this.lastRenderPosePartialTick == pt) {
            return this.renderPose;
        }

        return this.renderPose(pt);
    }

    /**
     * @return the pose for rendering with a given partialtick
     */
    @Override
    public Pose3dc renderPose(final float pt) {
        if (this.lastRenderPosePartialTick == pt) {
            return this.renderPose;
        }

        this.lastRenderPosePartialTick = pt;

        final Pose3d renderPose = this.renderPose.set(this.lastPose());
        final Pose3d target = this.logicalPose();

        renderPose.position().lerp(target.position(), pt);
        renderPose.orientation().slerp(target.orientation(), pt);
        renderPose.rotationPoint().lerp(target.rotationPoint(), pt);
        renderPose.scale().lerp(target.scale(), pt);

        return renderPose;
    }

    public void receiveServerMovementStop() {
        this.latestNetworkedVelocity.zero();
        this.latestNetworkedAngularVelocity.zero();
        this.interpolator.receiveStop();
    }

    @ApiStatus.Internal
    public void wasSplitFrom(final ClientSableInterpolationState state, @NotNull final ClientSubLevel splitFrom, @NotNull final Pose3dc pose) {
        final SubLevelSnapshotInterpolator otherInterpolator = splitFrom.getInterpolator();

        this.interpolator.splitFrom(otherInterpolator, pose);

        this.setInitialPosesFrom(state);
    }

    @ApiStatus.Internal
    public void setInitialPosesFrom(final ClientSableInterpolationState state) {
        if (!state.isStopped()) {
            this.interpolator.getSampleAt(state.mostRecentInterpolationTick, this.logicalPose());
            this.interpolator.getSampleAt(state.lastInterpolationTick, this.lastPose);
        }
    }

    public SubLevelSnapshotInterpolator getInterpolator() {
        return this.interpolator;
    }

    @Override
    public String toString() {
        return "ClientSubLevel" + super.toString();
    }

    /**
     * Sets this sub-level as finalized. This means we've received all initial data regarding this sub-level from the
     * server.
     */
    public void setFinalized() {
        this.finalized = true;
    }

    /**
     * If we've received all initial data regarding this sub-level from the server (all chunks, bounds, data, etc.)
     */
    public boolean isFinalized() {
        return this.finalized;
    }
}
