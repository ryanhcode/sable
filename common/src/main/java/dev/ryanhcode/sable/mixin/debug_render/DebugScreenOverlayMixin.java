package dev.ryanhcode.sable.mixin.debug_render;

import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/**
 * Mixin into {@link net.minecraft.client.gui.components.DebugScreenOverlay} to append Sable debug information to the F3 screen.
 */
@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {

    @Shadow protected abstract Level getLevel();

    @ModifyVariable(method = "getSystemInformation", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;showOnlyReducedInfo()Z", shift = At.Shift.BEFORE), ordinal = 0)
    public List<String> sable$addDebugInfo(final List<String> value) {
        final SubLevelContainer container = SubLevelContainer.getContainer(Minecraft.getInstance().level);

        value.add("");
        value.add(ChatFormatting.UNDERLINE + "Sable");
        if (container instanceof final ClientSubLevelContainer clientContainer) {
            clientContainer.addDebugInfo(value::add);
        }
        SubLevelRenderDispatcher.get().addDebugInfo(value::add);

        return value;
    }
}