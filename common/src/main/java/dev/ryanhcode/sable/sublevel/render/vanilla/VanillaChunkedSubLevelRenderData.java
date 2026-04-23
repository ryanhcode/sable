package dev.ryanhcode.sable.sublevel.render.vanilla;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.compatibility.SableIrisCompat;
import dev.ryanhcode.sable.mixin.sublevel_render.RenderSectionAccessor;
import dev.ryanhcode.sable.mixinterface.sublevel_render.vanilla.RenderSectionExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionContainer;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionRegion;
import foundry.veil.api.compat.IrisCompat;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.*;

import java.util.Collection;
import java.util.Set;

/**
 * A renderer and view area for a {@link dev.ryanhcode.sable.sublevel.SubLevel}.
 */
public class VanillaChunkedSubLevelRenderData implements SubLevelRenderData {

    private static final Matrix4f TRANSFORM = new Matrix4f();
    private static final Matrix4f MODEL_MATRIX = new Matrix4f();

    private final Vector3d origin = new Vector3d();
    /**
     * The origin(minimum) of the render section grid
     */
    private final Vector3i chunkOrigin = new Vector3i();
    /**
     * The sub-level this renderer is for
     */
    private final ClientSubLevel subLevel;
    /**
     * The size of the render section grid
     */
    private final Vector3i size = new Vector3i();
    /**
     * All render sections this renderer stores
     */
    private final ObjectList<SectionRenderDispatcher.RenderSection> allRenderSections = new ObjectArrayList<>();
    /**
     * All dirty render sections this renderer stores
     */
    private final ObjectList<SectionRenderDispatcher.RenderSection> dirtyRenderSections = new ObjectArrayList<>();
    /**
     * The grid of render sections
     */
    private SectionRenderDispatcher.RenderSection[] renderSections = null;
    /**
     * The section render dispatcher to build sections through
     */
    private final SectionRenderDispatcher sectionRenderDispatcher;

    /**
     * Creates a new renderer for the given sub-level
     *
     * @param subLevel the sub-level to render
     */
    public VanillaChunkedSubLevelRenderData(final ClientSubLevel subLevel, final SectionRenderDispatcher sectionRenderDispatcher) {
        this.subLevel = subLevel;
        this.sectionRenderDispatcher = sectionRenderDispatcher;
        this.resize();
    }

    /**
     * Gets a section in global section coordinates
     *
     * @param sections the section array
     * @param size     the dimensions of the section grid
     * @param origin   the origin of the section grid
     * @param x        the global x coordinate
     * @param y        the global y coordinate
     * @param z        the global z coordinate
     * @return the section if it exists
     */
    private static SectionRenderDispatcher.RenderSection getSection(final SectionRenderDispatcher.RenderSection[] sections, final Vector3i size, final Vector3i origin, final int x, final int y, final int z) {
        final int relX = (x - origin.x());
        final int relY = (y - origin.y());
        final int relZ = (z - origin.z());

        if (relX < 0 || relY < 0 || relZ < 0) {
            return null;
        }

        if (relX >= size.x() || relY >= size.y() || relZ >= size.z()) {
            return null;
        }

        return sections[relX + relY * size.x() + relZ * size.x() * size.y()];
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
        final SectionRenderDispatcher.RenderSection[] oldRenderSections = this.renderSections;
        final Collection<SectionRenderDispatcher.RenderSection> oldRenderSectionsList = new ObjectArrayList<>(this.allRenderSections);

        this.renderSections = null;
        this.allRenderSections.clear();
        this.dirtyRenderSections.clear();

        final BoundingBox3ic bounds = this.subLevel.getPlot().getBoundingBox();

        if (bounds != null && !bounds.equals(BoundingBox3i.EMPTY) && bounds.volume() > 0.0) {
            final Vector3i minChunkPos = new Vector3i(bounds.minX() >> 4, bounds.minY() >> 4, bounds.minZ() >> 4);
            final Vector3i maxChunkPos = new Vector3i(bounds.maxX() >> 4, bounds.maxY() >> 4, bounds.maxZ() >> 4);

            final Vector3i oldSize = new Vector3i(this.size);
            final Vector3i oldOrigin = new Vector3i(this.chunkOrigin);

            this.size.set(maxChunkPos.x() - minChunkPos.x() + 1, maxChunkPos.y() - minChunkPos.y() + 1, maxChunkPos.z() - minChunkPos.z() + 1);
            this.chunkOrigin.set(minChunkPos);
            this.origin.set(minChunkPos.x() << 4, minChunkPos.y() << 4, minChunkPos.z() << 4);

            this.renderSections = new SectionRenderDispatcher.RenderSection[this.size.x() * this.size.y() * this.size.z()];

            for (int x = minChunkPos.x(); x <= maxChunkPos.x(); x++) {
                for (int y = minChunkPos.y(); y <= maxChunkPos.y(); y++) {
                    for (int z = minChunkPos.z(); z <= maxChunkPos.z(); z++) {
                        final SectionRenderDispatcher.RenderSection oldSection = getSection(oldRenderSections, oldSize, oldOrigin, x, y, z);
                        final SectionRenderDispatcher.RenderSection newSection;

                        if (oldRenderSections != null && oldSection != null) {
                            newSection = oldSection;
                        } else {
                            newSection = this.sectionRenderDispatcher.new RenderSection(-1, x << 4, y << 4, z << 4);
                            ((RenderSectionExtension) newSection).sable$addDirtyListener(this.dirtyRenderSections::add);
                        }

                        if (newSection.isDirty()) {
                            this.dirtyRenderSections.add(newSection);
                        }
                        this.renderSections[this.getIndex(x, y, z)] = newSection;
                        this.allRenderSections.add(newSection);
                    }
                }
            }

            // free old chunks
            if (oldRenderSections != null) {
                for (final SectionRenderDispatcher.RenderSection oldSection : oldRenderSectionsList) {
                    // if not in bounds
                    final SectionPos oldSectionPos = SectionPos.of(oldSection.getOrigin());
                    if (oldSectionPos.getX() < minChunkPos.x() || oldSectionPos.getX() > maxChunkPos.x() ||
                            oldSectionPos.getY() < minChunkPos.y() || oldSectionPos.getY() > maxChunkPos.y() ||
                            oldSectionPos.getZ() < minChunkPos.z() || oldSectionPos.getZ() > maxChunkPos.z()) {

                        oldSection.releaseBuffers();
                        oldSection.updateGlobalBlockEntities(Set.of());
                        oldSection.setCompiled(SectionRenderDispatcher.CompiledSection.EMPTY);
                    }
                }
            }
        }
    }

