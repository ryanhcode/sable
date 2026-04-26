package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.crushing_wheel;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelBlock;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.EntityExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CrushingWheelBlock.class)
public abstract class CrushingWheelBlockMixin extends RotatedPillarKineticBlock implements IBE<CrushingWheelBlockEntity> {

    public CrushingWheelBlockMixin(final Properties arg) {
        super(arg);
    }

    private SubLevel entityInsideSubLevel = null;

   @WrapMethod(method = "entityInside")
    private void handleEntityInside(BlockState state, Level worldIn, BlockPos pos, Entity entityIn, Operation<Void> original) {
        try {
            entityInsideSubLevel = Sable.HELPER.getContaining(worldIn, pos);
            if (entityInsideSubLevel != null) {
                ((EntityExtension) entityIn).sable$withPos(entityInsideSubLevel.logicalPose().transformPositionInverse(entityIn.position()),
                        () -> original.call(state, worldIn, pos, entityIn));
            } else {
                original.call(state, worldIn, pos, entityIn);
            }
        } finally {
            entityInsideSubLevel = null;
        }
    }

    @WrapOperation(method = "entityInside", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;add(DDD)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 a(Vec3 instance, double x, double y, double z, Operation<Vec3> original) {
        if (entityInsideSubLevel == null)
            return original.call(instance, x, y, z);

        Vec3 impulse = new Vec3(x, 0, z);
        impulse = entityInsideSubLevel.logicalPose().transformNormal(impulse);
        return instance.add(impulse);
    };
}
