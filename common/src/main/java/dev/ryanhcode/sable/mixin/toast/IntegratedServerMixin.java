package dev.ryanhcode.sable.mixin.toast;

import dev.ryanhcode.sable.index.SableToasts;
import dev.ryanhcode.sable.mixinterface.toast.SableToastableServer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Mixin into {@link net.minecraft.client.server.IntegratedServer} to implement {@link dev.ryanhcode.sable.mixinterface.toast.SableToastableServer}, showing toasts for sub-level load/save/physics failures.
 */
@Mixin(IntegratedServer.class)
public class IntegratedServerMixin implements SableToastableServer {
    @Shadow @Final private Minecraft minecraft;

    @Override
    public void sable$reportSubLevelLoadFailure(final GlobalSavedSubLevelPointer pointer) {
        SystemToast.addOrUpdate(
                this.minecraft.getToasts(),
                SableToasts.SUB_LEVEL_LOAD_FAILURE,
                Component.translatable("sub_level.toast.loadFailure", Component.literal(pointer.toString())).withStyle(ChatFormatting.RED),
                Component.translatable("sub_level.toast.checkLog")
        );
    }

    @Override
    public void sable$reportSubLevelSaveFailure(final SubLevelData data) {
        SystemToast.addOrUpdate(
                this.minecraft.getToasts(),
                SableToasts.SUB_LEVEL_SAVE_FAILURE,
                Component.translatable("sub_level.toast.saveFailure", Component.literal(data.toString())).withStyle(ChatFormatting.RED),
                Component.translatable("sub_level.toast.checkLog")
        );
    }

    @Override
    public void sable$reportSubLevelPhysicsFailure(final ServerSubLevel data) {
        SystemToast.addOrUpdate(
                this.minecraft.getToasts(),
                SableToasts.SUB_LEVEL_PHYSICS_FAILURE,
                Component.translatable("sub_level.toast.physicsFailure", Component.literal(data.toString())).withStyle(ChatFormatting.RED),
                Component.translatable("sub_level.toast.attemptingRecovery")
        );
    }
}
