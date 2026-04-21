package dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision;

import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

import java.util.UUID;

@ApiStatus.Internal
public interface EntityMovementExtension {
    SubLevelEntityCollision.CollisionInfo sable$getCollisionInfo();

    /**
     * @return the sub-level the entity is standing on or locked to
     */
    SubLevel sable$getTrackingSubLevel();

    UUID sable$getLastTrackingSubLevelID();

    void sable$setPosField(Vec3 vec3);

    void sable$setTrackingSubLevel(SubLevel subLevel);

    void sable$setLastTrackingSubLevelID(UUID uuid);

    /**
     * @return the position that the state returned by getInBlockState was gotten from
     */
    BlockPos sable$getInBlockStatePos();
}
