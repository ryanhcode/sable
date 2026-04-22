package dev.ryanhcode.sable.item;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

public class SubLevelAssemblerItem extends Item {
    public SubLevelAssemblerItem() {
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
        SubLevelAssemblyHelper.assembleBlocks(serverLevel, pos, List.of(pos), new BoundingBox3i());

        final String posString = "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";

        if (ctx.getPlayer() != null) ctx.getPlayer().sendSystemMessage(Component.translatable("message.sable.sublevel_assembled", posString));

        return InteractionResult.SUCCESS;
    }

    static String posAsString(BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    static boolean inSameSpace(final ServerLevel serverLevel, final BlockPos pos0, final BlockPos pos1) {
        final SubLevel subLevel0 = Sable.HELPER.getContaining(serverLevel, pos0);
        final SubLevel subLevel1 = Sable.HELPER.getContaining(serverLevel, pos1);

        if (subLevel0 == null && subLevel1 == null) return true;
        if (subLevel1 == null || subLevel0 == null) return false;

        return subLevel0.getUniqueId().equals(subLevel1.getUniqueId());
    }
}
