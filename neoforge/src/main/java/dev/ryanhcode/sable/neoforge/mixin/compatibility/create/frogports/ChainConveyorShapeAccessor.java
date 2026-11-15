package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.frogports;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorShape;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Mixin into {@link com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorShape} to expose drawOutline for sub-level selection box rendering.
 */

@Mixin(ChainConveyorShape.class)
public interface ChainConveyorShapeAccessor {

    @Invoker
    void invokeDrawOutline(BlockPos anchor, PoseStack ms, VertexConsumer vb);

}
