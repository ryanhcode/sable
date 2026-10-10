package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.sails_providing_lift;

import com.simibubi.create.content.contraptions.bearing.SailBlock;
import dev.ryanhcode.sable.api.block.BlockSubLevelCustomCenterOfMass;
import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mixin into {@link com.simibubi.create.content.contraptions.bearing.SailBlock} to provide lift and a custom center of mass for sub-levels.
 */

@Mixin(SailBlock.class)
public class SailBlockMixin implements BlockSubLevelLiftProvider, BlockSubLevelCustomCenterOfMass {
    @Override
    public @NotNull Direction sable$getNormal(final BlockState state) {
        return state.getValue(BlockStateProperties.FACING).getOpposite();
    }

    @Override
    public Vector3dc getCenterOfMass(final BlockGetter blockGetter, final BlockState state) {
        return JOMLConversion.HALF;
    }
}
