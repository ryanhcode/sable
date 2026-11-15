package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.super_glue;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.LevelEntityGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Mixin into {@link net.minecraft.world.level.Level} to expose getEntities for sub-level aware entity queries.
 */

@Mixin(Level.class)
public interface LevelAccessor {

    @Invoker
    LevelEntityGetter<Entity> invokeGetEntities();

}
