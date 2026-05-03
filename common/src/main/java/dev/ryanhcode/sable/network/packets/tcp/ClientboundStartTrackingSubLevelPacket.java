package dev.ryanhcode.sable.network.packets.tcp;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.network.client.ClientSableInterpolationState;
import dev.ryanhcode.sable.network.client.SubLevelSnapshotInterpolator;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.util.SableBufferUtils;
import foundry.veil.api.network.handler.PacketContext;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record ClientboundStartTrackingSubLevelPacket(long plotCoordinate, UUID subLevelID, Pose3dc lastPose, Pose3d pose,
                                                     BoundingBox3ic bounds, @Nullable String name, int gameTick) implements SableTCPPacket {

    public static final Type<ClientboundStartTrackingSubLevelPacket> TYPE = new CustomPacketPayload.Type<>(Sable.sablePath("start_tracking_sub_level"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundStartTrackingSubLevelPacket> CODEC = StreamCodec.of((buf, value) ->
            value.write(buf), ClientboundStartTrackingSubLevelPacket::read);

    private void write(final FriendlyByteBuf buf) {
        buf.writeLong(this.plotCoordinate);
        buf.writeUUID(this.subLevelID);

        SableBufferUtils.write(buf, this.lastPose);
        SableBufferUtils.write(buf, this.pose);
        SableBufferUtils.write(buf, this.bounds);

        buf.writeBoolean(this.name != null);
        if (this.name != null) {
            buf.writeUtf(this.name);
        }

        buf.writeInt(this.gameTick);
    }

    private static ClientboundStartTrackingSubLevelPacket read(final FriendlyByteBuf buf) {
        return new ClientboundStartTrackingSubLevelPacket(buf.readLong(), buf.readUUID(), SableBufferUtils.read(buf, new Pose3d()), SableBufferUtils.read(buf, new Pose3d()), SableBufferUtils.read(buf, new BoundingBox3i()), buf.readBoolean() ? buf.readUtf() : null, buf.readInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public void handle(final PacketContext context) {
        final Level level = context.level();

        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (!(container instanceof final ClientSubLevelContainer clientContainer)) {
            Sable.LOGGER.error("Received a sub-level tracking packet for a level without a sub-level container");
            return;
        }

        final ClientSubLevel subLevel;
        try {
            subLevel = (ClientSubLevel) clientContainer.allocateSubLevel(
                    this.subLevelID,
                    ChunkPos.getX(this.plotCoordinate),
                    ChunkPos.getZ(this.plotCoordinate),
                    new Pose3d(this.lastPose)
            );
        } catch (IllegalArgumentException e) {
            return;   //Prevents Network Crash
        }

        final SubLevelSnapshotInterpolator interpolator = subLevel.getInterpolator();

        interpolator.receiveSnapshot(this.gameTick - 1, this.lastPose);
        interpolator.receiveSnapshot(this.gameTick, this.pose);

        final ClientSableInterpolationState interpolationState = clientContainer.getInterpolation();

        if (!interpolationState.isStopped()) {
            subLevel.setInitialPosesFrom(interpolationState);
        }

        interpolator.setFirstPoses(this.pose, this.lastPose);

        subLevel.getPlot().setBoundingBox(this.bounds);
        subLevel.forceUpdateBounds();
        // Create the initial render data after
        subLevel.updateRenderData();

        if (this.name != null) {
            subLevel.setName(this.name);
        }
    }
}