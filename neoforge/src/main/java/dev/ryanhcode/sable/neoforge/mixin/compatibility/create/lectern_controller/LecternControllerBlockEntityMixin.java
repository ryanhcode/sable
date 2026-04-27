package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.lectern_controller;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.redstone.link.controller.LecternControllerBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.create.LecternControllerBlockEntityExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(LecternControllerBlockEntity.class)
public abstract class LecternControllerBlockEntityMixin extends SmartBlockEntity implements LecternControllerBlockEntityExtension {

    public LecternControllerBlockEntityMixin(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @Shadow
    protected abstract void stopUsing(Player player);

    @Shadow
    private UUID user;
    @Unique
    private boolean sable$noDrop;

    @Inject(method = "dropController", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"), cancellable = true)
    public void sable$dropController(final BlockState state, final CallbackInfo ci) {
        if (!this.sable$noDrop) {
            return;
        }

        ci.cancel();
        final Entity entity = ((ServerLevel) this.level).getEntity(this.user);
        if (entity instanceof final Player player) {
            this.stopUsing(player);
        }
    }

    @Redirect(method = "playerInRange", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private static double sable$fixDistanceCheck(final Vec3 a, final Vec3 b, @Local(argsOnly = true) final Level level) {
        return Sable.HELPER.distanceSquaredWithSubLevels(level, a, b);
    }

    @Override
    public void sable$setNoDrop() {
        this.sable$noDrop = true;
    }
}
