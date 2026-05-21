package dev.ryanhcode.sable.neoforge.gametest;

import static dev.ryanhcode.sable.neoforge.gametest.SableTestHelper.removeSubLevel;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestAssertPosException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector3ic;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@GameTestHolder(Sable.MOD_ID)
public final class AssemblyTest {

    private static final Direction[] CAPABILITY_DIRECTIONS = {
            null,
            Direction.DOWN,
            Direction.UP,
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST
    };

    @GameTest(template = "brittlebreak")
    public static void testBrittleBreaking(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final ServerSubLevelContainer plotContainer = SubLevelContainer.getContainer(level);
        if (plotContainer == null) {
            throw new IllegalStateException("Plot container not found in level");
        }

        final SubLevelPhysicsSystem physicsSystem = plotContainer.physicsSystem();

        final BlockPos min = helper.absolutePos(new BlockPos(0, 1, 0));
        final BlockPos max = helper.absolutePos(new BlockPos(2, 3, 2));
        final BoundingBox3i bounds = new BoundingBox3i(
                min.getX(), min.getY(), min.getZ(),
                max.getX(), max.getY(), max.getZ()
        );

        final List<BlockState> expectedStates = new ArrayList<>(bounds.volume());
        for (final BlockPos pos : BlockPos.betweenClosed(min, max)) {
            expectedStates.add(level.getBlockState(pos));
        }

        final ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(level, min, BlockPos.betweenClosed(min, max), bounds);
        physicsSystem.getPipeline().teleport(subLevel,
                new Vector3d(min.getX() + (1 + max.getX() - min.getX()) / 2.0,
                        min.getY() + (1 + max.getY() - min.getY()) / 2.0,
                        min.getZ() + (1 + max.getZ() - min.getZ()) / 2.0),
                helper.getTestRotation().rotation().transformation().getNormalizedRotation(new Quaterniond()));
        helper.runAtTickTime(10, () -> {
            final Level plot = subLevel.getLevel();
            final BoundingBox3ic sublevelBounds = subLevel.getPlot().getBoundingBox();
            final Vector3ic actualSize = sublevelBounds.size(new Vector3i());
            final Vector3ic expectedSize = bounds.size(new Vector3i());
            if (actualSize.equals(expectedSize)) {
                int i = 0;
                for (final BlockPos pos : BlockPos.betweenClosed(sublevelBounds.minX(),
                        sublevelBounds.minY(),
                        sublevelBounds.minZ(),
                        sublevelBounds.maxX(),
                        sublevelBounds.maxY(),
                        sublevelBounds.maxZ())) {
                    final BlockState expected = expectedStates.get(i);
                    if (!plot.getBlockState(pos).equals(expected)) {
                        throw new GameTestAssertPosException("Expected %s".formatted(expected.getBlock().getName().getString()), pos, pos, helper.getTick());
                    }
                    i++;
                }
                helper.succeed();
            } else {
                helper.fail("Expected %dx%dx%d region, got %dx%dx%d".formatted(
                        expectedSize.x(),
                        expectedSize.y(),
                        expectedSize.z(),
                        actualSize.x(),
                        actualSize.y(),
                        actualSize.z()));
            }
        });
    }

