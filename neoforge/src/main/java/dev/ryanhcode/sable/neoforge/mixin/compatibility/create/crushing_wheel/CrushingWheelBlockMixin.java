package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.crushing_wheel;

import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelBlock;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Mixin into {@link com.simibubi.create.content.kinetics.crusher.CrushingWheelBlock} so crushing wheels correctly push entities in sub-levels.
 */

@Mixin(CrushingWheelBlock.class)
public abstract class CrushingWheelBlockMixin extends RotatedPillarKineticBlock implements IBE<CrushingWheelBlockEntity> {

    public CrushingWheelBlockMixin(final Properties arg) {
        super(arg);
    }

    /**
     * @author RyanH
     * @reason Take into account sub-levels existing
     */
    @Overwrite
    public void entityInside(final BlockState state, final Level level, final BlockPos pos, final Entity entityIn) {
        final SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        Vec3 entityPos = entityIn.position();
        if (subLevel != null) {
            entityPos = subLevel.logicalPose().transformPositionInverse(entityPos);
        }

        if (entityPos.y() < pos.getY() + 1.25f || !entityIn.onGround())
            return;

        final float speed = this.getBlockEntityOptional(level, pos).map(CrushingWheelBlockEntity::getSpeed)
                .orElse(0f);

        double x = 0;
        double z = 0;

        final double entityX = entityPos.x();
        final double entityZ = entityPos.z();

        if (state.getValue(AXIS) == Direction.Axis.X) {
            z = speed / 20f;
            x += (pos.getX() + .5f - entityX) * .1f;
        }

        if (state.getValue(AXIS) == Direction.Axis.Z) {
            x = speed / -20f;
            z += (pos.getZ() + .5f - entityZ) * .1f;
        }

        Vec3 impulse = new Vec3(x, 0, z);
        if (subLevel != null) {
            impulse = subLevel.logicalPose().transformNormal(impulse);
        }

        entityIn.setDeltaMovement(entityIn.getDeltaMovement().add(impulse));
    }

}
