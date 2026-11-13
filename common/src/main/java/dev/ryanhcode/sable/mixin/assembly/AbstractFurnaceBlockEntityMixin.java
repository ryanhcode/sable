package dev.ryanhcode.sable.mixin.assembly;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Mixin into {@link net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity} to also clear the recipes used map when the container is cleared.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin extends BaseContainerBlockEntity implements Clearable {

    @Shadow @Final private Object2IntOpenHashMap<ResourceLocation> recipesUsed;

    protected AbstractFurnaceBlockEntityMixin(final BlockEntityType<?> blockEntityType, final BlockPos blockPos, final BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.recipesUsed.clear();
    }

}
