package dev.ryanhcode.sable.mixin.entity.entities_stick_sublevels.player;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.player.ServerboundMovePlayerPacketExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Mixin into {@link net.minecraft.network.protocol.game.ServerboundMovePlayerPacket} to convert the player's movement packet position into sub-level local coordinates.
 */
@Mixin(ServerboundMovePlayerPacket.class)
public class ServerboundMovePlayerPacketMixin implements ServerboundMovePlayerPacketExtension {

    @Mutable
    @Shadow
    @Final
    protected double x;

    @Mutable
    @Shadow
    @Final
    protected double y;

    @Mutable
    @Shadow
    @Final
    protected double z;

    @Shadow
    @Final
    protected boolean hasPos;

    @Override
    public void sable$handle(final ServerPlayer player) {
        if (!this.hasPos) return;

        final SubLevel subLevel = Sable.HELPER.getContaining(player.level(), this.x, this.z);

        if (subLevel == null) {
            final SubLevelContainer container = SubLevelContainer.getContainer(player.level());

            if (container != null && container.inBounds(BlockPos.containing(this.x, this.y, this.z))) {
                this.x = player.getX();
                this.y = player.getY();
                this.z = player.getZ();
                ((EntityMovementExtension) player).sable$setTrackingSubLevel(null);
                return;
            }
        }

        ((EntityMovementExtension) player).sable$setTrackingSubLevel(subLevel);
        if (subLevel != null) {
            final Vector3d newPos = subLevel.logicalPose().transformPosition(new Vector3d(this.x, this.y, this.z));

            this.x = newPos.x;
            this.y = newPos.y;
            this.z = newPos.z;
        }
    }
}
