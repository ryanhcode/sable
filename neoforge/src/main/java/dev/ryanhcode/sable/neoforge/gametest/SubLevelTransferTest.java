package dev.ryanhcode.sable.neoforge.gametest;

import static dev.ryanhcode.sable.neoforge.gametest.SableTestHelper.removeSubLevel;
import static dev.ryanhcode.sable.neoforge.gametest.SableTestHelper.spawnSingleBlockSubLevel;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelTransferResult;
import dev.ryanhcode.sable.api.sublevel.SubLevelTransferService;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.UUID;

@GameTestHolder(Sable.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SubLevelTransferTest {
    private static final String TEMPLATE = "assemblytest.brittlebreak";

    private SubLevelTransferTest() {
    }

    @GameTest(template = TEMPLATE)
    public static void transfersAcrossDimensions(final GameTestHelper helper) {
        final ServerLevel sourceLevel = helper.getLevel();
        final ServerLevel destinationLevel = sourceLevel.getServer().getLevel(Level.END);
        if (destinationLevel == null) {
            helper.fail("The End level is unavailable");
            return;
        }

        final ServerSubLevelContainer sourceContainer = requireContainer(sourceLevel);
        final ServerSubLevelContainer destinationContainer = requireContainer(destinationLevel);
        final Vec3 sourceCenter = helper.absolutePos(BlockPos.ZERO).getCenter();
        final Vector3d sourcePosition = new Vector3d(sourceCenter.x, sourceCenter.y, sourceCenter.z);
        final ServerSubLevel source = spawnSingleBlockSubLevel(
                sourceContainer, sourcePosition, Blocks.DIAMOND_BLOCK.defaultBlockState());
        final UUID uuid = source.getUniqueId();
        final CompoundTag userData = new CompoundTag();
        userData.putString("transfer_test", "preserved");
        source.setUserDataTag(userData);

        final Entity plotEntity = EntityType.ARMOR_STAND.create(sourceLevel);
        if (plotEntity == null) {
            helper.fail("Unable to create plot entity");
            return;
        }
        final Vec3 sourceEntityPosition = source.getPlot().getCenterBlock().getCenter();
        plotEntity.setPos(sourceEntityPosition);
        sourceLevel.addFreshEntity(plotEntity);
        final UUID plotEntityUuid = plotEntity.getUUID();

        final RigidBodyHandle sourceHandle = RigidBodyHandle.of(source);
        if (sourceHandle == null) {
            helper.fail("Source rigid body was not created");
            return;
        }
        sourceHandle.addLinearAndAngularVelocity(new Vector3d(2.0, 3.0, 4.0), new Vector3d(0.0, 0.0, 2.0));

        final Pose3d destinationPose = new Pose3d(source.logicalPose());
        destinationPose.position().set(20.5, 90.0, -12.5);
        final SubLevelTransferResult result = SubLevelTransferService.transfer(source, destinationLevel, destinationPose);
        final ServerSubLevel replacement = result.root();

        if (!source.isRemoved() || sourceContainer.getSubLevel(uuid) != null) {
            helper.fail("Source sub-level remained loaded after transfer");
            return;
        }
        if (destinationContainer.getSubLevel(uuid) != replacement || !replacement.getUniqueId().equals(uuid)) {
            helper.fail("Persistent UUID was not preserved in the destination");
            return;
        }
        if (!replacement.getPlot().getEmbeddedLevelAccessor().getBlockState(BlockPos.ZERO).is(Blocks.DIAMOND_BLOCK)) {
            helper.fail("Transferred block contents were not preserved: state="
                    + replacement.getPlot().getEmbeddedLevelAccessor().getBlockState(BlockPos.ZERO)
                    + ", sourcePlot=" + source.getPlot().plotPos
                    + ", destinationPlot=" + replacement.getPlot().plotPos
                    + ", destinationBounds=" + replacement.getPlot().getBoundingBox());
            return;
        }
        if (replacement.getUserDataTag() == null
                || !"preserved".equals(replacement.getUserDataTag().getString("transfer_test"))) {
            helper.fail("Transferred user data was not preserved");
            return;
        }
        final Entity transferredEntity = destinationLevel.getEntity(plotEntityUuid);
        final Vec3 destinationEntityPosition = replacement.getPlot().getCenterBlock().getCenter();
        if (transferredEntity == null || transferredEntity.position().distanceTo(destinationEntityPosition) > 0.001) {
            helper.fail("Plot entity was not transferred with its sub-level");
            return;
        }
        if (replacement.logicalPose().position().distance(destinationPose.position()) > 0.001) {
            helper.fail("Destination pose was not applied");
            return;
        }

        final RigidBodyHandle replacementHandle = RigidBodyHandle.of(replacement);
        if (replacementHandle == null
                || replacementHandle.getLinearVelocity(new Vector3d()).distance(new Vector3d(2.0, 3.0, 4.0)) > 0.001
                || replacementHandle.getAngularVelocity(new Vector3d()).distance(new Vector3d(0.0, 0.0, 2.0)) > 0.001) {
            helper.fail("Velocity was not preserved across transfer");
            return;
        }

        removeSubLevel(destinationContainer, replacement);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void rejectsDestinationUuidCollision(final GameTestHelper helper) {
        final ServerLevel sourceLevel = helper.getLevel();
        final ServerLevel destinationLevel = sourceLevel.getServer().getLevel(Level.END);
        if (destinationLevel == null) {
            helper.fail("The End level is unavailable");
            return;
        }

        final ServerSubLevelContainer sourceContainer = requireContainer(sourceLevel);
        final ServerSubLevelContainer destinationContainer = requireContainer(destinationLevel);
        final Vec3 sourceCenter = helper.absolutePos(BlockPos.ZERO).getCenter();
        final Vector3d sourcePosition = new Vector3d(sourceCenter.x, sourceCenter.y, sourceCenter.z);
        final ServerSubLevel source = spawnSingleBlockSubLevel(
                sourceContainer, sourcePosition, Blocks.GOLD_BLOCK.defaultBlockState());
        final ServerSubLevel collision = spawnSingleBlockSubLevel(
                destinationContainer,
                source.getUniqueId(),
                new Vector3d(0.5, 80.0, 0.5),
                Blocks.IRON_BLOCK.defaultBlockState());

        try {
            SubLevelTransferService.transfer(source, destinationLevel, new Pose3d(source.logicalPose()));
            helper.fail("Transfer succeeded despite a destination UUID collision");
            return;
        } catch (final IllegalStateException expected) {
            // Expected preflight rejection.
        }

        if (source.isRemoved() || sourceContainer.getSubLevel(source.getUniqueId()) != source) {
            helper.fail("Rejected transfer modified the source sub-level");
            return;
        }
        if (destinationContainer.getSubLevel(collision.getUniqueId()) != collision) {
            helper.fail("Rejected transfer modified the destination sub-level");
            return;
        }

        removeSubLevel(sourceContainer, source);
        removeSubLevel(destinationContainer, collision);
        helper.succeed();
    }

    private static ServerSubLevelContainer requireContainer(final ServerLevel level) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            throw new IllegalStateException("Missing sub-level container in " + level.dimension().location());
        }
        return container;
    }
}
