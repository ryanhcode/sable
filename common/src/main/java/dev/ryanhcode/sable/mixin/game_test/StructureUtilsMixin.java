package dev.ryanhcode.sable.mixin.game_test;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link net.minecraft.gametest.framework.StructureUtils} to remove sub-levels when clearing space for a structure.
 */
@Mixin(StructureUtils.class)
public class StructureUtilsMixin {

    @Inject(method = "clearSpaceForStructure", at = @At("TAIL"))
    private static void clearSpaceForStructure(final BoundingBox box, final ServerLevel level, final CallbackInfo ci) {
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null) {
            for (final SubLevel subLevel : container.queryIntersecting(new BoundingBox3d(box))) {
                container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
            }
        }
    }
}
