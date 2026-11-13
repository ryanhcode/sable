package dev.ryanhcode.sable.mixin.punching;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Invoker exposing the static private getPlayerPOVHitResult method of {@link net.minecraft.world.item.Item}.
 */
@Mixin(Item.class)
public interface ItemInvoker {
    @Invoker("getPlayerPOVHitResult")
    static BlockHitResult sable$getPlayerPOVHitResult(final Level level, final Player player, final ClipContext.Fluid fluid) {
        throw new AssertionError();
    }
}
