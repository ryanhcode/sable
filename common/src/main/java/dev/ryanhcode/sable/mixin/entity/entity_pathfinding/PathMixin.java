package dev.ryanhcode.sable.mixin.entity.entity_pathfinding;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.entity.pathfinding.PathExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Path.class)
public class PathMixin implements PathExtension {

    @Unique
    private Level sable$level;

    @Unique
    private boolean sable$project;

    @ModifyReturnValue(method = "getNextEntityPos", at = @At("RETURN"))
    private Vec3 sable$getNextEntityPos(final Vec3 original, final Entity entity) {
        if (!this.sable$project) {
            return original;
        }

        return Sable.HELPER.projectOutOfSubLevel(entity.level(), original);
    }

    @ModifyReturnValue(method = "getNextNodePos", at = @At("RETURN"))
    private BlockPos sable$getNextNodePos(final BlockPos original) {
        if (!this.sable$project) {
            return original;
        }

        final SubLevel subLevel = Sable.HELPER.getContaining(this.sable$level, original);
        if (subLevel == null) {
            return original;
        }

        final BlockPos global = BlockPos.containing(subLevel.logicalPose().transformPosition(original.getCenter()));
        return global;
    }

    @ModifyReturnValue(method = "getNodePos", at = @At("RETURN"))
    private BlockPos sable$getNodePos(final BlockPos original) {
        if (!this.sable$project) {
            return original;
        }

        final SubLevel subLevel = Sable.HELPER.getContaining(this.sable$level, original);
        if (subLevel == null) {
            return original;
        }

        return BlockPos.containing(subLevel.logicalPose().transformPosition(original.getCenter()));
    }

    @Override
    public void sable$setLocalPath(final Level level, final boolean project) {
        this.sable$level = level;
        this.sable$project = project;
    }

}
