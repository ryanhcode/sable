package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.redstone_contacts;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.redstone.contact.RedstoneContactBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.redstone_contact.RedstoneContactBlockEntity;
import dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.redstone_contact.RedstoneContactBlockEntityTypeGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Mixin into {@link com.simibubi.create.content.redstone.contact.RedstoneContactBlock} so the redstone contact block uses Sable's custom block entity.
 */

@Mixin(RedstoneContactBlock.class)
public class RedstoneContactBlockMixin extends WrenchableDirectionalBlock implements IBE<RedstoneContactBlockEntity> {

    @Unique
    private static final AllBlockEntityTypes sable$cursed = new AllBlockEntityTypes();

    public RedstoneContactBlockMixin(final Properties properties) {
        super(properties);
    }

    @Override
    public Class<RedstoneContactBlockEntity> getBlockEntityClass() {
        return RedstoneContactBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RedstoneContactBlockEntity> getBlockEntityType() {
        return ((RedstoneContactBlockEntityTypeGetter) sable$cursed).sable$getRedstoneContactType().get();
    }

    @Override
    public <S extends BlockEntity> BlockEntityTicker<S> getTicker(final Level level, final BlockState p_153213_, final BlockEntityType<S> p_153214_) {
        if (!level.isClientSide) {
            return IBE.super.getTicker(level, p_153213_, p_153214_);
        } else {
            return null;
        }
    }
}
