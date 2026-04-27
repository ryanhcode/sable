package dev.ryanhcode.sable.mixin.block_placement;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes the rotation of block placement to take into account orientation
 * <p>
 * TODO: account for differing collision shapes
 */
@Mixin(BlockPlaceContext.class)
public abstract class BlockPlaceContextMixin extends UseOnContext {

    @Unique
    private final LevelReusedVectors sable$sink = new LevelReusedVectors();
    @Shadow
    protected boolean replaceClicked;

    public BlockPlaceContextMixin(final Player pPlayer, final InteractionHand pHand, final BlockHitResult pHitResult) {
        super(pPlayer, pHand, pHitResult);
    }

    @Shadow
    public abstract BlockPos getClickedPos();

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Direction;getFacingAxis(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Direction$Axis;)Lnet/minecraft/core/Direction;"))
    private Direction sable$getFacingAxis(final Entity player, final Direction.Axis axis) {
        final SubLevel subLevel = Sable.HELPER.getContaining(this.getLevel(), this.getClickedPos());

        if (subLevel != null) {
            SubLevelHelper.pushEntityLocal(subLevel, player);
            final Direction facingAxis = Direction.getFacingAxis(player, axis);
            SubLevelHelper.popEntityLocal(subLevel, player);
            return facingAxis;
        }

        return Direction.getFacingAxis(player, axis);
    }

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Direction;orderedByNearest(Lnet/minecraft/world/entity/Entity;)[Lnet/minecraft/core/Direction;"))
    private Direction[] sable$orderedByNearest(final Entity player) {
        final SubLevel subLevel = Sable.HELPER.getContaining(this.getLevel(), this.getClickedPos());

        if (subLevel != null) {
            SubLevelHelper.pushEntityLocal(subLevel, player);
            final Direction[] nearest = Direction.orderedByNearest(player);
            SubLevelHelper.popEntityLocal(subLevel, player);
            return nearest;
        }

        return Direction.orderedByNearest(player);
    }

    @Inject(method = "canPlace", at = @At("HEAD"), cancellable = true)
    private void sable$canPlace(final CallbackInfoReturnable<Boolean> cir) {
        final BlockPos clicked = this.getClickedPos();
        final SubLevel subLevel = Sable.HELPER.getContaining(this.getLevel(), this.getClickedPos());

        final BoundingBox3d placedBoxBoundingBox = new BoundingBox3d(clicked);
        final Quaterniond placedBoxOrientation = new Quaterniond();

        final Vector3d placedBoxPosition = new Vector3d(clicked.getX() + 0.5, clicked.getY() + 0.5, clicked.getZ() + 0.5);

        if (subLevel != null) {
            subLevel.logicalPose().transformPosition(placedBoxPosition);
            placedBoxOrientation.set(subLevel.logicalPose().orientation());

            placedBoxBoundingBox.transform(subLevel.logicalPose(), placedBoxBoundingBox);
        }

        final Iterable<SubLevel> subLevels = Sable.HELPER.getAllIntersecting(this.getLevel(), placedBoxBoundingBox);

        for (final SubLevel otherSubLevel : subLevels) {
            if (otherSubLevel == subLevel) {
                continue;
            }

            final boolean cancelled = this.sable$intersectBlocks(cir, otherSubLevel, placedBoxBoundingBox, this.sable$sink, placedBoxPosition, placedBoxOrientation);
            if (cancelled)
                return;
        }

        this.sable$intersectBlocks(cir, null, placedBoxBoundingBox, this.sable$sink, placedBoxPosition, placedBoxOrientation);
    }

    @Unique
    private boolean sable$intersectBlocks(final CallbackInfoReturnable<Boolean> cir, @Nullable final SubLevel otherSubLevel, final BoundingBox3dc placedBoxBoundingBox, final LevelReusedVectors sink, final Vector3d placedBoxPosition, final Quaterniond placedBoxOrientation) {
        final BoundingBox3d localBase = placedBoxBoundingBox.expand(0.8660254038 - 0.5, new BoundingBox3d());

        if (otherSubLevel != null) {
            localBase.transformInverse(otherSubLevel.logicalPose(), localBase);
        }

        // all blocks
        final Iterable<BlockPos> stream = BlockPos.betweenClosed(Mth.floor(localBase.minX()),
                Mth.floor(localBase.minY()),
                Mth.floor(localBase.minZ()),
                Mth.floor(localBase.maxX()),
                Mth.floor(localBase.maxY()),
                Mth.floor(localBase.maxZ()));

        for (final BlockPos position : stream) {
            final boolean replaced = replaceClicked || this.getLevel().getBlockState(position).canBeReplaced((BlockPlaceContext) (Object) this);

            Vector3d inWorldBoxPosition = new Vector3d(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5);
            final Quaterniond inWorldBoxOrientation = new Quaterniond();

            if (otherSubLevel != null) {
                inWorldBoxPosition = otherSubLevel.logicalPose().transformPosition(inWorldBoxPosition);
                inWorldBoxOrientation.set(otherSubLevel.logicalPose().orientation());
            }

            final OrientedBoundingBox3d inWorldBox = new OrientedBoundingBox3d(
                    inWorldBoxPosition,
                    new Vector3d(1.0, 1.0, 1.0), inWorldBoxOrientation, sink);

            final OrientedBoundingBox3d justPlacedBox = new OrientedBoundingBox3d(placedBoxPosition, new Vector3d(1.0, 1.0, 1.0), placedBoxOrientation, sink);

            if (!replaced && OrientedBoundingBox3d.sat(
                    inWorldBox, justPlacedBox
            ).lengthSquared() > 0.05) {
                cir.setReturnValue(false);
                return true;
            }
        }
        return false;
    }
}
