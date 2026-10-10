package dev.ryanhcode.sable.mixin.entity.entity_collision;

import dev.ryanhcode.sable.mixinhelpers.entity.entity_collision.TheFasterEntityCollisionContext;
import dev.ryanhcode.sable.mixinterface.entity.entity_collision.EntityExtension;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Mixin into {@link net.minecraft.world.entity.Entity} to provide entities with a pre-allocated, efficient collision context.
 */
@Mixin(Entity.class)
public class EntityMixin implements EntityExtension {

    @Unique
    private final TheFasterEntityCollisionContext sable$collisionContext = new TheFasterEntityCollisionContext((Entity) (Object) this);

    @Override
    public TheFasterEntityCollisionContext sable$getCollisionContext() {
        return this.sable$collisionContext;
    }
}
