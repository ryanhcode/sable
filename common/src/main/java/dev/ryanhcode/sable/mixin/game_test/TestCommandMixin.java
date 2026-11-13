package dev.ryanhcode.sable.mixin.game_test;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.TestCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin into {@link net.minecraft.gametest.framework.TestCommand} to remove intersecting sub-levels when a game test is reset.
 */
@Mixin(TestCommand.class)
public class TestCommandMixin {

    @Inject(method = "resetGameTestInfo", at = @At("HEAD"))
    private static void resetGameTestInfo(final GameTestInfo gameTestInfo, final CallbackInfoReturnable<Integer> cir) {
        final SubLevelContainer container = SubLevelContainer.getContainer(gameTestInfo.getLevel());
        if (container != null) {
            for (final SubLevel subLevel : container.queryIntersecting(new BoundingBox3d(gameTestInfo.getStructureBounds()))) {
                container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
            }
        }
    }
}
