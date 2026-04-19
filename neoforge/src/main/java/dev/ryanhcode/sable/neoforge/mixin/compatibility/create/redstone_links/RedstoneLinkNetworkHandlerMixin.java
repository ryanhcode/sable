package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.redstone_links;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.redstone_links.SubLevelRedstoneLinkUtility;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Changes the range checks between links in in Create's {@link RedstoneLinkNetworkHandler} to take into account
 * sub-levels
 * TODO: Handle movement when signals are active
 */
@Mixin(RedstoneLinkNetworkHandler.class)
public class RedstoneLinkNetworkHandlerMixin {
    
    @WrapOperation(
        method = "updateNetworkOf",
        at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/redstone/link/RedstoneLinkNetworkHandler;withinRange(Lcom/simibubi/create/content/redstone/link/IRedstoneLinkable;Lcom/simibubi/create/content/redstone/link/IRedstoneLinkable;)Z"),
        remap = false)
    private boolean sable$projectComparisons(final IRedstoneLinkable from, final IRedstoneLinkable to, final Operation<Boolean> original, @Local(argsOnly = true) final LevelAccessor levelAccessor) {
        if (original.call(from, to))
            return true;
        if (levelAccessor instanceof final Level level)
            return SubLevelRedstoneLinkUtility.projectRedstoneLinkComparison(from, to, level);
        return false;
    }
    
}
