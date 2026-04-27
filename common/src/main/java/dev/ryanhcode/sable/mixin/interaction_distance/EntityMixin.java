package dev.ryanhcode.sable.mixin.interaction_distance;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Changes distance checks in entities to take into account sublevels
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Shadow
    public abstract Vec3 position();

    @Shadow
    public abstract Level level();

    /**
     * @author RyanH
     * @reason Overwrite to make distance checks take into account sublevels
     */
    @Overwrite
    public float distanceTo(final Entity entity) {
        final Level level = this.level();
        final double distanceSquared = Sable.HELPER.distanceSquaredWithSubLevels(level, this.position(), entity.position());

        return (float) Math.sqrt(distanceSquared);
    }

    /**
     * @author RyanH
     * @reason Overwrite to make distance checks take into account sublevels
     */
    @Overwrite
    public double distanceToSqr(final double x, final double y, final double z) {
        final Level level = this.level();

        return Sable.HELPER.distanceSquaredWithSubLevels(level, this.position(), x, y, z);
    }

    /**
     * @author RyanH
     * @reason Overwrite to make distance checks take into account sublevels
     */
    @Overwrite
    public double distanceToSqr(final Vec3 pos) {
        final Level level = this.level();

        return Sable.HELPER.distanceSquaredWithSubLevels(level, this.position(), pos);
    }

}
