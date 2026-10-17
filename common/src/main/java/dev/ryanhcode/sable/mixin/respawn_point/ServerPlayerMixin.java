package dev.ryanhcode.sable.mixin.respawn_point;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import dev.ryanhcode.sable.mixinterface.respawn_point.ServerPlayerRespawnExtension;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundFreezePlayerPacket;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin implements ServerPlayerRespawnExtension {

    @Shadow
    @Final
    public MinecraftServer server;
    @Shadow
    public ServerGamePacketListenerImpl connection;
    @Shadow
    private @Nullable BlockPos respawnPosition;
    @Shadow
    private ResourceKey<Level> respawnDimension;
    @Shadow
    private float respawnAngle;
    @Shadow
    private boolean respawnForced;
    @Unique
    @Nullable
    private UUID sable$respawnPoint = null;
    @Unique
    private Pair<UUID, Vector3d> sable$queuedFreeze = null;
    @Unique
    @Nullable
    private UUID sable$respawnPointBefore = null;
    @Unique
    private @Nullable BlockPos sable$respawnPosBefore = null;
    @Unique
    private @Nullable ResourceKey<Level> sable$respawnDimBefore = null;

    @Shadow
    public static Optional<ServerPlayer.RespawnPosAngle> findRespawnAndUseSpawnBlock(final ServerLevel serverLevel, final BlockPos blockPos, final float f, final boolean bl, final boolean bl2) {
        return null;
    }

    @Shadow
    public abstract ServerLevel serverLevel();

    @Shadow
    public abstract void sendSystemMessage(Component component);

    @Override
    public @Nullable UUID sable$getRespawnPoint() {
        return this.sable$respawnPoint;
    }

    @Inject(method = "setRespawnPosition", at = @At("HEAD"), cancellable = true)
    private void sable$setRespawnPosition(final ResourceKey<Level> resourceKey, @Nullable final BlockPos blockPos, final float f, final boolean bl, final boolean sendMessage, final CallbackInfo ci) {
        // Snapshot the state so a call that is ultimately a no-op (e.g. cancelled by another
        // mod's PlayerSetSpawnEvent handler) does not destroy the tracked sub-level point.
        this.sable$respawnPointBefore = this.sable$respawnPoint;
        this.sable$respawnPosBefore = this.respawnPosition;
        this.sable$respawnDimBefore = this.respawnDimension;

        if (blockPos == null) {
            // Spawn is being reset; let vanilla/NeoForge handle it and reconcile at RETURN.
            return;
        }

        final ServerLevel level = this.serverLevel();
        final SubLevel trackingSubLevel = Sable.HELPER.getContaining(level, blockPos);

        if (trackingSubLevel instanceof final ServerSubLevel serverSubLevel) {
            final SubLevelTrackingPointSavedData data = SubLevelTrackingPointSavedData.getOrLoad(level);

            if (this.sable$respawnPoint != null) {
                data.removeTrackingPoint(this.sable$respawnPoint);
                this.sable$respawnPoint = null;
            }

            this.sable$respawnPoint = data.generateTrackingPoint(Vec3.atCenterOf(blockPos), serverSubLevel);

            if (this.sable$respawnPoint != null) {
                final boolean theSame = blockPos.equals(this.respawnPosition) && resourceKey.equals(this.respawnDimension);
                if (sendMessage && !theSame) {
                    this.sendSystemMessage(Component.translatable("block.minecraft.set_spawn"));
                }

                this.respawnPosition = blockPos;
                this.respawnDimension = resourceKey;
                this.respawnAngle = f;
                this.respawnForced = bl;
                ci.cancel();
            }
        }
    }

    @Inject(method = "setRespawnPosition", at = @At("RETURN"))
    private void sable$reconcileRespawnPoint(final ResourceKey<Level> resourceKey, @Nullable final BlockPos blockPos, final float f, final boolean bl, final boolean sendMessage, final CallbackInfo ci) {
        final BlockPos posBefore = this.sable$respawnPosBefore;
        final ResourceKey<Level> dimBefore = this.sable$respawnDimBefore;
        this.sable$respawnPosBefore = null;
        this.sable$respawnDimBefore = null;

        final boolean changed = !Objects.equals(this.respawnPosition, posBefore)
                || !Objects.equals(this.respawnDimension, dimBefore);

        // Only drop the tracked point when this call actually committed a non-sub-level spawn (or
        // a reset), and it is still the same point we entered the method with. A no-op call keeps
        // the point intact; a freshly generated sub-level point is never clobbered.
        if (changed
                && this.sable$respawnPoint != null
                && Objects.equals(this.sable$respawnPoint, this.sable$respawnPointBefore)) {
            SubLevelTrackingPointSavedData.getOrLoad(this.serverLevel())
                    .removeTrackingPoint(this.sable$respawnPoint);
            this.sable$respawnPoint = null;
        }

        this.sable$respawnPointBefore = null;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void sable$addRespawnPoint(final CompoundTag compoundTag, final CallbackInfo ci) {
        if (this.sable$respawnPoint != null) {
            compoundTag.putUUID("RespawnPoint", this.sable$respawnPoint);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void sable$readRespawnPoint(final CompoundTag compoundTag, final CallbackInfo ci) {
        if (compoundTag.hasUUID("RespawnPoint")) {
            this.sable$respawnPoint = compoundTag.getUUID("RespawnPoint");
        }
    }

    /**
     * @author RyanH
     * @reason Respawning on sub-levels
     */
    @Overwrite
    public void copyRespawnPosition(final ServerPlayer serverPlayer) {
        if (serverPlayer.getRespawnPosition() != null) {
            this.sable$respawnPoint = ((ServerPlayerRespawnExtension) serverPlayer).sable$getRespawnPoint();
            this.respawnPosition = serverPlayer.getRespawnPosition();
            this.respawnDimension = serverPlayer.getRespawnDimension();
            this.respawnAngle = serverPlayer.getRespawnAngle();
            this.respawnForced = serverPlayer.isRespawnForced();
        } else {
            this.sable$respawnPoint = null;
            this.respawnPosition = null;
            this.respawnDimension = Level.OVERWORLD;
            this.respawnAngle = 0.0F;
            this.respawnForced = false;
        }
    }

    @Override
    public void sable$takeQueuedFreezeFrom(final ServerPlayer oldPlayer) {
        final ServerPlayerRespawnExtension extension = (ServerPlayerRespawnExtension) oldPlayer;
        final Pair<UUID, Vector3d> queuedFreeze = extension.sable$getQueuedFreeze();

        if (queuedFreeze != null) {
            ((PlayerFreezeExtension) this).sable$freezeTo(queuedFreeze.first(), queuedFreeze.second());
            this.connection.send(new ClientboundCustomPayloadPacket(new ClientboundFreezePlayerPacket(queuedFreeze.first(), queuedFreeze.second())));
        }
    }

    @Override
    public @Nullable Pair<UUID, Vector3d> sable$getQueuedFreeze() {
        return this.sable$queuedFreeze;
    }

    /**
     * @author RyanH
     * @reason Respawning on sub-levels
     */
    @Redirect(method = "findRespawnPositionAndUseSpawnBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;findRespawnAndUseSpawnBlock(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;FZZ)Ljava/util/Optional;"))
    private Optional<ServerPlayer.RespawnPosAngle> sable$findRespawnPosition(final ServerLevel level, final BlockPos blockPos, final float f1, final boolean b1, final boolean b2) {
        final SubLevelTrackingPointSavedData data = SubLevelTrackingPointSavedData.getOrLoad(level);

        if (this.sable$respawnPoint != null) {
            final SubLevelTrackingPointSavedData.TakenLoginPoint point = data.take(this.sable$respawnPoint, false);

            if (point == null) {
                this.sable$respawnPoint = null;
                return Optional.empty();
            }

            // TODO: do validation here

            if (point.subLevelId() != null) {
                this.sable$queuedFreeze = Pair.of(point.subLevelId(), point.localAnchor());
            }

            return Optional.of(new ServerPlayer.RespawnPosAngle(JOMLConversion.toMojang(point.position()), f1));
        }

        return findRespawnAndUseSpawnBlock(level, blockPos, f1, b1, b2);
    }
}
