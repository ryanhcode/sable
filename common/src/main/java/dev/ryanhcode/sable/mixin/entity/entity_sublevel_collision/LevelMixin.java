package dev.ryanhcode.sable.mixin.entity.entity_sublevel_collision;

import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LevelExtension;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Mixin into {@link net.minecraft.world.level.Level} to provide a pool of reusable JOML vectors for collision computation.
 */
@Mixin(Level.class)
public class LevelMixin implements LevelExtension {

    /**
     * The JOML sink for this level.
     * We store vectors here so that {@link dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision} doesn't have to
     * repeatedly allocate new vectors.
     */
    @Unique
    private final LevelReusedVectors sable$reusedVectors = new LevelReusedVectors();

    @Override
    public LevelReusedVectors sable$getJOMLSink() {
        return this.sable$reusedVectors;
    }
}
