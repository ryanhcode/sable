package dev.ryanhcode.sable.mixin.sublevel_render;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.sublevel_render.vanilla.RenderSectionExtension;
import foundry.veil.api.client.render.VeilRenderSystem;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Fixes distance check used for priority and chunk building to take sublevels into account
 */
@Mixin(SectionRenderDispatcher.RenderSection.class)
public class RenderSectionMixin implements RenderSectionExtension {

    @Shadow
    private AABB bb;
    @Shadow
    private boolean dirty;

    @Unique
    private Set<DirtyListener> sable$listeners;
    @Unique
    private boolean sable$listening = true;

    @Inject(method = "setDirty", at = @At("HEAD"))
    public void setDirty(final boolean playerChanged, final CallbackInfo ci) {
        if (this.sable$listening && !this.dirty && this.sable$listeners != null) {
            VeilRenderSystem.renderThreadExecutor().execute(() -> {
                for (final DirtyListener listener : this.sable$listeners) {
                    listener.markDirty((SectionRenderDispatcher.RenderSection) (Object) this);
                }
            });
        }
    }

    /**
     * @author RyanH
     * @reason Fixes distance check to take sublevels into account
     */
    @Overwrite
    public double getDistToPlayerSqr() {
        final ClientLevel level = Minecraft.getInstance().level;
        final Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        final double x = this.bb.minX + 8.0;
        final double y = this.bb.minY + 8.0;
        final double z = this.bb.minZ + 8.0;
        return Sable.HELPER.distanceSquaredWithSubLevels(level, camera.getPosition(), x, y, z);
    }

    @Override
    public void sable$addDirtyListener(final DirtyListener listener) {
        if (this.sable$listeners == null) {
            this.sable$listeners = new ObjectArraySet<>();
        }
        this.sable$listeners.add(listener);
    }

    @Override
    public void sable$setListening(final boolean listening) {
        this.sable$listening = listening;
    }
}
