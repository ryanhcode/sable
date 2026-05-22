package dev.ryanhcode.sable.neoforge.gametest;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CommonLevelAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import java.util.function.Consumer;

public final class SableTestHelper {

    public static ServerSubLevel spawnSubLevel(final SubLevelContainer plotContainer, final Vector3dc pos, final Consumer<CommonLevelAccessor> setter) {
        final Pose3d pose = new Pose3d();
        pose.position().set(pos);

        final SubLevel subLevel = plotContainer.allocateNewSubLevel(pose);
        final LevelPlot plot = subLevel.getPlot();

        final ChunkPos center = plot.getCenterChunk();
        plot.newEmptyChunk(center);

        setter.accept(plot.getEmbeddedLevelAccessor());
        subLevel.updateLastPose();
        return (ServerSubLevel) subLevel;
    }

    public static ServerSubLevel spawnSingleBlockSubLevel(final SubLevelContainer plotContainer, final Vector3dc pos, final BlockState state) {
        return spawnSubLevel(plotContainer, pos, accessor -> accessor.setBlock(BlockPos.ZERO, state, 3));
    }

    public static Vector3d absoluteDirection(final GameTestHelper helper, final Vector3dc localDirection) {
        return new Vector3d(localDirection).rotateY(-getAngle(helper.getTestRotation()));
    }

    public static Vector3d localDirection(final GameTestHelper helper, final Vector3dc globalDirection) {
        return new Vector3d(globalDirection).rotateY(getAngle(helper.getTestRotation()));
    }

    public static Vector3d absolutePosition(final GameTestHelper helper, final Vector3dc localPosition) {
        final BlockPos origin = helper.testInfo.getStructureBlockPos();
        final Vector3d pos = localPosition.sub(0.5, 0.5, 0.5, new Vector3d()).rotateY(-getAngle(helper.getTestRotation()));
        return pos.add(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5);
    }

    public static Vector3d localPosition(final GameTestHelper helper, final Vector3dc globalPosition) {
        final BlockPos origin = helper.testInfo.getStructureBlockPos();
        final Vector3d pos = globalPosition.sub(origin.getX(), origin.getY(), origin.getZ(), new Vector3d());
        return pos.rotateY(getAngle(helper.getTestRotation()));
    }

    public static double getAngle(final Rotation rotation) {
        return switch (rotation) {
            case NONE -> 0;
            case CLOCKWISE_90 -> Math.PI / 2.0;
            case CLOCKWISE_180 -> Math.PI;
            case COUNTERCLOCKWISE_90 -> -Math.PI / 2.0;
        };
    }

    /**
     * Checks if the specified global position is within the bounds of the test.
     *
     * @param helper         The game test helper instance
     * @param globalPosition The position in global space to check
     * @return Whether that position is inside the bounds of the test
     */
    public static boolean isInBounds(final GameTestHelper helper, final Vector3dc globalPosition) {
        final AABB box = helper.getBounds();
        return box.contains(globalPosition.x(), globalPosition.y(), globalPosition.z());
    }

    public static void removeSubLevel(final SubLevelContainer container, final ServerSubLevel subLevel) {
        final LevelPlot plot = subLevel.getPlot();
        final Vector2i origin = container.getOrigin();
        container.removeSubLevel(plot.plotPos.x - origin.x, plot.plotPos.z - origin.y, SubLevelRemovalReason.REMOVED);
    }
}
