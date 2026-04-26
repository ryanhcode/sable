package dev.ryanhcode.sable.neoforge.mixin.compatibility.flywheel;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractBlockEntityVisual.class)
public class AbstractBlockEntityVisualMixin {

    @WrapOperation(method = "relight(Lnet/minecraft/core/BlockPos;[Ldev/engine_room/flywheel/lib/instance/FlatLit;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I"))
    private int sable$getLightColor(final BlockAndTintGetter blockAndTintGetter, final BlockPos blockPos, Operation<Integer> original) {
        final ClientSubLevelContainer container = SubLevelContainer.getContainer(Minecraft.getInstance().level);
        assert container != null;
        final SubLevel subLevel = Sable.HELPER.getContainingClient(blockPos);

        if (subLevel instanceof final ClientSubLevel clientSubLevel) {
            final int color = original.call(blockAndTintGetter, blockPos);
            return clientSubLevel.scaleLightColor(color);
        }

        return original.call(blockAndTintGetter, blockPos);
    }
}
