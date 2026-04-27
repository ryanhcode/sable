package dev.ryanhcode.sable.mixin.compatibility.vista;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.mehvahdjukaar.moonlight.api.client.util.LOD;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LOD.class)
public class LODMixin {

    @Unique
    private static final Vector3d sable$direction = new Vector3d();

    @Shadow
    @Final
    @Mutable
    private Vec3 objCenter;

    @Shadow
    @Final
    @Mutable
    private double distSq;

    @Shadow
    @Final
    private Vec3 cameraPosition;

    @Unique
    private Vec3 sable$localPos = null;

    @WrapMethod(method = "isPlaneCulled(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;FF)Z")
    private boolean sable$isPlaneCulled(Vec3 planeNormal, Vec3 offset, final float discRadius, final float cosTolerance, final Operation<Boolean> original) {
        final ClientSubLevel clientSubLevel = Sable.HELPER.getContainingClient(this.sable$localPos);

        if (clientSubLevel != null) {
            planeNormal = clientSubLevel.renderPose().transformNormal(planeNormal);

            if (offset != null)
                offset = clientSubLevel.renderPose().transformNormal(offset);
        }

        return original.call(planeNormal, offset, discRadius, cosTolerance);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/Camera;Lnet/minecraft/world/phys/Vec3;)V", at = @At("TAIL"))
    private void sable$init(final Camera camera, final Vec3 objCenter, final CallbackInfo ci) {
        final ClientLevel level = Minecraft.getInstance().level;
        this.sable$localPos = objCenter;
        this.objCenter = Sable.HELPER.projectOutOfSubLevel(level, objCenter);
        this.distSq = LOD.isScoping() ? (double) 1.0F : Sable.HELPER.distanceSquaredWithSubLevels(level, this.cameraPosition, objCenter);
    }
}
