package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.super_glue;

import com.google.common.collect.Lists;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import dev.ryanhcode.sable.util.SubLevelInclusiveLevelEntityGetter;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(value = SuperGlueEntity.class, remap = false)
public class SuperGlueEntityMixin {

    @WrapOperation(method = "collectCropped", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private static List sable$collectGlueEntities(final Level instance, final Class aClass, final AABB aabb, Operation<List> original) {
        if (((LevelAccessor) instance).invokeGetEntities() instanceof final SubLevelInclusiveLevelEntityGetter<Entity> getter) {
            final List<Entity> list = Lists.newArrayList();
            getter.getIgnoringSubLevels(EntityTypeTest.forClass(aClass), aabb, (entity) -> {
                list.add(entity);
                return AbortableIterationConsumer.Continuation.CONTINUE;
            });
            return list;
        }

        return original.call(instance, aClass, aabb);
    }

}
