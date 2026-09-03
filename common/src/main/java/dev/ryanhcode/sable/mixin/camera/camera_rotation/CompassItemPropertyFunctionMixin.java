package dev.ryanhcode.sable.mixin.camera.camera_rotation;

import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Make the compass target angle calculation take sub-level transforms into account.
 */
@Mixin(CompassItemPropertyFunction.class)
public abstract class CompassItemPropertyFunctionMixin {

    /**
     * @author RyanH
     * @reason Take into account sub-levels
     */
    @Overwrite
    private double getAngleFromEntityToPos(final Entity entity, final BlockPos pos) {
        Vec3 localPos = Vec3.atCenterOf(pos);
        double entityX = entity.getX();
        double entityZ = entity.getZ();

        final ActiveSableCompanion helper = Sable.HELPER;
        SubLevel subLevel = helper.getContaining(entity);

        if (subLevel == null) {
            final Entity vehicle = entity.getVehicle();

            if (vehicle != null) {
                subLevel = helper.getContaining(vehicle);

                if (subLevel != null) {
                    final Vec3 localEntityPos = subLevel.lastPose().transformPositionInverse(entity.position());
                    entityX = localEntityPos.x;
                    entityZ = localEntityPos.z;
                }
            }
        }

        if (subLevel != null) {
            localPos = subLevel.lastPose().transformPositionInverse(localPos);
        }

        return Math.atan2(localPos.z() - entityZ, localPos.x() - entityX) / (float) (Math.PI * 2);
    }

}
