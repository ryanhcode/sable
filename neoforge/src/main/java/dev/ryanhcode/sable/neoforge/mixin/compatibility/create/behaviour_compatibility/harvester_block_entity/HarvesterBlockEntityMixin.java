package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.behaviour_compatibility.harvester_block_entity;

import com.simibubi.create.content.contraptions.actors.harvester.HarvesterBlockEntity;
import com.simibubi.create.foundation.blockEntity.CachedRenderBBBlockEntity;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.harvester.HarvesterLerpedSpeed;
import dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.harvester.HarvesterMovementBehaviourExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import static dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.harvester.HarvesterTicker.blockEntityBehaviour;

@Mixin(HarvesterBlockEntity.class)
public abstract class HarvesterBlockEntityMixin extends CachedRenderBBBlockEntity implements HarvesterLerpedSpeed, BlockEntitySubLevelActor {

    @Unique
    private final LerpedFloat sable$lerpedSpeed = LerpedFloat.angular();

    @Unique
    private BlockPos sable$previousPos = BlockPos.ZERO;

    public HarvesterBlockEntityMixin(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void sable$clientTick() {
        final double velocity = Sable.HELPER.getVelocity(this.getLevel(), JOMLConversion.atCenterOf(this.getBlockPos())).length();
        this.sable$lerpedSpeed.chase(this.sable$lerpedSpeed.getValue() + (velocity * 5), 20f, LerpedFloat.Chaser.LINEAR);

        this.sable$lerpedSpeed.tickChaser();
    }

    /**
     * Server tick
     */
    @Override
    public void sable$tick(final ServerSubLevel subLevel) {
        final ActiveSableCompanion helper = Sable.HELPER;
        final Vec3 center = this.getBlockPos().getCenter();
        BlockPos gatheredPos = helper.runIncludingSubLevels(this.getLevel(), center, false, helper.getContaining(this), (sublevel, pos) -> {
            if (blockEntityBehaviour.isValidCrop(this.getLevel(), pos, this.getLevel().getBlockState(pos))) {
                return pos;
            } else {
                return null;
            }
        });

        if (gatheredPos == null) {
            gatheredPos = BlockPos.containing(helper.projectOutOfSubLevel(this.getLevel(), center));
        }

        if (!this.sable$previousPos.equals(gatheredPos)) {
            this.sable$previousPos = gatheredPos;

            final HarvesterMovementBehaviourExtension duck = (HarvesterMovementBehaviourExtension) blockEntityBehaviour;
            duck.sable$setManualLevel(this.getLevel());
            duck.sable$setSelfPos(this.getBlockPos());

            blockEntityBehaviour.visitNewPosition(null, this.sable$previousPos);

            duck.sable$setManualLevel(null);
            duck.sable$setSelfPos(null);
        }
    }

    @Override
    public LerpedFloat sable$getLerpedFloat() {
        return this.sable$lerpedSpeed;
    }
}
