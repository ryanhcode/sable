package dev.ryanhcode.sable.mixinhelpers.loaded_chunk_debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.mixinterface.loaded_chunk_debug.DebugChunkProviderAttachments;
import dev.ryanhcode.sable.mixinterface.loaded_chunk_debug.DebugLevelChunkExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix4f;

@ApiStatus.Internal
public class SableChunkDebugRenderer {

    public static void render(final PoseStack poseStack, final MultiBufferSource bufferSource, final double camX, final double camY, final double camZ) {
        final long time = System.currentTimeMillis();

        final Minecraft minecraft = Minecraft.getInstance();
        final Entity entity = minecraft.gameRenderer.getMainCamera().getEntity();
        final VertexConsumer builder = bufferSource.getBuffer(RenderType.debugLineStrip(1.0F));
        final Matrix4f pose = poseStack.last().pose();

        final ClientLevel level = minecraft.level;
        final int minBuildHeight = level.getMinBuildHeight();
        final int maxBuildHeight = level.getMaxBuildHeight();

        final DebugChunkProviderAttachments attachments = (DebugChunkProviderAttachments) level.getChunkSource();
        for (final LevelChunk chunk : attachments.sable$loadedChunks()) {
            final ChunkPos pos = chunk.getPos();
            final float diff = Mth.clamp(time - ((DebugLevelChunkExtension) chunk).sable$getLastUpdate(), 0, 1000) / 1000.0F;
            final float red = 1.0F - diff;
            final float blue = 0.0F;

            final float x = (float) (pos.getMinBlockX() - camX);
            final float z = (float) (pos.getMinBlockZ() - camZ);
            float y = (float) (minBuildHeight - camY);
            if (camY > minBuildHeight) {
                y += (10 * ((1 - diff) / 100));
            } else {
                y -= (10 * ((1 - diff) / 100));
            }
            float y1 = (float) (maxBuildHeight - camY);
            if (camY < maxBuildHeight) {
                y1 -= (10 * ((1 - diff) / 100));
            } else {
                y1 += (10 * ((1 - diff) / 100));
            }
            builder.addVertex(pose, x, y, z).setColor(red, diff, blue, 0.0F);

            builder.addVertex(pose, x, y, z).setColor(red, diff, blue, 1.0F);
            builder.addVertex(pose, x + 16, y, z).setColor(red, diff, blue, 1.0F);
            builder.addVertex(pose, x + 16, y, z + 16).setColor(red, diff, blue, 1.0F);
            builder.addVertex(pose, x, y, z + 16).setColor(red, diff, blue, 1.0F);

            builder.addVertex(pose, x, y, z).setColor(red, diff, blue, 0.0F);

            y = y1;
            builder.addVertex(pose, x, y, z).setColor(red, diff, blue, 0.0F);

            builder.addVertex(pose, x, y, z).setColor(red, diff, blue, 1.0F);
            builder.addVertex(pose, x + 16, y, z).setColor(red, diff, blue, 1.0F);
            builder.addVertex(pose, x + 16, y, z + 16).setColor(red, diff, blue, 1.0F);
            builder.addVertex(pose, x, y, z + 16).setColor(red, diff, blue, 1.0F);

            builder.addVertex(pose, x, y, z).setColor(red, diff, blue, 0.0F);
        }

        final ChunkPos ckPos = entity.chunkPosition();
        final float x = (float) (ckPos.x * 16 - camX);
        float y = (float) (minBuildHeight - camY);
        float y1 = (float) (maxBuildHeight - camY);
        final float z = (float) (ckPos.z * 16 - camZ);

        for (int xO = 0; xO < 2; xO++) {
            for (int zO = 0; zO < 2; zO++) {
                builder.addVertex(pose, x + xO * 16, y, z + zO * 16).setColor(1, 1, 0.0F, 0.0F);

                builder.addVertex(pose, x + xO * 16, y, z + zO * 16).setColor(1, 1, 0.0F, 1.0F);
                builder.addVertex(pose, x + xO * 16, y1, z + zO * 16).setColor(1, 1, 0.0F, 1.0F);

                builder.addVertex(pose, x + xO * 16, y1, z + zO * 16).setColor(1, 1, 0.0F, 0.0F);
            }
        }

        y = minBuildHeight;
        y = ((int) (y / 16)) * 16;
        y1 = maxBuildHeight;

        for (int yO = (int) y; yO <= y1 + 1; yO += 16) {
            builder.addVertex(pose, x, (float) (yO - camY), z).setColor(0, 0, 1.0F, 0.0F);

            builder.addVertex(pose, x, (float) (yO - camY), z).setColor(0, 0, 1.0F, 1.0F);
            builder.addVertex(pose, x + 16, (float) (yO - camY), z).setColor(0, 0, 1.0F, 1.0F);
            builder.addVertex(pose, x + 16, (float) (yO - camY), z + 16).setColor(0, 0, 1.0F, 1.0F);
            builder.addVertex(pose, x, (float) (yO - camY), z + 16).setColor(0, 0, 1.0F, 1.0F);
            builder.addVertex(pose, x, (float) (yO - camY), z).setColor(0, 0, 1.0F, 1.0F);

            builder.addVertex(pose, x, (float) (yO - camY), z).setColor(0, 0, 1.0F, 0.0F);
        }
    }
}