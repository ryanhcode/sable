package dev.ryanhcode.sable.mixin.compatibility.oritech.rendering;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rearth.oritech.block.entity.interaction.LaserArmBlockEntity;
import rearth.oritech.client.renderers.LaserArmModel;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationState;

@Mixin(LaserArmModel.class)
public class LaserArmModelMixin {

    @Inject(method = "setCustomAnimations(Lrearth/oritech/block/entity/interaction/LaserArmBlockEntity;JLsoftware/bernie/geckolib/animation/AnimationState;)V", at = @At("HEAD"))
    public <T extends LaserArmBlockEntity & GeoAnimatable> void test(final T laserEntity, final long instanceId, final AnimationState<T> animationState, final CallbackInfo ci, @Share("parent_sublevel") final LocalRef<ClientSubLevel> sublevel) {
        sublevel.set(Sable.HELPER.getContainingClient(laserEntity.getBlockPos()));
    }

    @Redirect(method = "setCustomAnimations(Lrearth/oritech/block/entity/interaction/LaserArmBlockEntity;JLsoftware/bernie/geckolib/animation/AnimationState;)V", at = @At(value = "INVOKE", target = "Lrearth/oritech/block/entity/interaction/LaserArmBlockEntity;getVisualTarget()Lnet/minecraft/world/phys/Vec3;"))
    public Vec3 sable$projectTargetPos(final LaserArmBlockEntity instance, @Share("parent_sublevel") final LocalRef<ClientSubLevel> parentSublevel) {
        return sable$transformPos(instance.getVisualTarget(), parentSublevel.get());
    }

    @Redirect(method = "setCustomAnimations(Lrearth/oritech/block/entity/interaction/LaserArmBlockEntity;JLsoftware/bernie/geckolib/animation/AnimationState;)V", at = @At(value = "FIELD", target = "Lrearth/oritech/block/entity/interaction/LaserArmBlockEntity;laserHead:Lnet/minecraft/world/phys/Vec3;", opcode = Opcodes.GETFIELD))
    public Vec3 sable$projectHeadPos(final LaserArmBlockEntity instance, @Share("parent_sublevel") final LocalRef<ClientSubLevel> parentSublevel) {
        return sable$transformPos(instance.laserHead, parentSublevel.get());
    }

    @Unique
    private static Vec3 sable$transformPos(Vec3 pos, @Nullable final ClientSubLevel parentSublevel) {
        final ClientSubLevel clientSublevel = Sable.HELPER.getContainingClient(pos);

        if (clientSublevel != null) {
            pos = clientSublevel.renderPose().transformPosition(pos);
        }

        if (parentSublevel != null) {
            pos = parentSublevel.renderPose().transformPositionInverse(pos);
        }

        return pos;
    }

}
