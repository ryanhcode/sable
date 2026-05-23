package dev.ryanhcode.sable.mixin.portal;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

@Mixin(NetherPortalBlock.class)
public class NetherPortalBlockMixin {

    @Redirect(method = "getPortalDestination", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/border/WorldBorder;clampToBounds(DDD)Lnet/minecraft/core/BlockPos;"))
    private BlockPos sable$getPortalDestination(final WorldBorder instance,
                                                final double x,
                                                final double y,
                                                final double z,
                                                @Local(argsOnly = true) final Entity entity,
                                                @Local(ordinal = 0) final double multiplier) {
        final Vec3 position = new Vec3(entity.getX(), entity.getY(), entity.getZ());

        final Vec3 globalPos = Sable.HELPER.projectOutOfSubLevel(entity.level(), position);

        return instance.clampToBounds(
                globalPos.x * multiplier,
                globalPos.y,
                globalPos.z * multiplier
        );
    }

    @Redirect(method = "createDimensionTransition", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/portal/PortalShape;findCollisionFreePosition(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/EntityDimensions;)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 sable$createDimensionTransition(final Vec3 pos, final ServerLevel level, final Entity entity, final EntityDimensions dimensions) {
        final Vec3 projectedPos = Sable.HELPER.projectOutOfSubLevel(level, pos);
        return PortalShape.findCollisionFreePosition(projectedPos, level, entity, dimensions);
    }

    @ModifyVariable(method = "getExitPortal", at = @At("STORE"), ordinal = 0)
    private DimensionTransition.PostDimensionTransition sable$getExitPortal(final DimensionTransition.PostDimensionTransition value, @Local(name = "level") final ServerLevel level, @Local(name = "optional") final Optional<BlockPos> optional) {
        if (optional.isEmpty()) return value;
        return DimensionTransition.PLAY_PORTAL_SOUND.then((p_351967_) -> p_351967_.placePortalTicket(BlockPos.containing(Sable.HELPER.projectOutOfSubLevel(level, optional.get().getCenter()))));
    }
}
