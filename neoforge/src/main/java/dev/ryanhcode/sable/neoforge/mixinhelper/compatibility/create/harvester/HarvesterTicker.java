package dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.harvester;

import com.simibubi.create.content.contraptions.actors.harvester.HarvesterMovementBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class HarvesterTicker<T extends BlockEntity & HarvesterLerpedSpeed> implements BlockEntityTicker<T> {

    public static final HarvesterMovementBehaviour blockEntityBehaviour = new HarvesterMovementBehaviour();

    @Override
    public void tick(final Level level, final BlockPos arg2, final BlockState arg3, final T be) {
        if (!be.hasLevel()) {
            be.setLevel(level);
        }

        be.sable$clientTick();
    }

    public static void dropItem(final Level level, final ItemStack itemStack, final BlockPos sable$selfPos) {
        if (sable$selfPos != null) {
            final Vec3 center = sable$selfPos.getCenter();

            final ItemEntity itemEntity = new ItemEntity(level, center.x, center.y, center.z, itemStack);
            level.addFreshEntity(itemEntity);
        }
    }
}