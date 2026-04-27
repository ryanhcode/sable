package dev.ryanhcode.sable.mixin.particle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TerrainParticle.class)
public abstract class TerrainParticleMixin extends Particle {

    @Shadow
    @Final
    private BlockPos pos;

    protected TerrainParticleMixin(final ClientLevel clientLevel, final double d, final double e, final double f) {
        super(clientLevel, d, e, f);
    }

    @WrapOperation(method = "getLightColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I"))
    private int sable$getLightColor(final BlockAndTintGetter blockAndTintGetter, final BlockPos blockPos, Operation<Integer> original, @Local final int existingColor) {

        final ClientSubLevelContainer container = SubLevelContainer.getContainer(Minecraft.getInstance().level);
        assert container != null;
        final SubLevel subLevel = Sable.HELPER.getContainingClient(this.pos);

        if (subLevel instanceof final ClientSubLevel clientSubLevel) {
            final int color = original.call(blockAndTintGetter, blockPos);
            return clientSubLevel.scaleLightColor(color);
        } else if (container.inBounds(blockPos)) {
            return existingColor;
        }

        return original.call(blockAndTintGetter, blockPos);
    }

}
