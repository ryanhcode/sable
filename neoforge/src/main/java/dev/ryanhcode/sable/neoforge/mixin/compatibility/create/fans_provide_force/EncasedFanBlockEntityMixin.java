package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.fans_provide_force;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlock;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import dev.ryanhcode.sable.api.block.propeller.BlockEntityPropeller;
import dev.ryanhcode.sable.api.block.propeller.BlockEntitySubLevelPropellerActor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Mixin into {@link com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity} to act as a propeller providing airflow and thrust in sub-levels.
 */

@Mixin(EncasedFanBlockEntity.class)
public class EncasedFanBlockEntityMixin extends KineticBlockEntity implements BlockEntitySubLevelPropellerActor, BlockEntityPropeller {

    @Unique
    private boolean sable$blocked;

    public EncasedFanBlockEntityMixin(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void sable$tick(final ServerSubLevel subLevel) {
        final BlockPos frontPos = this.getBlockPos().relative(this.getBlockState().getValue(EncasedFanBlock.FACING));
        this.sable$blocked = !this.level.getBlockState(frontPos).isAir();
    }

    @Override
    public BlockEntityPropeller getPropeller() {
        return this;
    }

    @Override
    public Direction getBlockDirection() {
        return this.getBlockState().getValue(EncasedFanBlock.FACING);
    }

    protected float sable$getPropSpeed() {
        final float rotationSpeed = convertToAngular(this.getSpeed());
        return this.getBlockDirection().getAxisDirection().getStep() * rotationSpeed * (10 / 3);
    }

    @Override
    public double getAirflow() {
        return 0.1f * this.sable$getPropSpeed();
    }

    @Override
    public double getThrust() {
        return 0.3f * this.sable$getPropSpeed();
    }

    @Override
    public boolean isActive() {
        return !this.sable$blocked && Math.abs(this.sable$getPropSpeed()) > 0.01f;
    }
}
