package dev.ryanhcode.sable.mixin.physics;

import dev.ryanhcode.sable.mixinterface.physics.ServerLevelSceneExtension;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Mixin into {@link net.minecraft.server.level.ServerLevel} to implement {@link dev.ryanhcode.sable.mixinterface.physics.ServerLevelSceneExtension}, holding the physics scene ID.
 */
@Mixin(ServerLevel.class)
public class ServerLevelMixin implements ServerLevelSceneExtension {

    @Unique
    private int sable$sceneID = -1;

    @Override
    public int sable$getSceneID() {
        return this.sable$sceneID;
    }

    @Override
    public void sable$setSceneID(final int sable$sceneID) {
        this.sable$sceneID = sable$sceneID;
    }
}
