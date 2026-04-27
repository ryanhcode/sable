package dev.ryanhcode.sable.neoforge.mixin.compatibility.pmweather;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.protomanly.pmweather.block.entity.RadarBlockEntity;
import dev.protomanly.pmweather.render.RadarRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RadarRenderer.class)
public class RadarRendererMixin {

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D"))
    private double sable$redirectDistanceTo(final Vec3 position, final Vec3 blockEntityPos) {
        final ClientLevel level = Minecraft.getInstance().level;
        return Math.sqrt(Sable.HELPER.distanceSquaredWithSubLevels(level, position, blockEntityPos));
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Ldev/protomanly/pmweather/block/entity/RadarBlockEntity;getBlockPos()Lnet/minecraft/core/BlockPos;"))
    public BlockPos sable$render(final RadarBlockEntity instance) {
        final Vec3 globalPos = Sable.HELPER.projectOutOfSubLevel(instance.getLevel(), instance.getBlockPos().getCenter());
        return BlockPos.containing(globalPos);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;multiply(DDD)Lnet/minecraft/world/phys/Vec3;", ordinal = 0))
    public Vec3 sable$renderWorldPos(final Vec3 instance, final double x, final double y, final double z, Operation<Vec3> original, @Local final RadarBlockEntity blockEntity) {
        final SubLevel subLevel = Sable.HELPER.getContaining(blockEntity);

        if (subLevel == null) {
            return original.call(instance, x, y, z);
        }

        final Vec3 globalDir = subLevel.logicalPose().transformNormal(new Vec3(instance.x, 0, instance.z));

        return new Vec3(globalDir.x, 0.0, globalDir.z).multiply(x, y, z);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec2;normalized()Lnet/minecraft/world/phys/Vec2;", ordinal = 0))
    public Vec2 sable$renderWind(final Vec2 instance, Operation<Vec2> original, @Local final RadarBlockEntity blockEntity) {
        final SubLevel subLevel = Sable.HELPER.getContaining(blockEntity);

        if (subLevel == null) {
            return original.call(instance);
        }

        final Vec3 globalDir = subLevel.logicalPose().transformNormal(new Vec3(instance.x, 0.0, instance.y));

        return original.call(new Vec2((float) globalDir.x, (float) globalDir.z));
    }

}
