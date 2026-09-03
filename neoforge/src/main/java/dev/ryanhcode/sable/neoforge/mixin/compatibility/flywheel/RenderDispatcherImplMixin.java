package dev.ryanhcode.sable.neoforge.mixin.compatibility.flywheel;

import dev.engine_room.flywheel.api.backend.RenderContext;
import dev.engine_room.flywheel.impl.visualization.VisualManagerImpl;
import dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl;
import dev.ryanhcode.sable.neoforge.compatibility.flywheel.FlywheelCompatNeoForge;
import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.flywheel.BlockEntityStorageExtension;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into VisualizationManagerImpl.RenderDispatcherImpl to update sub-level embeddings before each frame.
 */

@Mixin(targets = "dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl$RenderDispatcherImpl")
public class RenderDispatcherImplMixin {

    @Shadow @Final private VisualizationManagerImpl this$0;

    @Inject(method = "onStartLevelRender", at = @At("HEAD"))
    private void sable$onStartLevelRender(final RenderContext ctx, final CallbackInfo ci) {
        FlywheelCompatNeoForge.preVisualizationFrame(ctx.level(), ctx.partialTick());
        ((BlockEntityStorageExtension) ((VisualManagerImpl) this.this$0.blockEntities()).getStorage())
                .sable$preFlywheelFrame();
    }

}
