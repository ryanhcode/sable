package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.tracks;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.trains.graph.*;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin into {@link com.simibubi.create.content.trains.graph.TrackGraphVisualizer} so track graph visualization displays correctly in sub-levels.
 */

@Mixin(TrackGraphVisualizer.class)
public class TrackGraphVisualizerMixin {

    @WrapOperation(method = "debugViewGraph", at = @At(value = "FIELD", target = "Lcom/simibubi/create/content/trains/graph/TrackGraphBounds;box:Lnet/minecraft/world/phys/AABB;", opcode = Opcodes.GETFIELD))
    private static AABB debugViewGraph(final TrackGraphBounds instance,
                                       final Operation<AABB> original) {
        if (instance.box == null) return original.call(instance);

        final Level level = Minecraft.getInstance().level;
        if (level == null) return original.call(instance);

        final Vec3 center = instance.box.getCenter();
        final SubLevel containing = Sable.HELPER.getContaining(level, center);

        if (containing == null) return original.call(instance);

        return new BoundingBox3d(instance.box).transform(containing.logicalPose()).toMojang();
    }

    @WrapOperation(method = "debugViewGraph", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D"))
    private static double debugViewGraph(final Vec3 location,
                                         final Vec3 camera,
                                         final Operation<Double> original) {
        final Level level = Minecraft.getInstance().level;
        if (level == null) return original.call(location, camera);
        return Math.sqrt(Sable.HELPER.distanceSquaredWithSubLevels(level, location, camera));
    }

}
