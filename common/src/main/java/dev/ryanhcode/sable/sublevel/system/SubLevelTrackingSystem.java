package dev.ryanhcode.sable.sublevel.system;

import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.api.sublevel.SubLevelTrackingPlugin;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotDualPacket;
import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotInfoDualPacket;
import dev.ryanhcode.sable.network.packets.tcp.*;
import dev.ryanhcode.sable.network.udp.SableUDPServer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.plot.SubLevelPlayerChunkSender;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import foundry.veil.api.network.VeilPacketManager;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;

import java.util.*;

/**
 * Handles the loading and unloading of {@link SubLevel SubLevels} for {@link ServerPlayer ServerPlayers}.
 */
public class SubLevelTrackingSystem implements SubLevelObserver {
    private final ServerLevel level;
    private final List<SubLevel> additionQueue = new ObjectArrayList<>();
    private final Set<UUID> currentlyUpdatingPlayers = new ObjectOpenHashSet<>();
    private final Set<UUID> pluginNeededPlayers = new ObjectOpenHashSet<>();
    private final List<SubLevelTrackingPlugin> plugins = new ObjectArrayList<>();
    private int interpolationTick;
    private long lastSendMs = -1;

    public SubLevelTrackingSystem(final ServerLevel level) {
        this.level = level;
    }

    private static long getSubLevelLong(final ServerSubLevel subLevel, final SubLevelContainer subLevels) {
        final Vector2i origin = subLevels.getOrigin();
        final ChunkPos plotPos = subLevel.getPlot().plotPos;
        return ChunkPos.asLong(plotPos.x - origin.x, plotPos.z - origin.y);
    }

    private boolean shouldLoad(final Player player, final Vector3dc entityPosition) {
        final double trackingRange = SableConfig.SUB_LEVEL_TRACKING_RANGE.getAsDouble();
        return entityPosition.distanceSquared(player.getX(), player.getY(), player.getZ()) < trackingRange * trackingRange;
    }

    @Override
    public void onSubLevelAdded(final SubLevel subLevel) {
        this.additionQueue.add(subLevel);
    }

    @Override
    public void onSubLevelRemoved(final SubLevel subLevel, final SubLevelRemovalReason reason) {
        this.additionQueue.remove(subLevel);
        final ServerSubLevel serverSubLevel = (ServerSubLevel) subLevel;
        this.sendRemoval(this.serverWidePlayerSink(serverSubLevel), serverSubLevel);
    }

    public VeilPacketManager.PacketSink serverWidePlayerSink(final ServerSubLevel serverSubLevel) {
        return packet -> {
            for (final UUID uuid : serverSubLevel.getTrackingPlayers()) {
                final ServerPlayer player = this.level.getServer().getPlayerList().getPlayer(uuid);

                if (player instanceof ServerPlayer) {
                    player.connection.send(packet);
                }
            }
        };
    }

    private void collectPlayers(final Vector3d position, final Collection<UUID> tracking) {
        for (final ServerPlayer player : this.level.players()) {
            if (this.shouldLoad(player, position)) {
                tracking.add(player.getGameProfile().getId());
            }
        }
    }

    private void sendFullSync(final ServerPlayer player, final ServerSubLevel subLevel, @Nullable final CustomPacketPayload extraPacket) {
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);
        assert container != null;

        final long l = getSubLevelLong(subLevel, container);

        final LevelPlot plot = subLevel.getPlot();

        final Collection<PlotChunkHolder> chunks = plot.getLoadedChunks();
        final ObjectList<Packet<? super ClientGamePacketListener>> packets = new ObjectArrayList<>(3 + chunks.size());

        packets.add(new ClientboundCustomPayloadPacket(new ClientboundStartTrackingSubLevelPacket(l, subLevel.getUniqueId(), subLevel.lastPose(), subLevel.logicalPose(), plot.getBoundingBox(), subLevel.getName(), this.interpolationTick)));

        if (extraPacket != null) {
            packets.add(new ClientboundCustomPayloadPacket(extraPacket));
        }

        for (final PlotChunkHolder chunk : chunks) {
            SubLevelPlayerChunkSender.sendChunk(packets::add, plot.getLightEngine(), chunk.getChunk());
        }

