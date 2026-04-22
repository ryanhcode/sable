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
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static dev.ryanhcode.sable.item.SubLevelAssemblerItem.posAsString;
import static dev.ryanhcode.sable.item.SubLevelAssemblerItem.inSameSpace;

// very nice item name i know
public class SingleBlockSubLevelAreaAssemblerItem extends Item {
    public SingleBlockSubLevelAreaAssemblerItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    private BlockPos pos;

    @Override
    public boolean isFoil(final @NotNull ItemStack stack) {
        return true;
    }

    @Override
    public @NotNull InteractionResult useOn(final UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof final ServerLevel serverLevel)) return InteractionResult.PASS;

        final BlockPos clickedPos = ctx.getClickedPos().immutable();

        if (this.pos == null) this.pos = clickedPos;
        else {
            if (!inSameSpace(serverLevel, this.pos, clickedPos)) return InteractionResult.FAIL;

            int assembled = 0;

            for (final BlockPos p : BlockPos.betweenClosed(this.pos, clickedPos)) {
                if (serverLevel.getBlockState(p).isAir()) continue;

                SubLevelAssemblyHelper.assembleBlocks(serverLevel, p, List.of(p), new BoundingBox3i());

                assembled++;
            }

            if (ctx.getPlayer() != null) ctx.getPlayer().sendSystemMessage(Component.translatable("message.sable.single_block_sublevel_area_assembled", posAsString(this.pos), posAsString(clickedPos), String.valueOf(assembled)));

            this.pos = null;
        }

        return InteractionResult.SUCCESS;
    }
}
