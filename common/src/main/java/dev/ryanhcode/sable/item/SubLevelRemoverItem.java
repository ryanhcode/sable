package dev.ryanhcode.sable.item;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;

import java.util.UUID;

public class SubLevelRemoverItem extends Item {
    public SubLevelRemoverItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    @Override
    public boolean isFoil(final ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResult useOn(final UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel serverLevel)) return InteractionResult.PASS;

        final BlockPos pos = ctx.getClickedPos().immutable();
        SubLevel subLevel = Sable.HELPER.getContaining(serverLevel, pos);
        if (subLevel == null) return InteractionResult.FAIL;

        final UUID subLevelUUID = subLevel.getUniqueId();
        subLevel.markRemoved();

        if (ctx.getPlayer() != null) ctx.getPlayer().sendSystemMessage(Component.translatable("message.sable.sublevel_removed", subLevelUUID.toString()));

        return InteractionResult.SUCCESS;
    }
}
