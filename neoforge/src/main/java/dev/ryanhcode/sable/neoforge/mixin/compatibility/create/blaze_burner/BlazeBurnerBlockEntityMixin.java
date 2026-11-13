package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.blaze_burner;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalDoubleRef;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity} to project the player position into sub-levels to drive animations.
 */

@Mixin(BlazeBurnerBlockEntity.class)
public abstract class BlazeBurnerBlockEntityMixin extends SmartBlockEntity {

    @Unique
    private static Vector3d sable$playerPos = new Vector3d();

    public BlazeBurnerBlockEntityMixin(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "tickAnimation", at = @At(value = "INVOKE_ASSIGN", target = "Lnet/minecraft/client/player/LocalPlayer;getZ()D"))
    private void sable$projectPlayerPosition(final CallbackInfo ci, @Local(name = "x") final LocalDoubleRef x, @Local(name = "z") final LocalDoubleRef z, @Local(name = "player") final LocalPlayer player) {
        final SubLevel subLevel = Sable.HELPER.getContaining(this);
        if (subLevel != null) {
            sable$playerPos.set(x.get(), player.getEyeY(), z.get());
            subLevel.logicalPose().transformPositionInverse(sable$playerPos);
            x.set(sable$playerPos.x);
            z.set(sable$playerPos.z);
        }
    }
}