    @Override
    public void rebuild() {
        for (final SectionRenderDispatcher.RenderSection renderSection : this.allRenderSections) {
            renderSection.setDirty(true);
            ((RenderSectionAccessor) renderSection).getGlobalBlockEntities().clear();
        }
    }

    @Override
    public void compileSections(final PrioritizeChunkUpdates chunkUpdates, final RenderRegionCache renderRegionCache, final Camera camera) {
        if (this.dirtyRenderSections.isEmpty()) {
            return;
        }

        final ProfilerFiller profiler = Minecraft.getInstance().getProfiler();
        final Vector3d cameraPos = JOMLConversion.atCenterOf(camera.getBlockPosition()).sub(8, 8, 8);
        this.subLevel.logicalPose().transformPositionInverse(cameraPos);

        for (final SectionRenderDispatcher.RenderSection renderSection : this.dirtyRenderSections) {
            ((RenderSectionExtension) renderSection).sable$setListening(false);

            boolean buildSync = false;
            if (chunkUpdates == PrioritizeChunkUpdates.NEARBY) {
                final BlockPos origin = renderSection.getOrigin();
                buildSync = cameraPos.distanceSquared(origin.getX(), origin.getY(), origin.getZ()) < 768.0 || renderSection.isDirtyFromPlayer();
            } else if (chunkUpdates == PrioritizeChunkUpdates.PLAYER_AFFECTED) {
                buildSync = renderSection.isDirtyFromPlayer();
            }

            if (buildSync) {
                profiler.push("sublevel_build_near_sync");
                this.sectionRenderDispatcher.rebuildSectionSync(renderSection, renderRegionCache);
                profiler.pop();
            } else {
                profiler.push("sublevel_schedule_async_compile");
                renderSection.rebuildSectionAsync(this.sectionRenderDispatcher, renderRegionCache);
                profiler.pop();
            }

            renderSection.setNotDirty();
            ((RenderSectionExtension) renderSection).sable$setListening(true);
        }
        this.dirtyRenderSections.clear();
    }

    @Override
    public ClientSubLevel getSubLevel() {
        return this.subLevel;
    }

    @Override
    public boolean isSectionCompiled(final int x, final int y, final int z) {
        if (this.renderSections == null) {
            return false;
        }

        if (!this.inBounds(x, y, z)) {
            return true;
        }

        final int index = this.getIndex(x, y, z);
        return index >= 0 && index < this.renderSections.length && this.renderSections[index].compiled.get() != SectionRenderDispatcher.CompiledSection.UNCOMPILED;
    }

    @Override
    public void setDirty(final int x, final int y, final int z, final boolean playerChanged) {
        if (this.renderSections == null) {
            return;
        }

        if (!this.inBounds(x, y, z)) {
            return;
        }

        final int index = this.getIndex(x, y, z);
        if (index >= 0 && index < this.renderSections.length) {
            this.renderSections[index].setDirty(playerChanged);
        }
    }

