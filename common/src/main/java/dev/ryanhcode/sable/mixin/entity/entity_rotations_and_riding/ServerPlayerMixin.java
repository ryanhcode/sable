package dev.ryanhcode.sable.mixin.entity.entity_rotations_and_riding;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Set;

/**
 * Mixin into {@link net.minecraft.server.level.ServerPlayer} to adjust the position of sent teleport packets when riding a sub-level vehicle.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends Player {

    @Shadow public ServerGamePacketListenerImpl connection;

    public ServerPlayerMixin(final Level level, final BlockPos blockPos, final float f, final GameProfile gameProfile) {
        super(level, blockPos, f, gameProfile);
    }

    @WrapOperation(method = "startRiding", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;teleport(DDDFF)V"))
    private void sable$adjustTeleportPacket(final ServerGamePacketListenerImpl instance, final double x, final double y, final double z, final float rot1, final float rot2, final Operation<Void> original) {
        final Entity vehicle = this.getVehicle();

        if (vehicle == null) {
            original.call(instance, x, y, z, rot1, rot2);
            return;
        }

        final SubLevel containingSubLevel = Sable.HELPER.getContaining(vehicle);

        if (containingSubLevel == null) {
            original.call(instance, x, y, z, rot1, rot2);
            return;
        }

        this.absMoveTo(x, y, z, rot1, rot2);
        final Vec3 pos = containingSubLevel.logicalPose().transformPositionInverse(this.position());
        this.connection.send(new ClientboundPlayerPositionPacket(pos.x, pos.y, pos.z, rot1, rot2, Set.of(), -1));
    }
}
