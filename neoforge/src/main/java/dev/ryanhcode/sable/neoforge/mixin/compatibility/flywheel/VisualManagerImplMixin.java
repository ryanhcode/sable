package dev.ryanhcode.sable.neoforge.mixin.compatibility.flywheel;

import dev.engine_room.flywheel.api.task.Plan;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualManager;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.impl.visualization.VisualManagerImpl;
import dev.engine_room.flywheel.impl.visualization.storage.Storage;
import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.flywheel.BlockEntityStorageExtension;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin into {@link dev.engine_room.flywheel.impl.visualization.VisualManagerImpl} to provide visualization contexts for sub-level embeddings.
 */

@Mixin(value = VisualManagerImpl.class, remap = false)
public abstract class VisualManagerImplMixin<T, S extends Storage<T>> implements VisualManager<T> {

    @Shadow @Final private S storage;

    @Inject(method = "framePlan", at = @At("HEAD"))
    private void sable$preFramePlan(final VisualizationContext visualizationContext, final CallbackInfoReturnable<Plan<DynamicVisual.Context>> cir) {
        if (this.storage instanceof final BlockEntityStorageExtension extension) {
            extension.sable$setPlanVisualizationContext(visualizationContext);
        }
    }
}
