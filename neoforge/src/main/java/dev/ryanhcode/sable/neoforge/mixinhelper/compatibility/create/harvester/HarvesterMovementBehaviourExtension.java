package dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.harvester;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public interface HarvesterMovementBehaviourExtension {

    BlockPos sable$getSelfPos();

    void sable$setSelfPos(BlockPos sable$selfPos);

    Level sable$getManualLevel();
    void sable$setManualLevel(Level level);

}
