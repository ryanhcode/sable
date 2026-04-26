package dev.ryanhcode.sable.mixin.extension;

import dev.ryanhcode.sable.mixinterface.EntityExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Entity.class)
public abstract class EntityMixin implements EntityExtension {
    @Shadow
    private Vec3 position;

    @Shadow
    private BlockPos blockPosition;

    @Shadow
    private AABB bb;

    @Shadow
    protected abstract AABB makeBoundingBox();

    @Shadow protected abstract Vec3 collide(Vec3 vec3);

    @Override
    public void sable$setPosSuperRaw(final Vec3 pos) {
        this.position = pos;
        this.blockPosition = BlockPos.containing(pos);
        this.bb = this.makeBoundingBox();
    }

    @Override
    public Vec3 sable$vanillaCollide(final Vec3 vec3) {
        return this.collide(vec3);
    }

    @Override
    public void sable$withPos(final Vec3 pos, final Runnable run) {
        Vec3 curentPos = position;
        position = pos;
        run.run();
        position = curentPos;
    }
}
