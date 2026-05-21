package dev.ryanhcode.sable.sublevel.render;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import org.joml.*;

import java.io.Closeable;

public interface SubLevelRenderData extends Closeable {

    @Override
    void close();

    /**
     * Forces the sections in this renderer to rebuild.
     */
    void rebuild();

    /**
     * Checks if a section in global section coordinates is compiled
     *
     * @param x the global x coordinate
     * @param y the global y coordinate
     * @param z the global z coordinate
     * @return if the section exists and is compiled
     */
    boolean isSectionCompiled(int x, int y, int z);

    /**
     * Sets a section in global section coordinates as dirty
     *
     * @param x             the global x coordinate
     * @param y             the global y coordinate
     * @param z             the global z coordinate
     * @param playerChanged if the section is dirty from a player action
     */
    void setDirty(final int x, final int y, final int z, final boolean playerChanged);

    /**
     * Compiles all dirty sections in this renderer.
     *
     * @param chunkUpdates      The chunk update mode
     * @param renderRegionCache The render region cache instance for compiling sections
     * @param camera            The camera instance
     */
    void compileSections(PrioritizeChunkUpdates chunkUpdates, final RenderRegionCache renderRegionCache, Camera camera);

    int getVisibleSectionCount();

    default Matrix4f getTransformation(final double camX, final double camY, final double camZ) {
        return this.getTransformation(camX, camY, camZ, new Matrix4f());
    }

    default Matrix4f getTransformation(final double camX, final double camY, final double camZ, final Matrix4f store) {
        store.identity();

        final Pose3dc pose = this.getSubLevel().renderPose();

        final Vector3dc pos = pose.position();
        final Vector3dc scale = pose.scale();
        final Quaterniondc orientation = pose.orientation();

        store.translate((float) (pos.x() - camX), (float) (pos.y() - camY), (float) (pos.z() - camZ));
        store.rotate(new Quaternionf(orientation));
        store.scale((float) scale.x(), (float) scale.y(), (float) scale.z());

        return store;
    }

    ClientSubLevel getSubLevel();

    default Vector3d getChunkOffset() {
        return this.getChunkOffset(new Vector3d());
    }

    default Vector3d getChunkOffset(final Vector3d dest) {
        return this.getSubLevel().renderPose().rotationPoint().negate(dest);
    }
}
