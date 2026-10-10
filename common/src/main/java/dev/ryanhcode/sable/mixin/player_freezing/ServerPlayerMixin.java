package dev.ryanhcode.sable.mixin.player_freezing;

import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Mixin into {@link net.minecraft.server.level.ServerPlayer} to keep frozen players tracked to their sub-level and teleport them once it becomes available.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends Player implements PlayerFreezeExtension {

    public ServerPlayerMixin(final Level level, final BlockPos blockPos, final float f, final GameProfile gameProfile) {
        super(level, blockPos, f, gameProfile);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void setSable$frozenToSubLevel(final CallbackInfo ci) {
        this.sable$kick();
    }

    @Unique
    private void sable$kick() {
        final UUID uuid = this.sable$getFrozenToSubLevel();
        if (uuid != null) {
            final SubLevelContainer container = SubLevelContainer.getContainer(this.level());
            assert container != null;

            final SubLevel subLevel = container.getSubLevel(uuid);

            if (subLevel != null) {
                ((EntityMovementExtension) this).sable$setTrackingSubLevel(subLevel);
                this.sable$teleport();
                this.sable$freezeTo(null, null);
            }
        }
    }

}
