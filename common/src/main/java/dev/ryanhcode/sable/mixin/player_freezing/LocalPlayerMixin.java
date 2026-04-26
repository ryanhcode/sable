package dev.ryanhcode.sable.mixin.player_freezing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin extends Player implements PlayerFreezeExtension {

    public LocalPlayerMixin(final Level level, final BlockPos blockPos, final float f, final GameProfile gameProfile) {
        super(level, blockPos, f, gameProfile);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;hasChunkAt(II)Z"))
    private boolean sable$freezeTicking(final Level instance, final int x, final int z, Operation<Boolean> original) {
        this.sable$tickStopFreezing();

        final UUID uuid = this.sable$getFrozenToSubLevel();

        if (uuid != null) {
            final SubLevelContainer container = SubLevelContainer.getContainer(this.level());
            assert container != null;
            final ClientSubLevel subLevel = (ClientSubLevel) container.getSubLevel(uuid);

            if (subLevel == null || !subLevel.isFinalized()) {
                return false;
            }

            this.sable$teleport();
            this.sable$freezeTo(null, null);
        }

        return original.call(instance, x, z);
    }
}
