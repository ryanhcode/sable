package dev.ryanhcode.sable.mixin.compatibility.waystones;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundFreezePlayerPacket;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Lets Waystones warp onto sub-levels. A waystone on a sub-level is stored at its plot-yard
 * coordinate, so the target is projected out to the contraption's real (or last-known) position -
 * Waystones' same-dimension warp uses {@code connection.teleport} directly and would otherwise drop
 * the player into the plot-yard. If the contraption is unloaded, the player is frozen to it after the
 * teleport so they land on the deck once it re-activates, as bed respawns do.
 */
@Mixin(targets = "net.blay09.mods.waystones.core.WaystoneTeleportManager", remap = false)
public class WaystoneTeleportManagerMixin {

    private static final String TELEPORT_ENTITY = "teleportEntity(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/entity/Entity;";

    @ModifyVariable(method = TELEPORT_ENTITY, at = @At("HEAD"), argsOnly = true, index = 2)
    private static Vec3 sable$projectTeleportTarget(final Vec3 targetPos3d, @Local(argsOnly = true) final ServerLevel targetWorld, @Share("sableHeldFreeze") final LocalRef<Pair<UUID, Vector3d>> freezeRef) {
        // On an unloaded sub-level, remember it (and the local anchor) to freeze the player afterwards.
        final UUID heldUuid = sable$heldSubLevelUuid(targetWorld, targetPos3d);
        if (heldUuid != null) {
            freezeRef.set(Pair.of(heldUuid, new Vector3d(targetPos3d.x, targetPos3d.y, targetPos3d.z)));
        }

        return Sable.HELPER.projectOutOfSubLevel(targetWorld, targetPos3d);
    }

    @Inject(method = TELEPORT_ENTITY, at = @At("RETURN"))
    private static void sable$freezeOntoSubLevel(final Entity entity, final ServerLevel targetWorld, final Vec3 targetPos3d, final Direction direction, final CallbackInfoReturnable<Entity> cir, @Share("sableHeldFreeze") final LocalRef<Pair<UUID, Vector3d>> freezeRef) {
        final Pair<UUID, Vector3d> freeze = freezeRef.get();
        if (freeze == null) {
            return;
        }

        if (cir.getReturnValue() instanceof final ServerPlayer player) {
            ((PlayerFreezeExtension) player).sable$freezeTo(freeze.first(), freeze.second());
            player.connection.send(new ClientboundCustomPayloadPacket(new ClientboundFreezePlayerPacket(freeze.first(), freeze.second())));
        }
    }

    /**
     * @return the UUID of the held (unloaded) sub-level at the target, or {@code null} if it is open world or already loaded
     */
    private static @Nullable UUID sable$heldSubLevelUuid(final ServerLevel level, final Vec3 pos) {
        if (Sable.HELPER.getContaining(level, pos.x, pos.z) != null) {
            return null;
        }
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        final int chunkX = Mth.floor(pos.x) >> SectionPos.SECTION_BITS;
        final int chunkZ = Mth.floor(pos.z) >> SectionPos.SECTION_BITS;
        return container.getLastKnownUuid(chunkX, chunkZ);
    }
}
