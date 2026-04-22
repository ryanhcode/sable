package dev.ryanhcode.sable.api.sublevel;


import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.network.client.ClientSableInterpolationState;
import dev.ryanhcode.sable.render.light_bridge.VirtualLightManager;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.ApiStatus;

import java.util.BitSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Holds all sub-levels and plots in a {@link ClientLevel}
 */
public class ClientSubLevelContainer extends SubLevelContainer {
    private final ClientSableInterpolationState interpolation = new ClientSableInterpolationState();

    /**
     * Temp lighting scene IDs for flywheel
     */
    private final BitSet lightingSceneIds;

    /**
     * Creates a new sub-level container with the given side length and plot size.
     *
     * @param level         the level of the plotgrid
     * @param logSideLength the log_2 of the amount of chunks in the side of the plotgrid
     * @param logPlotSize   the log_2 of the amount of chunks in the side of a plot
     * @param originX       the X coordinate in plots of the origin of the plotgrid
     * @param originZ       the Z coordinate in plots of the origin of the plotgrid
     */
    public ClientSubLevelContainer(final Level level, final int logSideLength, final int logPlotSize, final int originX, final int originZ) {
        super(level, logSideLength, logPlotSize, originX, originZ);
        this.lightingSceneIds =  new BitSet(this.subLevels.length);
    }

    @Override
    protected SubLevel createSubLevel(final int globalPlotX, final int globalPlotZ, final Pose3d pose, final UUID uuid) {
        final ClientSubLevel subLevel = new ClientSubLevel(this.getLevel(), globalPlotX, globalPlotZ, pose);
        subLevel.setUniqueId(uuid);
        return subLevel;
    }

    /**
     * Called every tick for the plotgrid.
     */
    @Override
    public void tick() {
        this.interpolation.tick();
        super.tick();
        VirtualLightManager.get().tick(this.getLevel());
    }

    @ApiStatus.Internal
    public void addDebugInfo(final Consumer<String> consumer) {
        consumer.accept("Sub-Levels: " + this.getAllSubLevels().size());
        this.interpolation.addDebugInfo(consumer);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<ClientSubLevel> getAllSubLevels() {
        return (List<ClientSubLevel>) super.getAllSubLevels();
    }

    /**
     * @return the level of the plotgrid.
     */
    @Override
    public ClientLevel getLevel() {
        return (ClientLevel) super.getLevel();
    }

    public ClientSableInterpolationState getInterpolation() {
        return this.interpolation;
    }

    /**
     * Gets the lighting scene ID for a sub-level.
     */
    public int getLightingSceneId(final ClientSubLevel subLevel) {
        synchronized (this.lightingSceneIds) {
            if (subLevel.getLightingSceneId() >= 0) {
                return subLevel.getLightingSceneId();
            }

            for (int i = 0; i < this.lightingSceneIds.size(); i++) {
                if (!this.lightingSceneIds.get(i)) {
                    this.lightingSceneIds.set(i);
                    subLevel.setLightingSceneId(i + 1);
                    return subLevel.getLightingSceneId();
                }
            }

            throw new IllegalStateException("Out of lighting scene ids, uh oh!");
        }
    }

    @ApiStatus.Internal
    public void freeLightingScene(final int lightingSceneId) {
        this.lightingSceneIds.clear(lightingSceneId - 1);
    }
}
