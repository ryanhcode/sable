package dev.ryanhcode.sable.sublevel.render.fancy;

import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.core.SectionPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3i;
import org.joml.Vector3ic;

import java.util.LinkedList;
import java.util.List;

public class FancySubLevelRenderData implements SubLevelRenderData {

    private final ClientSubLevel subLevel;
    private final FancySubLevelSectionCompiler compiler;

    private final Vector3d origin = new Vector3d();
    private final Vector3i chunkOrigin = new Vector3i();
    private final Vector3i size = new Vector3i();

    private final List<FancySubLevelSectionCompiler.RenderSection> allRenderSections = new ObjectArrayList<>();
    private final List<FancySubLevelSectionCompiler.RenderSection> dirtyRenderSections = new LinkedList<>();
    private final FancySubLevelOcclusionData occlusionData;
    private FancySubLevelSectionCompiler.RenderSection[] renderSections;

    public FancySubLevelRenderData(final ClientSubLevel subLevel, final FancySubLevelSectionCompiler compiler) {
        this.subLevel = subLevel;
        this.compiler = compiler;
        this.occlusionData = new FancySubLevelOcclusionData(this);
        this.resize();
    }

    /**
     * Gets an index in the render section grid from a global position
     */
    private int getIndex(final int x, final int y, final int z) {
        return (x - this.chunkOrigin.x()) + (y - this.chunkOrigin.y()) * this.size.x() + (z - this.chunkOrigin.z()) * this.size.x() * this.size.y();
    }

    /**
     * Checks if a global section coordinate is in bounds
     */
    private boolean inBounds(final int x, final int y, final int z) {
        final int localX = x - this.chunkOrigin.x();
        final int localY = y - this.chunkOrigin.y();
        final int localZ = z - this.chunkOrigin.z();
        return localX >= 0 && localY >= 0 && localZ >= 0 &&
                localX < this.size.x() && localY < this.size.y() && localZ < this.size.z();

    }

    public void resize() {
        final BoundingBox3ic bounds = this.subLevel.getPlot().getBoundingBox();
        for (final FancySubLevelSectionCompiler.RenderSection section : this.allRenderSections) {
            section.free();
        }
        this.allRenderSections.clear();
        this.dirtyRenderSections.clear();

        if (bounds != null && !bounds.equals(BoundingBox3i.EMPTY) && bounds.volume() > 0.0) {
            final Vector3i minChunkPos = new Vector3i(bounds.minX() >> 4, bounds.minY() >> 4, bounds.minZ() >> 4);
            final Vector3i maxChunkPos = new Vector3i(bounds.maxX() >> 4, bounds.maxY() >> 4, bounds.maxZ() >> 4);

            this.size.set(maxChunkPos.x() - minChunkPos.x() + 1, maxChunkPos.y() - minChunkPos.y() + 1, maxChunkPos.z() - minChunkPos.z() + 1);
            this.chunkOrigin.set(minChunkPos);
            this.origin.set(minChunkPos.x() << 4, minChunkPos.y() << 4, minChunkPos.z() << 4);

            this.renderSections = new FancySubLevelSectionCompiler.RenderSection[this.size.x() * this.size.y() * this.size.z()];

            for (int z = minChunkPos.z(); z <= maxChunkPos.z(); z++) {
                for (int x = minChunkPos.x(); x <= maxChunkPos.x(); x++) {
                    for (int y = minChunkPos.y(); y <= maxChunkPos.y(); y++) {
                        final FancySubLevelSectionCompiler.RenderSection section = new FancySubLevelSectionCompiler.RenderSection(SectionPos.of(x, y, z), minChunkPos);
                        this.renderSections[this.getIndex(x, y, z)] = section;
                        this.allRenderSections.add(section);
                        if (section.isDirty()) {
                            this.dirtyRenderSections.add(section);
                        }
                    }
                }
            }
        }

        this.occlusionData.invalidate();
    }

    /**
     * Retrieves a render section relative to this plot.
     *
     * @param x The x-relative position
     * @param y The y-relative position
     * @param z The z-relative position
     * @return The section at that location or <code>null</code> if out of bounds
     */
    public @Nullable FancySubLevelSectionCompiler.RenderSection getRenderSection(final int x, final int y, final int z) {
        final int index = this.getIndex(x, y, z);
        return index >= 0 && index < this.renderSections.length ? this.renderSections[index] : null;
    }

    public Vector3dc getOrigin() {
        return this.origin;
    }

    public Vector3ic getChunkOrigin() {
        return this.chunkOrigin;
    }

    public Vector3ic getSize() {
        return this.size;
    }

    @Override
    public void rebuild() {
        // Since the buffer is fully cleared by the dispatcher, there's no point in looping through every section to free it
        this.allRenderSections.clear();
        this.resize();
    }

    /**
     * Checks if a section in global section coordinates is compiled
     *
     * @param x the global x coordinate
     * @param y the global y coordinate
     * @param z the global z coordinate
     * @return if the section exists and is compiled
     */
    @Override
    public boolean isSectionCompiled(final int x, final int y, final int z) {
        final FancySubLevelSectionCompiler.RenderSection section = this.getRenderSection(x, y, z);
        return section != null && section.getCompiledSection() != FancySubLevelSectionCompiler.CompiledSection.UNCOMPILED;
    }

    @Override
    public void setDirty(final int x, final int y, final int z, final boolean playerChanged) {
        final int index = this.getIndex(x, y, z);
        if (index < 0 || index >= this.renderSections.length) {
            return;
        }

        final FancySubLevelSectionCompiler.RenderSection section = this.renderSections[index];
        if (section == null) {
            return;
        }

        if (!section.isDirty()) {
            section.setDirty(playerChanged);
            this.dirtyRenderSections.add(section);
        }
    }

    @Override
    public void compileSections(final PrioritizeChunkUpdates chunkUpdates, final RenderRegionCache renderRegionCache, final Camera camera) {
        if (this.dirtyRenderSections.isEmpty()) {
            return;
        }

        final ClientLevel level = this.subLevel.getLevel();
        final Vector3d cameraPos = JOMLConversion.atCenterOf(camera.getBlockPosition()).sub(8, 8, 8);
        this.subLevel.logicalPose().transformPositionInverse(cameraPos);

        for (final FancySubLevelSectionCompiler.RenderSection section : this.dirtyRenderSections) {
            final SectionPos origin = section.getPos();
            final double distanceSq = cameraPos.distanceSquared(
                    origin.x() << SectionPos.SECTION_BITS,
                    origin.y() << SectionPos.SECTION_BITS,
                    origin.z() << SectionPos.SECTION_BITS);

            this.compiler.getScheduler().scheduleCompile(section, renderRegionCache.createRegion(level, section.getPos()), distanceSq, this.occlusionData::addSection);
            section.setNotDirty();
        }

        this.dirtyRenderSections.clear();
    }

    @Override
    public int getVisibleSectionCount() {
        return this.allRenderSections.size();
    }

    @Override
    public ClientSubLevel getSubLevel() {
        return this.subLevel;
    }

    @Override
    public void close() {
    }

    public FancySubLevelOcclusionData getOcclusionData() {
        return this.occlusionData;
    }
}
