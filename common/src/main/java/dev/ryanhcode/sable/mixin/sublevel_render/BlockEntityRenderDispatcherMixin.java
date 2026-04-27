package dev.ryanhcode.sable.mixin.sublevel_render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.BlockEntityRenderDispatcherExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin implements BlockEntityRenderDispatcherExtension {

    @Unique
    private Vec3 sable$cameraPos;

    @WrapOperation(method = "setupAndRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I"))
    private static int sable$getLightColor(final BlockAndTintGetter blockAndTintGetter, final BlockPos blockPos, Operation<Integer> original) {
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(blockPos);

        final int existingColor = original.call(blockAndTintGetter, blockPos);
        return subLevel != null ? subLevel.scaleLightColor(existingColor) : existingColor;
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;shouldRender(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/phys/Vec3;)Z"), index = 1)
    public Vec3 sable$moveCameraPosForCheck(final Vec3 pCameraPos) {
        return this.sable$cameraPos != null ? this.sable$cameraPos : pCameraPos;
    }

    @Override
    public void sable$setCameraPosition(@Nullable final Vec3 pos) {
        this.sable$cameraPos = pos;
    }
}
