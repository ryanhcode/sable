package dev.ryanhcode.sable.mixin.entity.entity_aabb_lookup;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.ryanhcode.sable.util.SubLevelInclusiveLevelEntityGetter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.LevelEntityGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Wraps the client and server level {@link net.minecraft.world.level.entity.LevelEntityGetterAdapter} in a {@link SubLevelInclusiveLevelEntityGetter}
 */
@Mixin(ServerLevel.class)
public class ServerLevelMixin {

    @ModifyReturnValue(method = "getEntities()Lnet/minecraft/world/level/entity/LevelEntityGetter;", at = @At("RETURN"))
    private LevelEntityGetter<Entity> sable$postGetEntities(final LevelEntityGetter<Entity> original) {
        return new SubLevelInclusiveLevelEntityGetter<>((Level) (Object) this, original);
    }
}
