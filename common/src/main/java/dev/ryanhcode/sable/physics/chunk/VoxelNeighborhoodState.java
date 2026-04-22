package dev.ryanhcode.sable.physics.chunk;

import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.util.LevelAccelerator;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

public enum VoxelNeighborhoodState {
    EMPTY(0x000000),
    FACE(0x8bd21c),
    EDGE(0xe9eb0b),
    CORNER(0xeb6c0b),
    INTERIOR(0x000000);

    public static BiFunction<BlockGetter, BlockState, Boolean> IS_SOLID_MEMOIZED = new BiFunction<>() {
        private final Int2BooleanOpenHashMap cache = new Int2BooleanOpenHashMap();

        @Override
        public Boolean apply(final BlockGetter blockGetter, final BlockState state) {
            return this.cache.computeIfAbsent(state.hashCode(), x -> {
                // TODO add the blockgetter and position as context
                if (state.isAir())
                    return false;

                if (state.getBlock() instanceof MovingPistonBlock)
                    return true;

                return !state.getCollisionShape(blockGetter, BlockPos.ZERO).isEmpty();
            });
        }
    };

    public static BiFunction<BlockGetter, BlockState, Boolean> IS_FULL_BLOCK = new BiFunction<>() {
        private final Int2BooleanOpenHashMap cache = new Int2BooleanOpenHashMap();

        @Override
        public Boolean apply(final BlockGetter blockGetter, final BlockState state) {
            return this.cache.computeIfAbsent(state.hashCode(), x -> {
                // TODO add the blockgetter and position as context
                if (state.isAir())
                    return false;

                return state.isCollisionShapeFullBlock(blockGetter, BlockPos.ZERO);
            });
        }
    };

    private final int debugColor;

    VoxelNeighborhoodState(final int debugColor) {
        this.debugColor = debugColor;
    }

    public static boolean isSolid(final BlockGetter blockGetter, final BlockPos pos, final BlockState state) {
        return IS_SOLID_MEMOIZED.apply(blockGetter, state);
    }

    public static boolean isFullBlock(final BlockGetter blockGetter, final BlockPos pos, final BlockState state) {
        return IS_FULL_BLOCK.apply(blockGetter, state);
    }

    public static int getLiquidType(final BlockState state) {
        if (state.is(Blocks.WATER) || state.getBlock() instanceof KelpPlantBlock || state.getBlock() instanceof KelpBlock) {
            return 1;
        }

        if (state.getBlock() instanceof BubbleColumnBlock) {
            if (state.getValue(BubbleColumnBlock.DRAG_DOWN)) {
                return 3;
            } else {
                return 2;
            }
        }

        if (state.is(Blocks.LAVA)) {
            return 4;
        }
        
        return 0;
    }

    public static VoxelNeighborhoodState getState(final LevelAccelerator level, final BlockPos pos, @Nullable final LevelChunk chunk) {
        final ChunkPos initialPos = new ChunkPos(pos);
        final BlockState state = chunk != null ? level.getBlockState(chunk, pos) : level.getBlockState(pos);

        if (getLiquidType(state) > 0 || BlockWithSubLevelCollisionCallback.hasCallback(state))
            return CORNER;

        if (!isSolid(level, pos, state)) {
            return EMPTY;
        }

        if (!isFullBlock(level, pos, state)) {
            return CORNER;
        }

        boolean allSolid = true;
        boolean cornerSolid = true;
        int bothSidesCount = 0;

        for (final Direction.Axis axis : Direction.Axis.VALUES) {
            final BlockPos nPos = pos.relative(Direction.get(Direction.AxisDirection.NEGATIVE, axis));
            final BlockPos pPos = pos.relative(Direction.get(Direction.AxisDirection.POSITIVE, axis));

            final BlockState nState = chunk != null && new ChunkPos(nPos).equals(initialPos) ? level.getBlockState(chunk, nPos) : level.getBlockState(nPos);
            final BlockState pState = chunk != null && new ChunkPos(pPos).equals(initialPos) ? level.getBlockState(chunk, pPos) : level.getBlockState(pPos);

            final boolean negativeSolid = isSolid(level, nPos, nState) && isFullBlock(level, nPos, nState);
            final boolean positiveSolid = isSolid(level, pPos, pState) && isFullBlock(level, pPos, pState);

            if (!negativeSolid || !positiveSolid) {
                allSolid = false;
            }

            if (negativeSolid && positiveSolid) {
                cornerSolid = false;
                bothSidesCount++;
            }
        }

        if (allSolid) {
            return INTERIOR;
        }

        if (bothSidesCount == 1) {
            return EDGE;
        }

        if (cornerSolid) {
            return CORNER;
        }

        return FACE;
    }

    public int getDebugColor() {
        return this.debugColor;
    }

    public byte byteRepresentation() {
        return (byte) this.ordinal();
    }
}
