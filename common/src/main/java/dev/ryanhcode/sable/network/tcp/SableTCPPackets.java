package dev.ryanhcode.sable.network.tcp;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotDualPacket;
import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotInfoDualPacket;
import dev.ryanhcode.sable.network.packets.tcp.*;
import foundry.veil.api.network.VeilPacketManager;

public class SableTCPPackets {

    private static final VeilPacketManager PACKET_MANAGER = VeilPacketManager.create(Sable.MOD_ID, "1");

    public static void init() {
        PACKET_MANAGER.registerClientbound(ClientboundSableSnapshotDualPacket.TYPE, ClientboundSableSnapshotDualPacket.CODEC, ClientboundSableSnapshotDualPacket::handle);
        PACKET_MANAGER.registerClientbound(ClientboundSableSnapshotInfoDualPacket.TYPE, ClientboundSableSnapshotInfoDualPacket.CODEC, ClientboundSableSnapshotInfoDualPacket::handle);
        PACKET_MANAGER.registerClientbound(ClientboundStopMovingSubLevelPacket.TYPE, ClientboundStopMovingSubLevelPacket.CODEC, ClientboundStopMovingSubLevelPacket::handle);

        PACKET_MANAGER.registerClientbound(ClientboundChangeSubLevelNamePacket.TYPE, ClientboundChangeSubLevelNamePacket.CODEC, ClientboundChangeSubLevelNamePacket::handle);

        PACKET_MANAGER.registerClientbound(ClientboundStartTrackingSubLevelPacket.TYPE, ClientboundStartTrackingSubLevelPacket.CODEC, ClientboundStartTrackingSubLevelPacket::handle);
        PACKET_MANAGER.registerClientbound(ClientboundFinalizeSubLevelPacket.TYPE, ClientboundFinalizeSubLevelPacket.CODEC, ClientboundFinalizeSubLevelPacket::handle);
        PACKET_MANAGER.registerClientbound(ClientboundStopTrackingSubLevelPacket.TYPE, ClientboundStopTrackingSubLevelPacket.CODEC, ClientboundStopTrackingSubLevelPacket::handle);
        PACKET_MANAGER.registerClientbound(ClientboundChangeBoundsSubLevelPacket.TYPE, ClientboundChangeBoundsSubLevelPacket.CODEC, ClientboundChangeBoundsSubLevelPacket::handle);

        PACKET_MANAGER.registerClientbound(ClientboundFreezePlayerPacket.TYPE, ClientboundFreezePlayerPacket.CODEC, ClientboundFreezePlayerPacket::handle);

        PACKET_MANAGER.registerClientbound(ClientboundPhysicsPropertyPacket.TYPE, ClientboundPhysicsPropertyPacket.CODEC, ClientboundPhysicsPropertyPacket::handle);
        PACKET_MANAGER.registerClientbound(ClientboundFloatingBlockMaterialPacket.TYPE, ClientboundFloatingBlockMaterialPacket.CODEC, ClientboundFloatingBlockMaterialPacket::handle);
        PACKET_MANAGER.registerClientbound(ClientboundDimensionPhysicsPacket.TYPE, ClientboundDimensionPhysicsPacket.CODEC, ClientboundDimensionPhysicsPacket::handle);

        PACKET_MANAGER.registerClientbound(ClientboundRecentlySplitSubLevelPacket.TYPE, ClientboundRecentlySplitSubLevelPacket.CODEC, ClientboundRecentlySplitSubLevelPacket::handle);

        PACKET_MANAGER.registerClientbound(ClientboundSableUDPActivationPacket.TYPE, ClientboundSableUDPActivationPacket.CODEC, ClientboundSableUDPActivationPacket::handle);

        PACKET_MANAGER.registerClientbound(ClientboundEnterGizmoPacket.TYPE, ClientboundEnterGizmoPacket.CODEC, ClientboundEnterGizmoPacket::handle);

        PACKET_MANAGER.registerServerbound(ServerboundPunchSubLevelPacket.TYPE, ServerboundPunchSubLevelPacket.CODEC, ServerboundPunchSubLevelPacket::handle);
        PACKET_MANAGER.registerServerbound(ServerboundGizmoMoveSubLevelPacket.TYPE, ServerboundGizmoMoveSubLevelPacket.CODEC, ServerboundGizmoMoveSubLevelPacket::handle);
    }
}