        packets.add(new ClientboundCustomPayloadPacket(new ClientboundFinalizeSubLevelPacket(l)));
        player.connection.send(new ClientboundBundlePacket(packets));

        for (final PlotChunkHolder chunk : chunks) {
            SubLevelPlayerChunkSender.sendChunkPoiData(this.level, chunk.getChunk());
        }
    }

    private void sendRemoval(final VeilPacketManager.PacketSink sink, final ServerSubLevel subLevel) {
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);
        assert container != null;

        final long l = getSubLevelLong(subLevel, container);
        sink.sendPacket(new ClientboundStopTrackingSubLevelPacket(l));
    }

    @Override
    public void tick(final SubLevelContainer container) {
        for (final SubLevel subLevel : this.additionQueue) {
            // If the sub-level has been removed before we could even send it to clients, skip it
            if (subLevel.isRemoved()) {
                continue;
            }

            final ServerSubLevel serverSubLevel = (ServerSubLevel) subLevel;

            final Collection<UUID> tracking = serverSubLevel.getTrackingPlayers();
            final Vector3d position = subLevel.logicalPose().position();

            this.collectPlayers(position, tracking);

            final UUID splitFromSubLevelID = serverSubLevel.getSplitFromSubLevel();
            final SubLevel splitFromSubLevel = splitFromSubLevelID != null ? container.getSubLevel(splitFromSubLevelID) : null;

            for (final UUID uuid : tracking) {
                final ServerPlayer player = (ServerPlayer) this.level.getPlayerByUUID(uuid);

                if (player == null) {
                    throw new IllegalStateException("Player not found immediately after tracking initializes");
                }


                CustomPacketPayload extraPacket = null;

                if (splitFromSubLevelID != null && splitFromSubLevel != null) {
                    extraPacket = new ClientboundRecentlySplitSubLevelPacket(
                            serverSubLevel.getUniqueId(),
                            splitFromSubLevel.getUniqueId(),
                            serverSubLevel.getSplitFromPose()
                    );
                }

                this.sendFullSync(player, serverSubLevel, extraPacket);
            }

            serverSubLevel.clearSplitFrom();
        }
        this.additionQueue.clear();

        for (final SubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) {
                continue;
            }
            final ServerSubLevel serverSubLevel = (ServerSubLevel) subLevel;

            final Collection<UUID> tracking = serverSubLevel.getTrackingPlayers();
            final Vector3dc entityPos = subLevel.logicalPose().position();

            final Iterator<UUID> iter = tracking.iterator();
            while (iter.hasNext()) {
                final UUID uuid = iter.next();

                final ServerPlayer player = (ServerPlayer) this.level.getPlayerByUUID(uuid);

                if (player == null) {
                    // player has been removed
                    final ServerPlayer serverWidePlayer = this.level.getServer().getPlayerList().getPlayer(uuid);

                    if (serverWidePlayer != null) {
                        // they are still online, just not in this world
                        this.sendRemoval(VeilPacketManager.player(serverWidePlayer), serverSubLevel);
                    }

                    iter.remove();
                    continue;
                }

                if (!this.shouldLoad(player, entityPos)) {
                    this.sendRemoval(VeilPacketManager.player(player), serverSubLevel);
                    iter.remove();
                }
            }

            // add players who SHOULD be tracking but aren't
            for (final ServerPlayer player : this.level.players()) {
                final UUID uuid = player.getGameProfile().getId();
                if (this.shouldLoad(player, entityPos) && !tracking.contains(uuid)) {
                    tracking.add(uuid);
                    this.sendFullSync(player, serverSubLevel, null);
                }
            }
        }

        // send positional updates separately
        this.sendBoundsUpdates(container);
        this.sendMovementUpdates(container);
    }

    /**
     * Sends updates regarding sub-level plot bound changes to all tracking players
     *
     * @param container the sublevels to send updates for
     */
    private void sendBoundsUpdates(final SubLevelContainer container) {
        for (final SubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) {
                continue;
            }
            final ServerSubLevel serverSubLevel = (ServerSubLevel) subLevel;

            final BoundingBox3ic plotBounds = serverSubLevel.getPlot().getBoundingBox();
            final BoundingBox3i lastNetworkedBounds = serverSubLevel.lastNetworkedBoundingBox();

            if (!plotBounds.equals(lastNetworkedBounds)) {
                lastNetworkedBounds.set(plotBounds);

                final long l = getSubLevelLong(serverSubLevel, container);
                serverSubLevel.playerSink().sendPacket(new ClientboundChangeBoundsSubLevelPacket(l, plotBounds));
            }
        }
    }

    public int getInterpolationTick() {
        return this.interpolationTick;
    }

    /**
     * Sends updates regarading sub-level movement to all tracking players
     *
     * @param container the sublevels to send updates for
     */
    private void sendMovementUpdates(final SubLevelContainer container) {
        // we want to batch updates we send to players, so we'll collect them here
        final Map<UUID, List<SubLevelUpdateTicket>> movementUpdates = new Object2ObjectOpenHashMap<>();

        for (final SubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) {
                continue;
            }
            final ServerSubLevel serverSubLevel = (ServerSubLevel) subLevel;
            final Collection<UUID> tracking = serverSubLevel.getTrackingPlayers();
            SubLevelUpdateTicket.UpdateTicketType type = SubLevelUpdateTicket.UpdateTicketType.MOVE;

            if (!serverSubLevel.logicalPose().withinTolerance(serverSubLevel.lastNetworkedPose(), 0.015 / 16.0, Math.toRadians(0.015))) {
                serverSubLevel.lastNetworkedPose().set(serverSubLevel.logicalPose());
                serverSubLevel.setLastNetworkedStopped(false);
            } else {
                if (!serverSubLevel.getLastNetworkedStopped()) {
                    type = SubLevelUpdateTicket.UpdateTicketType.STOP;
                    serverSubLevel.setLastNetworkedStopped(true);
                } else {
                    continue;
                }
            }

            for (final UUID uuid : tracking) {
                final ServerPlayer player = (ServerPlayer) this.level.getPlayerByUUID(uuid);

                if (player == null) {
                    continue;
                }

                final List<SubLevelUpdateTicket> playerUpdates = movementUpdates.computeIfAbsent(uuid, (p) -> new ArrayList<>());
                playerUpdates.add(new SubLevelUpdateTicket(serverSubLevel, type));
            }
        }

        final long ms = System.currentTimeMillis();
        final int msSinceLastSend;
        if (this.lastSendMs == -1) {
            msSinceLastSend = (int) (1000.0 / this.level.getServer().tickRateManager().tickrate());
        } else {
            msSinceLastSend = (int) (ms - this.lastSendMs);
        }
        this.lastSendMs = ms;

        this.pluginNeededPlayers.clear();
        for (final SubLevelTrackingPlugin plugin : this.plugins) {
            for (final UUID neededPlayer : plugin.neededPlayers()) {
                this.pluginNeededPlayers.add(neededPlayer);
            }
        }

        this.currentlyUpdatingPlayers.addAll(movementUpdates.keySet());
        this.currentlyUpdatingPlayers.addAll(this.pluginNeededPlayers);

        final Iterator<UUID> currentlyUpdatingIter = this.currentlyUpdatingPlayers.iterator();
        while (currentlyUpdatingIter.hasNext()) {
            final UUID uuid = currentlyUpdatingIter.next();

            final ServerPlayer player = (ServerPlayer) this.level.getPlayerByUUID(uuid);
            if (player == null) {
                currentlyUpdatingIter.remove();
                continue;
            }

            if (!movementUpdates.containsKey(uuid)) {
                if (this.pluginNeededPlayers.contains(uuid)) {
                    player.connection.send(new ClientboundCustomPayloadPacket(new ClientboundSableSnapshotInfoDualPacket(msSinceLastSend, this.interpolationTick, false)));
                    continue;
                }

                player.connection.send(new ClientboundCustomPayloadPacket(new ClientboundSableSnapshotInfoDualPacket(msSinceLastSend, this.interpolationTick, true)));

                currentlyUpdatingIter.remove();
            }
        }

        for (final SubLevelTrackingPlugin plugin : this.plugins) {
            plugin.sendTrackingData(this.interpolationTick);
        }

        for (final Map.Entry<UUID, List<SubLevelUpdateTicket>> entry : movementUpdates.entrySet()) {
            final UUID uuid = entry.getKey();
            final ServerPlayer player = (ServerPlayer) this.level.getPlayerByUUID(uuid);

            final List<SubLevelUpdateTicket> toUpdate = entry.getValue();
            final List<ClientboundSableSnapshotDualPacket.Entry> entries = new ObjectArrayList<>();

            for (final SubLevelUpdateTicket ticket : toUpdate) {
                final ServerSubLevel serverSubLevel = (ServerSubLevel) ticket.subLevels;
                final long l = getSubLevelLong(serverSubLevel, container);

                switch (ticket.type) {
                    case STOP -> player.connection.send(new ClientboundCustomPayloadPacket(new ClientboundStopMovingSubLevelPacket(l)));
                    case MOVE -> {
                        final Vector3f linearVelocity = new Vector3f((float) serverSubLevel.latestLinearVelocity.x, (float) serverSubLevel.latestLinearVelocity.y, (float) serverSubLevel.latestLinearVelocity.z);
                        final Vector3f angularVelocity = new Vector3f((float) serverSubLevel.latestAngularVelocity.x, (float) serverSubLevel.latestAngularVelocity.y, (float) serverSubLevel.latestAngularVelocity.z);
                        entries.add(new ClientboundSableSnapshotDualPacket.Entry(l, serverSubLevel.logicalPose(), linearVelocity, angularVelocity));
                    }
                }
            }

            final int maxBatchSize = 16;

            final SableUDPServer udpServer = SableUDPServer.getServer(this.level.getServer());
            if (udpServer != null && udpServer.isConnectedTo(player)) {
                final Iterator<ClientboundSableSnapshotDualPacket.Entry> iter = entries.iterator();

                udpServer.sendUDPPacket(player, new ClientboundSableSnapshotInfoDualPacket(msSinceLastSend, this.interpolationTick, false), true);
                while (iter.hasNext()) {
                    final List<ClientboundSableSnapshotDualPacket.Entry> batch = new ObjectArrayList<>();

                    for (int i = 0; i < maxBatchSize && iter.hasNext(); i++) {
                        batch.add(iter.next());
                    }

                    udpServer.sendUDPPacket(player, new ClientboundSableSnapshotDualPacket(this.interpolationTick, batch), true);
                }
            } else {
                // We have to fallback to TCP, unfortunately...
                final Iterator<ClientboundSableSnapshotDualPacket.Entry> iter = entries.iterator();

                while (iter.hasNext()) {
                    final List<ClientboundSableSnapshotDualPacket.Entry> batch = new ObjectArrayList<>();

                    for (int i = 0; i < maxBatchSize && iter.hasNext(); i++) {
                        batch.add(iter.next());
                    }

                    player.connection.send(
                            new ClientboundBundlePacket(List.of(
                                    new ClientboundCustomPayloadPacket(new ClientboundSableSnapshotInfoDualPacket(msSinceLastSend, this.interpolationTick, false)),
                                    new ClientboundCustomPayloadPacket(new ClientboundSableSnapshotDualPacket(this.interpolationTick, batch))
                            )));
                }
            }
        }

        this.interpolationTick++;
    }

    /**
     * Other mods or projects (looking at you, Simulated!) may want to piggyback off of the snapshot interpolation
     * system so that their content can also abide by it and benefit from its improvements. As such, we expose
     * "tracking" plugins for these projects to give us players that need to be informed about the interpolation tick
     * at any given moment.
     */
    public void addTrackingPlugin(final SubLevelTrackingPlugin plugin) {
        if (this.plugins.contains(plugin)) {
            return;
        }
        this.plugins.add(plugin);
    }

    private record SubLevelUpdateTicket(SubLevel subLevels, UpdateTicketType type) {
        private enum UpdateTicketType {
            STOP,
            MOVE
        }
    }
}
