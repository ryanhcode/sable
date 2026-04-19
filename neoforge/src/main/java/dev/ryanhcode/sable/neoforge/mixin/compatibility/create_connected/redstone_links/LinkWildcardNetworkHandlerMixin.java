package dev.ryanhcode.sable.neoforge.mixin.compatibility.create_connected.redstone_links;

import com.hlysine.create_connected.content.redstonelinkwildcard.LinkWildcardNetworkHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.redstone_links.SubLevelRedstoneLinkUtility;
import dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create_connected.redstone_links.LevelAttachedRedstoneLinkableNetwork;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Set;
import java.util.function.Consumer;

@Mixin(LinkWildcardNetworkHandler.class)
public class LinkWildcardNetworkHandlerMixin {
    
    @WrapOperation(
        method = "updateNetworkForReceiver",
        at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/redstone/link/RedstoneLinkNetworkHandler;withinRange(Lcom/simibubi/create/content/redstone/link/IRedstoneLinkable;Lcom/simibubi/create/content/redstone/link/IRedstoneLinkable;)Z"),
        remap = false)
    private static boolean sable$projectComparisons(final IRedstoneLinkable from, final IRedstoneLinkable to, final Operation<Boolean> original, @Local(argsOnly = true) final LevelAccessor levelAccessor) {
        if (original.call(from, to))
            return true;
        if (levelAccessor instanceof final Level level)
            return SubLevelRedstoneLinkUtility.projectRedstoneLinkComparison(from, to, level);
        return false;
    }
    
    @WrapOperation(
        method = "lambda$updateNetworkForReceiver$0",
        at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/redstone/link/RedstoneLinkNetworkHandler;withinRange(Lcom/simibubi/create/content/redstone/link/IRedstoneLinkable;Lcom/simibubi/create/content/redstone/link/IRedstoneLinkable;)Z"),
        remap = false)
    private static boolean sable$projectComparisonsInLambda(final IRedstoneLinkable from, final IRedstoneLinkable to, final Operation<Boolean> original, @Local(argsOnly = true) final Set<IRedstoneLinkable> network) {
        if (original.call(from, to))
            return true;
        if (network instanceof final LevelAttachedRedstoneLinkableNetwork levelAttachedNetwork)
            return SubLevelRedstoneLinkUtility.projectRedstoneLinkComparison(from, to, levelAttachedNetwork.getLevel());
        return false;
    }
    
    @WrapOperation(
        method = "updateNetworkForReceiver",
        at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"),
        remap = false)
    private static void sable$attachLevelToNetwork(final Consumer<?> instance, final Object t, final Operation<Void> original, @Local(argsOnly = true) final LevelAccessor levelAccessor) {
        if (levelAccessor instanceof final Level level)
            original.call(instance, new LevelAttachedRedstoneLinkableNetwork(level, (Set<IRedstoneLinkable>) t));
        original.call(instance, t);
    }
    
}
