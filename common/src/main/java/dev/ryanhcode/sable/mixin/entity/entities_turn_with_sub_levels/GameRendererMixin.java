package dev.ryanhcode.sable.mixin.entity.entities_turn_with_sub_levels;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Shadow @Final private Minecraft minecraft;

    @Unique
    private final Quaterniond sable$lastOrientation = new Quaterniond();

    @Unique
    private final Quaterniond sable$relativeOrientation = new Quaterniond();

    @Unique
    private UUID sable$lastSubLevel = null;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    public void renderLevel(final DeltaTracker deltaTracker, final CallbackInfo ci) {
        final LocalPlayer player = this.minecraft.player;
        final SubLevel standingSubLevel = Sable.HELPER.getTrackingSubLevel(player);

        if (standingSubLevel != null && player.getVehicle() == null && !standingSubLevel.isRemoved() && !EntitySubLevelUtil.hasCustomEntityOrientation(player)) {
            final Quaterniondc current = ((ClientSubLevel) standingSubLevel).renderPose().orientation();

            if (this.sable$lastSubLevel == null || !this.sable$lastSubLevel.equals(standingSubLevel.getUniqueId())) {
                this.sable$lastOrientation.set(current);
                this.sable$lastSubLevel = standingSubLevel.getUniqueId();
            }

            final Quaterniond relativeOrientation;
            final Quaterniondc customOrientation = EntitySubLevelUtil.getCustomEntityOrientation(player, 1.0f);
            if (customOrientation != null) {
                final Quaterniond inverseCustom = new Quaterniond(customOrientation).conjugate();
                final Quaterniond currentLocal = current.premul(inverseCustom, new Quaterniond());
                final Quaterniond lastLocal = this.sable$lastOrientation.premul(inverseCustom, new Quaterniond());
                relativeOrientation = currentLocal.div(lastLocal, this.sable$relativeOrientation);
            } else {
                current.div(this.sable$lastOrientation, this.sable$relativeOrientation);
                relativeOrientation = current.div(this.sable$lastOrientation, this.sable$relativeOrientation);
            }

            final double angleDiff = 2 * relativeOrientation.y / relativeOrientation.w;
            final float delta = (float) Math.toDegrees(angleDiff);

            player.yBodyRot -= delta;
            player.yBodyRotO -= delta;
            player.yHeadRot -= delta;
            player.yHeadRotO -= delta;
            player.setYRot(player.getYRot() - delta);
            player.yRotO -= delta;

            this.sable$lastOrientation.set(current);
        } else {
            this.sable$lastSubLevel = null;
        }
    }
}
