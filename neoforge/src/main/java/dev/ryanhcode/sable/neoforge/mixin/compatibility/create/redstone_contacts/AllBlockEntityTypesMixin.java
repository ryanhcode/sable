package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.redstone_contacts;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.redstone_contact.RedstoneContactBlockEntity;
import dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.redstone_contact.RedstoneContactBlockEntityTypeGetter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * Mixin into {@link com.simibubi.create.AllBlockEntityTypes} to register the redstone contact block entity type used by Sable.
 */

@Mixin(AllBlockEntityTypes.class)
public class AllBlockEntityTypesMixin implements RedstoneContactBlockEntityTypeGetter {

    @Shadow @Final private static CreateRegistrate REGISTRATE;
    @Unique
    private static final BlockEntityEntry<RedstoneContactBlockEntity> REDSTONE_CONTACT = REGISTRATE
            .blockEntity("redstone_contact", RedstoneContactBlockEntity::new)
            .validBlock(AllBlocks.REDSTONE_CONTACT)
            .register();


    @Override
    public BlockEntityEntry<RedstoneContactBlockEntity> sable$getRedstoneContactType() {
        return REDSTONE_CONTACT;
    }
}
