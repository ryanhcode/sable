package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.contraptions;

import dev.engine_room.flywheel.impl.visualization.VisualizationEventHandler;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.neoforge.compatibility.flywheel.FlywheelCompatNeoForge;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link dev.engine_room.flywheel.impl.visualization.VisualizationEventHandler} to create Flywheel render info when an entity joins a sub-level.
 */

@Mixin(VisualizationEventHandler.class)
public class VisualizationEventHandlerMixin {

    @Inject(method = "onEntityJoinLevel", at = @At("TAIL"))
    private static void sable$onEntityJoinLevel(final Level level, final Entity entity, final CallbackInfo ci) {
        final SubLevel subLevel = Sable.HELPER.getContaining(entity);

        if (subLevel != null) {
            FlywheelCompatNeoForge.createRenderInfo(level, subLevel);
        }
    }
}
