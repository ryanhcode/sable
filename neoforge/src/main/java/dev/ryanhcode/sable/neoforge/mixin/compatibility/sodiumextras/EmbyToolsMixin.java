package dev.ryanhcode.sable.neoforge.mixin.compatibility.sodiumextras;

import dev.ryanhcode.sable.Sable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import toni.sodiumextras.EmbyTools;

@Mixin(EmbyTools.class)
public class EmbyToolsMixin {

    /**
     * @author Ocelot
     * @reason Take into account sub-levels
     */
    @Overwrite
    public static boolean isEntityWithinDistance(final BlockPos bePos, final Vec3 camVec, final int maxHeight, final int maxDistanceSquare) {
        return Sable.HELPER.distanceSquaredWithSubLevels(Minecraft.getInstance().level, bePos.getX() + 0.5, bePos.getY() + 0.5, bePos.getZ() + 0.5, camVec.x, camVec.y, camVec.z) < maxDistanceSquare;
    }
}
