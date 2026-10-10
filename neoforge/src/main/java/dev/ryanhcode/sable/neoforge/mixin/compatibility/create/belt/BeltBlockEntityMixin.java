package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.belt;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mixin into {@link com.simibubi.create.content.kinetics.belt.BeltBlockEntity} to wake up physics objects when the belt speed changes.
 */

@Mixin(BeltBlockEntity.class)
public abstract class BeltBlockEntityMixin extends KineticBlockEntity {

    public BeltBlockEntityMixin(final BlockEntityType<?> typeIn, final BlockPos pos, final BlockState state) {
        super(typeIn, pos, state);
    }

    @Override
    public void onSpeedChanged(final float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);

        if (container instanceof final ServerSubLevelContainer serverSubLevelContainer) {
            final SubLevelPhysicsSystem physicsSystem = serverSubLevelContainer.physicsSystem();

            final BlockPos blockPos = this.getBlockPos();
            physicsSystem.wakeUpObjectsAt(blockPos.getX(), blockPos.getY(), blockPos.getZ());

            final SubLevel subLevel = Sable.HELPER.getContaining(this);
            if (subLevel instanceof final ServerSubLevel serverSubLevel) {
                physicsSystem.getPipeline().wakeUp(serverSubLevel);
            }
        }
    }
}
