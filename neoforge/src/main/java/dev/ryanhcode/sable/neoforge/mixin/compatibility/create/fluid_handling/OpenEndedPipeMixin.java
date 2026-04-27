package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.fluid_handling;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.fluids.OpenEndedPipe;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OpenEndedPipe.class)
public abstract class OpenEndedPipeMixin {
    @Shadow
    private BlockPos outputPos;

    @Shadow
    private Level world;

    @Shadow
    public abstract BlockPos getPos();

    @Unique
    private BlockPos sable$plotOutputPos;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void sable$saveCurrentPos(final BlockFace face, final CallbackInfo ci) {
        this.sable$plotOutputPos = this.outputPos;
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState sable$getBlockstateInclSublevels(final Level level, final BlockPos pos, Operation<BlockState> original) {
        this.outputPos = this.sable$plotOutputPos;

        final ActiveSableCompanion helper = Sable.HELPER;
        final Vec3 checkPos = Vec3.atCenterOf(this.sable$plotOutputPos);
        BlockState gatheredState = helper.runIncludingSubLevels(level, checkPos, true, helper.getContaining(level, checkPos), this::sable$gatherState);
        if (gatheredState == null) {
            this.outputPos = this.sable$plotOutputPos;
            gatheredState = original.call(level, this.sable$plotOutputPos);
        }

        return gatheredState;
    }

    @Unique
    private BlockState sable$gatherState(final SubLevel level, final BlockPos b) {
        final BlockState checkedState = this.world.getBlockState(b);
        if (!checkedState.isAir()) {
            this.outputPos = b;
            return checkedState;
        }

        return null;
    }

    @WrapOperation(method = "provideFluidToSpace",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z", ordinal = 1))
    private boolean sable$preventInWorldPlace(final Level instance, final BlockPos pPos, final BlockState pNesubleveltate, final int pFlags, Operation<Boolean> original) {
        return original.call(instance, this.sable$plotOutputPos, pNesubleveltate, 3);
    }

}
