package dev.ryanhcode.sable.mixinhelpers.entity.entity_collision;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TheFasterEntityCollisionContext extends EntityCollisionContext {

    private final Entity entity;

    public TheFasterEntityCollisionContext(final Entity entity) {
        super(false, 0.0, ItemStack.EMPTY, atack -> false, entity);
        this.entity = entity;
    }

    @Override
    public boolean isHoldingItem(final Item item) {
        return this.entity instanceof final LivingEntity livingEntity && livingEntity.getMainHandItem().is(item);
    }

    @Override
    public boolean canStandOnFluid(final FluidState fluidState, final FluidState fluidState2) {
        return this.entity instanceof final LivingEntity livingEntity && livingEntity.canStandOnFluid(fluidState2) && !fluidState.getType().isSame(fluidState2.getType());
    }

    @Override
    public boolean isDescending() {
        return this.entity.isDescending();
    }

    @Override
    public boolean isAbove(final VoxelShape shape, final BlockPos pos, final boolean bl) {
        return this.entity.getY() > (double) pos.getY() + shape.max(Direction.Axis.Y) - 1.0E-5F;
    }
}
