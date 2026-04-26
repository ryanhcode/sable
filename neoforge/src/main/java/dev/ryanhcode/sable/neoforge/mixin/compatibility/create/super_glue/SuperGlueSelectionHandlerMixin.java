package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.super_glue;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueSelectionHandler;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * TODO: Instead of projecting the BB outwards, project the ray inwards
 */
@Mixin(SuperGlueSelectionHandler.class)
public class SuperGlueSelectionHandlerMixin {

    @WrapOperation(method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lcom/simibubi/create/content/contraptions/glue/SuperGlueEntity;getBoundingBox()Lnet/minecraft/world/phys/AABB;", ordinal = 0))
    private AABB sable$projectBoundingBox(final SuperGlueEntity instance, Operation<AABB> original) {
        final SubLevel subLevel = Sable.HELPER.getContaining(instance);

        if (subLevel != null) {
            final BoundingBox3d bb = new BoundingBox3d(original.call(instance));
            return bb.transform(subLevel.logicalPose(), bb).toMojang();
        }

        return original.call(instance);
    }
}