    @GameTest(template = "allblocks", required = false, manualOnly = true, timeoutTicks = 30_000_000)
    public static void testAllBlocks(final GameTestHelper helper) {
        final boolean failOnFirstError = false;
        final boolean fastTest = true;
        final Set<ResourceLocation> skip = Set.of(
                ResourceLocation.fromNamespaceAndPath("copycats", "wrapped_copycat")
        );
        final Set<ResourceLocation> illegalInventories = Set.of(
                ResourceLocation.fromNamespaceAndPath("create_new_age", "reactor_fuel_acceptor"),
                ResourceLocation.fromNamespaceAndPath("farmersdelight", "cooking_pot") // Unsure why this failed
        );

        final ServerLevel level = helper.getLevel();
        final ServerSubLevelContainer plotContainer = SubLevelContainer.getContainer(level);
        if (plotContainer == null) {
            throw new IllegalStateException("Plot container not found in level");
        }

        final SubLevelPhysicsSystem physicsSystem = plotContainer.physicsSystem();
        final ItemStack insertStack = new ItemStack(Items.OCELOT_SPAWN_EGG);

        final BlockPos pos = helper.absolutePos(new BlockPos(2, 3, 2));
        final BlockPos onPos = pos.below();
        final Set<Block> invalidBlocks = new HashSet<>();
        final Set<Block> failures = new HashSet<>();

        final TestProgressBar progressBar = new TestProgressBar(level.getServer().getPlayerList());
        final AtomicLong completedItems = new AtomicLong();

        long tick = 0;
        long tests = 0;
        for (final Map.Entry<ResourceKey<Block>, Block> entry : BuiltInRegistries.BLOCK.entrySet()) {
            final ResourceLocation blockId = entry.getKey().location();
            if (skip.contains(blockId)) {
                continue;
            }

            final Block block = entry.getValue();
            for (final BlockState state : block.getStateDefinition().getPossibleStates()) {
                // Bug with lecterns. If the block state is set, but there isn't a book it will try to drop an air item
                if (state.is(Blocks.LECTERN) && state.getValue(BlockStateProperties.HAS_BOOK)) {
                    continue;
                }

                boolean hasInventory = false;
                for (@Nullable final Direction direction : CAPABILITY_DIRECTIONS) {
                    final IItemHandler inventory = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, level.getBlockEntity(pos), direction);
                    if (inventory != null) {
                        hasInventory = true;
                        break;
                    }
                }

                if (!hasInventory && !state.hasBlockEntity()) {
                    continue;
                }

                tests++;
                helper.runAtTickTime(tick += 2, () -> {
                    level.setBlock(onPos, Blocks.STONE.defaultBlockState(), 2);
                    level.setBlock(pos, state, 2);

                    // The block was unstable and can't be placed in this configuration
                    if (isInvalidState(level.getBlockState(pos))) {
                        helper.killAllEntities();
                        invalidBlocks.add(block);
                        return;
                    }

                    final List<Entity> startEntities = level.getEntities(EntityTypeTest.forClass(Entity.class), helper.getBounds(), Entity::isAlive);
                    final NonNullList<ItemStack>[] startingInventory = new NonNullList[CAPABILITY_DIRECTIONS.length];

                    for (int i = 0; i < CAPABILITY_DIRECTIONS.length; i++) {
                        final Direction direction = CAPABILITY_DIRECTIONS[i];
                        final IItemHandler inventory = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, level.getBlockEntity(pos), direction);

                        if (inventory != null) {
                            try {
                                final int slots = inventory.getSlots();
                                if (inventory instanceof final IItemHandlerModifiable modifiable) {
                                    for (int slot = 0; slot < slots; slot++) {
                                        try {
                                            modifiable.setStackInSlot(slot, insertStack.copy());
                                        } catch (final Throwable ignored) {
                                            inventory.insertItem(slot, insertStack.copy(), false);
                                        }
                                    }
                                } else {
                                    for (int slot = 0; slot < slots; slot++) {
                                        inventory.insertItem(slot, insertStack.copy(), false);
                                    }
                                }

                                final NonNullList<ItemStack> list = NonNullList.withSize(slots, ItemStack.EMPTY);
                                for (int slot = 0; slot < slots; slot++) {
                                    list.set(slot, inventory.getStackInSlot(slot).copy());
                                }
                                startingInventory[i] = list;
                            } catch (final Throwable t) {
                                t.printStackTrace();
                                helper.fail(formatBlockState(state).getString() + " failed. Unable to insert items successfully for face " + direction, pos);
                            }
                        }
                    }

                    helper.runAfterDelay(1, () -> {
                        final ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(level, pos, List.of(pos, onPos), new BoundingBox3i(
                                onPos.getX(),
                                onPos.getY(),
                                onPos.getZ(),
                                pos.getX() + 1,
                                pos.getY() + 1,
                                pos.getZ() + 1));
                        physicsSystem.getPipeline().teleport(subLevel,
                                new Vector3d(pos.getX() + 0.5,
                                        pos.getY() + 1.0,
                                        pos.getZ() + 0.5),
                                helper.getTestRotation().rotation().transformation().getNormalizedRotation(new Quaterniond()));

                        final BlockPos centerBlock = subLevel.getPlot().getCenterBlock();

                        if (isInvalidState(level.getBlockState(centerBlock))) {
                            invalidBlocks.add(block);
                            helper.killAllEntities();
                            removeSubLevel(plotContainer, subLevel);
                            progressBar.update(completedItems.incrementAndGet());
                            return;
                        }

                        final List<Entity> resultEntities = level.getEntities(EntityTypeTest.forClass(Entity.class), helper.getBounds(), Entity::isAlive);
                        if (startEntities.size() != resultEntities.size()) {
                            if (failOnFirstError) {
                                final List<Component> names = new ArrayList<>(resultEntities.size());
                                for (final Entity entity : resultEntities) {
                                    if (!startEntities.contains(entity)) {
                                        names.add(entity.getDisplayName());
                                    }
                                }
                                final String formattedEntities = ComponentUtils.formatList(names, Component.literal(", ")).getString();
                                helper.fail(formatBlockState(state).getString() + " failed. Expected " + startEntities.size() + " entities to exist, found " + formattedEntities);
                                return;
                            }
                            failures.add(block);
                            helper.killAllEntities();
                        }

                        if (!illegalInventories.contains(blockId)) {
                            for (int i = 0; i < CAPABILITY_DIRECTIONS.length; i++) {
                                final Direction direction = CAPABILITY_DIRECTIONS[i];
                                final IItemHandler inventory = level.getCapability(Capabilities.ItemHandler.BLOCK, centerBlock, state, level.getBlockEntity(centerBlock), direction);
                                final NonNullList<ItemStack> starting = startingInventory[i];

                                if (inventory != null) {
                                    if (starting == null) {
                                        final String stateString = formatBlockState(state).getString();
                                        helper.fail(stateString + " failed. Expected no inventory for face " + direction + ", found items");
                                        return;
                                    }

                                    if (starting.size() != inventory.getSlots()) {
                                        final String stateString = formatBlockState(state).getString();
                                        helper.fail(stateString + " failed. Expected " + starting.size() + " inventory slots for face " + direction + ", found " + inventory.getSlots());
                                        return;
                                    }

                                    try {
                                        for (int slot = 0; slot < starting.size(); slot++) {
                                            if (!ItemStack.isSameItemSameComponents(starting.get(slot), inventory.getStackInSlot(slot))) {
                                                final String stateString = formatBlockState(state).getString();
                                                final String expectedStack = starting.get(slot).toString();
                                                final String foundStack = inventory.getStackInSlot(slot).toString();
                                                helper.fail(stateString + " failed. Expected slot " + slot + " for face " + direction + " to be " + expectedStack + " found " + foundStack);
                                                return;
                                            }
                                        }
                                    } catch (final GameTestAssertException e) {
                                        throw e;
                                    } catch (final Throwable t) {
                                        t.printStackTrace();
                                        helper.fail(formatBlockState(state).getString() + " failed. Unable to get items successfully for face " + direction);
                                    }
                                } else if (starting != null) {
                                    final String stateString = formatBlockState(state).getString();
                                    helper.fail(stateString + " failed. Expected inventory items for face " + direction + ", found none");
                                    return;
                                }
                            }
                        }

                        removeSubLevel(plotContainer, subLevel);
                        progressBar.update(completedItems.incrementAndGet());
                    });
                });

                if (fastTest) {
                    break;
                }
            }
        }

        progressBar.begin(tests);

        helper.runAtTickTime(tick + 1, () -> {
            progressBar.end();

            if (!invalidBlocks.isEmpty()) {
                final List<String> names = new ArrayList<>(invalidBlocks.size());
                for (final Block block : invalidBlocks) {
                    names.add(String.valueOf(BuiltInRegistries.BLOCK.getKey(block)));
                }
                final String formattedLines = String.join("\n", names);
                Sable.LOGGER.info("Skipped blocks:\n{}", formattedLines);
            }

            if (!failures.isEmpty()) {
                final List<String> names = new ArrayList<>(failures.size());
                for (final Block block : failures) {
                    names.add(String.valueOf(BuiltInRegistries.BLOCK.getKey(block)));
                }
                final String formattedLines = String.join("\n", names);
                helper.fail(failures.size() + " blocks failed.\n" + formattedLines);
            }

            helper.succeed();
        });
    }

    private static Component formatBlockState(final BlockState state) {
        final MutableComponent name = Component.literal(String.valueOf(BuiltInRegistries.BLOCK.getKey(state.getBlock())));

        final Collection<Property<?>> properties = state.getProperties();
        if (!properties.isEmpty()) {
            final StringBuilder propertiesString = new StringBuilder("[");
            for (final Property<?> property : properties) {
                final Object value = state.getValue(property);
                propertiesString.append(property.getName()).append("=").append(value).append(",");
            }
            propertiesString.setCharAt(propertiesString.length() - 1, ']');
            name.append(propertiesString.toString());
        }

        return name;
    }

    private static boolean isInvalidState(final BlockState state) {
        return state.isAir() || state.getFluidState().createLegacyBlock() == state;
    }
}
