package dev.ryanhcode.sable.mixin.compatibility.waystones;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Waystones stores a waystone's location as the raw block position of its block entity. For a waystone
 * placed on an assembled Sable sub-level (e.g. an aircraft) that position is the hidden plot-yard
 * coordinate (~20.48M blocks out), not the contraption's rendered location.
 *
 * <p>Sable already projects {@link net.minecraft.server.level.ServerPlayer#teleportTo} out of the
 * sub-level, but Waystones' same-dimension warp path teleports via
 * {@code connection.teleport(...)} directly, bypassing that projection. The player is dropped into the
 * plot-yard, which desyncs the sub-level stream ("Received a sub-level movement packet for a
 * non-existent sub-level") and freezes the server thread.
 *
 * <p>Project the teleport target out of the sub-level before the entity is moved. This affects only
 * the entity placement (the warp-plate / modifier block-entity lookups still use the original plot
 * position via {@code destination.location()}).
 * {@link dev.ryanhcode.sable.api.SubLevelHelper#projectOutOfSubLevel} is a no-op for positions that
 * are not inside a sub-level, so this is safe for ordinary warps and idempotent with the
 * cross-dimension {@code teleportTo} path that Sable already handles.
 */
@Mixin(targets = "net.blay09.mods.waystones.core.WaystoneTeleportManager", remap = false)
public class WaystoneTeleportManagerMixin {

    @ModifyVariable(
            method = "teleportEntity(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/entity/Entity;",
            at = @At("HEAD"),
            argsOnly = true,
            index = 2
    )
    private static Vec3 sable$projectTeleportTargetOutOfSubLevel(final Vec3 targetPos3d, @Local(argsOnly = true) final ServerLevel targetWorld) {
        return Sable.HELPER.projectOutOfSubLevel(targetWorld, targetPos3d);
    }
}
