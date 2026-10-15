package dev.ryanhcode.sable.neoforge.gametest;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertPosException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static dev.ryanhcode.sable.neoforge.gametest.SableTestHelper.*;

@GameTestHolder(Sable.MOD_ID)
public final class PhysicsTest {

    @GameTest(template = "continuouscollision", timeoutTicks = 100)
    public static void testUnloadedChunkReadsOffThread(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final BlockPos unloaded = helper.absolutePos(new BlockPos(16_384, 0, 16_384));
        if (level.hasChunkAt(unloaded)) {
            helper.fail("Test chunk was already loaded");
            return;
        }

        CompletableFuture.supplyAsync(() -> {
                    final LevelAccelerator accelerator = new LevelAccelerator(level);
                    return accelerator.getBlockState(unloaded).isAir()
                            && accelerator.getFluidState(unloaded).isEmpty()
                            && accelerator.getBlockEntity(unloaded) == null;
                })
                .orTimeout(2, TimeUnit.SECONDS)
                .whenComplete((safe, failure) -> level.getServer().execute(() -> {
                    if (failure != null) {
                        helper.fail("Off-thread unloaded chunk read blocked or failed: " + failure);
                    } else if (level.hasChunkAt(unloaded)) {
                        helper.fail("Off-thread read loaded the chunk");
                    } else if (!safe) {
                        helper.fail("Unloaded chunk did not read as empty");
                    } else {
                        helper.succeed();
                    }
                }));
    }

    @GameTest(template = "continuouscollision")
    public static void testContinuousCollision(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final ServerSubLevelContainer plotContainer = SubLevelContainer.getContainer(level);
        if (plotContainer == null) {
            throw new IllegalStateException("Plot container not found in level");
        }

        final SubLevelPhysicsSystem physicsSystem = plotContainer.physicsSystem();
        if (physicsSystem == null) {
            throw new IllegalStateException("Plot container does not have physics");
        }

        final ServerSubLevel subLevel = spawnSingleBlockSubLevel(plotContainer, absolutePosition(helper, new Vector3d(2.5, 4, 1.5)), Blocks.GLASS.defaultBlockState());
        final RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        final Vector3d impulse = absoluteDirection(helper, new Vector3d(0, 10, 20));

        helper.startSequence()
                .thenExecuteAfter(10, () -> handle.applyLinearImpulse(impulse))
                .thenExecuteFor(40, () -> {
                    final Vector3d globalPos = subLevel.logicalPose().position();
                    final Vector3d localPos = localPosition(helper, globalPos);
                    if (localPos.z >= 9 || !isInBounds(helper, globalPos)) {
                        helper.fail("Sublevel passed through wall", BlockPos.containing(localPos.x, localPos.y, localPos.z));
                    }
                }).thenSucceed();
    }

    // FIXME allow manual tests to run automatically when rapier is set to 64-bit mode
    @GameTest(template = "gravity", required = false)
    public static void testGravity(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final ServerSubLevelContainer plotContainer = SubLevelContainer.getContainer(level);
        if (plotContainer == null) {
            throw new IllegalStateException("Plot container not found in level");
        }

        final SubLevelPhysicsSystem physicsSystem = plotContainer.physicsSystem();
        if (physicsSystem == null) {
            throw new IllegalStateException("Plot container does not have physics");
        }

        final Vector3dc spawnPos = absolutePosition(helper, new Vector3d(2.5, 12, 2.5));
        final ServerSubLevel subLevel = spawnSingleBlockSubLevel(plotContainer, spawnPos, Blocks.DIAMOND_BLOCK.defaultBlockState());

        helper.runAfterDelay(20, () -> {
            if (subLevel.isRemoved()) {
                helper.fail("Sublevel was removed");
                return;
            }

            final Vector3dc gravity = DimensionPhysicsData.getGravity(helper.getLevel(), spawnPos);
            final RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
            final Vector3dc linearVelocity = handle.getLinearVelocity(new Vector3d());

            if (!gravity.equals(linearVelocity, 1e-2)) {
                final Vector3d localPos = localPosition(helper, spawnPos);
                helper.fail("Sublevel velocity didn't follow gravity: Delta: " + gravity.distance(linearVelocity), BlockPos.containing(localPos.x, localPos.y, localPos.z));
                return;
            }

            // 1/2 * a * t * t
            // t = 1
            final Vector3d expectedDelta = gravity.mul(0.5, new Vector3d());
            final Vector3d delta = subLevel.logicalPose().position().sub(spawnPos, new Vector3d());

            // FIXME for some reason the sublevels don't have a consistent distance travelled. It seems like they aren't spawned on the first tick?
            if (!expectedDelta.equals(delta, 1e-2)) {
                final Vector3d localPos = localPosition(helper, spawnPos);
                helper.fail("Sublevel position didn't follow gravity. Delta: " + expectedDelta.distance(delta), BlockPos.containing(localPos.x, localPos.y, localPos.z));
            }

            helper.succeed();
        });
    }

    @GameTest(template = "snag", attempts = 10, requiredSuccesses = 10, required = false)
    public static void testSnag(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final ServerSubLevelContainer plotContainer = SubLevelContainer.getContainer(level);
        if (plotContainer == null) {
            throw new IllegalStateException("Plot container not found in level");
        }

        final SubLevelPhysicsSystem physicsSystem = plotContainer.physicsSystem();
        if (physicsSystem == null) {
            throw new IllegalStateException("Plot container does not have physics");
        }

        final Vector3dc spawnPos = absolutePosition(helper, new Vector3d(13, 3.5, 3.5));
        final ServerSubLevel subLevel = spawnSingleBlockSubLevel(plotContainer, spawnPos, Blocks.DIAMOND_BLOCK.defaultBlockState());
        final RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        final Vector3d impulse = absoluteDirection(helper, new Vector3d(-60, 0, 0));

        helper.startSequence()
                .thenExecuteAfter(10, () -> handle.applyLinearImpulse(impulse))
                .thenExecuteFor(40, () -> {
                    final Vector3d globalPos = subLevel.logicalPose().position();
                    final Vector3d localPos = localPosition(helper, globalPos);
                    if (localPos.x <= 9 && isInBounds(helper, globalPos)) {
                        helper.succeed();
                    }
                }).thenFail(() -> {
                    final Vector3dc position = subLevel.logicalPose().position();
                    final BlockPos globalPos = BlockPos.containing(position.x(), position.y(), position.z());
                    return new GameTestAssertPosException("Sub-level got stuck", globalPos, helper.relativePos(globalPos), helper.getTick());
                });
    }
}
