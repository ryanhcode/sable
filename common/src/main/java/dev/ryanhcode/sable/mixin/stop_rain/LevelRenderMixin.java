package dev.ryanhcode.sable.mixin.stop_rain;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin into {@link net.minecraft.client.renderer.LevelRenderer} to include sub-level blocks when computing the height used for rain and splash rendering.
 */
@Mixin(LevelRenderer.class)
public class LevelRenderMixin {

    @Unique
    private BlockPos.MutableBlockPos sable$tempPos;

    @Unique
    private static int sable$getSubLevelHeight(final Level level, final int pX, final int yOffset, final int pZ) {
        final LevelAccelerator accelerator = new LevelAccelerator(level);

        final BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        final Vector3d checkingPos = new Vector3d();
        final Vector3d localUp = new Vector3d(0, 1, 0);

        final BoundingBox3dc minMaxBB = new BoundingBox3d(pX, level.getMinBuildHeight(), pZ, pX + 1, level.getMaxBuildHeight(), pZ + 1);
        int maxHeight = Integer.MIN_VALUE;
        for (final SubLevel subLevel : Sable.HELPER.getAllIntersecting(level, minMaxBB)) {
            subLevel.logicalPose().transformPositionInverse(checkingPos.set(pX + 0.5f, subLevel.boundingBox().maxY(), pZ + 0.5f));
            subLevel.logicalPose().transformNormalInverse(localUp.set(0, 1, 0));

            final double checkingDistance = subLevel.boundingBox().maxY() - subLevel.boundingBox().minY();
            for (int i = 0; i < checkingDistance; i++) {
                checkingPos.sub(localUp);

                final BlockState gatheredState = accelerator.getBlockState(mutableBlockPos.set(checkingPos.x, checkingPos.y, checkingPos.z));
                if (gatheredState.blocksMotion() || !gatheredState.getFluidState().isEmpty()) {
                    subLevel.logicalPose().transformPosition(checkingPos);
                    maxHeight = (int) Math.max(maxHeight, checkingPos.y + yOffset);
                    break;
                }
            }
        }

        return maxHeight;
    }

    @WrapOperation(method = "renderSnowAndRain", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"))
    public int sable$preventRainThoughSubLevel(final Level instance, final Heightmap.Types types, final int i, final int j, final Operation<Integer> original) {
        return Math.max(original.call(instance, types, i, j), sable$getSubLevelHeight(instance, i, 1, j));
    }

    @WrapOperation(method = "tickRain", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelReader;getHeightmapPos(Lnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;"))
    public BlockPos sable$stopSplashParticles(final LevelReader instance, final Heightmap.Types types, final BlockPos blockPos, final Operation<BlockPos> original) {
        int height = original.call(instance, types, blockPos).getY();
        if (instance instanceof final Level level) {
            height = Math.max(height, sable$getSubLevelHeight(level, blockPos.getX(), 2, blockPos.getZ()));
        }

        return new BlockPos(blockPos.getX(), height, blockPos.getZ());
    }



}
