package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.render_fixes;

import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Mixin into {@link com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer} so item culling checks work correctly across sub-levels.
 */

@Mixin(SafeBlockEntityRenderer.class)
public class SafeBlockEntityRendererMixin {

    @ModifyVariable(method = "shouldCullItem", at = @At("HEAD"), remap = false, argsOnly = true)
    public Vec3 sable$projectItemPos(final Vec3 itemPos) {
        return Sable.HELPER.projectOutOfSubLevel(Minecraft.getInstance().level, itemPos);
    }
}
