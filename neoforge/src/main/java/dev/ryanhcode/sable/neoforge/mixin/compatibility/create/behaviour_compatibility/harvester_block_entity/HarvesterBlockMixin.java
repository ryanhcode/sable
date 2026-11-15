package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.behaviour_compatibility.harvester_block_entity;

import com.simibubi.create.content.contraptions.actors.AttachedActorBlock;
import com.simibubi.create.content.contraptions.actors.harvester.HarvesterBlock;
import com.simibubi.create.content.contraptions.actors.harvester.HarvesterBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.harvester.HarvesterTicker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mixin into {@link com.simibubi.create.content.contraptions.actors.harvester.HarvesterBlock} to provide a custom client ticker for the harvester.
 */

@Mixin(HarvesterBlock.class)
public abstract class HarvesterBlockMixin extends AttachedActorBlock implements IBE<HarvesterBlockEntity> {
    protected HarvesterBlockMixin(final Properties properties) {
        super(properties);
    }

    @Override
    public <S extends BlockEntity> BlockEntityTicker<S> getTicker(final Level level, final BlockState state, final BlockEntityType<S> blockEntityType) {
        if (level.isClientSide) {
            return new HarvesterTicker();
        } else {
            return null;
        }
    }
}