    /**
     * @return all render sections this renderer stores
     */
    public ObjectList<SectionRenderDispatcher.RenderSection> allRenderSections() {
        return this.allRenderSections;
    }

    public void renderChunkedSubLevel(final RenderType layer, final ShaderInstance shader, final Matrix4f modelView, final double camX, final double camY, final double camZ) {
        final Pose3dc renderPose = this.subLevel.renderPose();
        final Vector3d renderPos = new Vector3d(renderPose.position());
        final Quaterniondc renderRot = renderPose.orientation();
        final Vector3d renderCOR = renderRot.transform(new Vector3d(renderPose.rotationPoint()).sub(this.origin));

        float[] oldFogColor = null;

        if (shader.FOG_COLOR != null) {
            final WaterOcclusionContainer<?> container = WaterOcclusionContainer.getContainer(this.subLevel.getLevel());

            final Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            final WaterOcclusionRegion occludingRegion = container.getOccludingRegion(camera.getPosition());

            // TODO: Redo to swap to main fog instead of just getting rid of it
            if (occludingRegion != null && Sable.HELPER.getContaining(this.subLevel.getLevel(), occludingRegion.getVolume().getMinBlockPos()) == this.subLevel) {
                oldFogColor = RenderSystem.getShaderFogColor();
                shader.FOG_COLOR.set(0.0f, 0.0f, 0.0f, 0.0f);
                shader.FOG_COLOR.upload();
            }
        }

        final Uniform sableSkyLightScale = shader.getUniform("SableSkyLightScale");
        if (sableSkyLightScale != null) {
            final int skyLight = this.subLevel.getLatestSkyLightScale();
            sableSkyLightScale.set(skyLight / 15.0f);
            sableSkyLightScale.upload();
        }

        renderPos.sub(renderCOR);

        final Matrix4f transform = TRANSFORM.identity();

        // convert the camera pos to local to the origin / rotated
        final Vector3d fogOffset = new Vector3d(camX, camY, camZ).sub(renderPos).mul(-1.0);

        transform.translate((float) (renderPos.x() - camX - fogOffset.x), (float) (renderPos.y() - camY - fogOffset.y), (float) (renderPos.z() - camZ - fogOffset.z));
        transform.rotate(new Quaternionf(renderRot));

        if (shader.MODEL_VIEW_MATRIX != null) {
            shader.MODEL_VIEW_MATRIX.set(modelView.mul(transform, MODEL_MATRIX));
            shader.MODEL_VIEW_MATRIX.upload();

            if (IrisCompat.isLoaded()) {
                SableIrisCompat.refreshModelMatrices(shader);
            }
        }

        // TODO: sorting
        final Uniform chunkOffsetUniform = shader.CHUNK_OFFSET;

        for (final SectionRenderDispatcher.RenderSection renderSection : this.allRenderSections) {
            if (renderSection.getCompiled().isEmpty(layer)) {
                continue;
            }

            if (chunkOffsetUniform != null) {
                final BlockPos pos = renderSection.getOrigin();
                final Vector3d fogOffsetRot = renderRot.transformInverse(fogOffset, new Vector3d());
                chunkOffsetUniform.set((float) (pos.getX() - this.origin.x() + fogOffsetRot.x), (float) (pos.getY() - this.origin.y() + fogOffsetRot.y), (float) (pos.getZ() - this.origin.z() + fogOffsetRot.z));
                chunkOffsetUniform.upload();
            }

            final VertexBuffer buffer = renderSection.getBuffer(layer);
            buffer.bind();
            buffer.draw();
        }

        if (chunkOffsetUniform != null) {
            chunkOffsetUniform.set(0f, 0f, 0f);
        }

        if (oldFogColor != null) {
            shader.FOG_COLOR.set(oldFogColor[0], oldFogColor[1], oldFogColor[2], oldFogColor[3]);
        }
    }

    @Override
    public void close() {
        for (final SectionRenderDispatcher.RenderSection section : this.allRenderSections) {
            section.releaseBuffers();
            section.updateGlobalBlockEntities(Set.of());
            section.setCompiled(SectionRenderDispatcher.CompiledSection.EMPTY);
        }
        this.allRenderSections.clear();
        this.renderSections = null;
    }

    public SectionRenderDispatcher.RenderSection getRenderSection(final SectionPos sectionPos) {
        if (this.renderSections == null) {
            return null;
        }

        final int index = this.getIndex(sectionPos.getX(), sectionPos.getY(), sectionPos.getZ());

        if (index < 0 || index >= this.renderSections.length) {
            return null;
        }

        return this.renderSections[index];
    }
}
