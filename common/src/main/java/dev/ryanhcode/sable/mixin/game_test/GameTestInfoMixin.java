package dev.ryanhcode.sable.mixin.game_test;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link net.minecraft.gametest.framework.GameTestInfo} to remove intersecting sub-levels when a game test succeeds.
 */
@Mixin(GameTestInfo.class)
public abstract class GameTestInfoMixin {

    @Shadow
    public abstract ServerLevel getLevel();

    @Inject(method = "succeed", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"))
    public void removeSublevels(final CallbackInfo ci, @Local final AABB aabb) {
        final SubLevelContainer container = SubLevelContainer.getContainer(this.getLevel());
        if (container != null) {
            for (final SubLevel subLevel : container.queryIntersecting(new BoundingBox3d(aabb))) {
                container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
            }
        }
    }
}
