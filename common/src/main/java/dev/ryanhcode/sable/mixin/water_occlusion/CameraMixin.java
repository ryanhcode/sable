package dev.ryanhcode.sable.mixin.water_occlusion;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.ryanhcode.sable.mixinterface.water_occlusion.CameraWaterOcclusionExtension;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionContainer;
import net.minecraft.client.Camera;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Makes the camera stop returning underwater fog types when inside a water-occluded area.
 */
@Mixin(Camera.class)
public class CameraMixin implements CameraWaterOcclusionExtension {

    @Shadow
    private Vec3 position;

    @Shadow
    private BlockGetter level;

    @Unique
    private boolean sable$ignoreOcclusion = false;

    @ModifyReturnValue(method = "getFluidInCamera", at = @At("RETURN"))
    public FogType sable$getFluidInCamera(final FogType original) {
        if (this.sable$ignoreOcclusion) {
            return original;
        }

        if (original == FogType.WATER || original == FogType.LAVA) {
            final boolean occluded = this.sable$isOccluded();

            if (occluded) {
                return FogType.NONE;
            }
        }

        return original;
    }

    @Override
    public void sable$setIgnoreOcclusion(final boolean ignore) {
        this.sable$ignoreOcclusion = ignore;
    }

    @Override
    public boolean sable$isIgnoreOcclusion() {
        return this.sable$ignoreOcclusion;
    }

    @Override
    public boolean sable$isOccluded() {
        final WaterOcclusionContainer<?> container = WaterOcclusionContainer.getContainer((Level) this.level);

        if (container == null)
            return false;

        return container.isOccluded(this.position);
    }
}
