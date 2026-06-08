package dev.ryanhcode.sable.mixin.compatibility.waystones;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hides the distance for a waystone whose sub-level isn't loaded on this client. Its stored plot-yard
 * coordinate can't be projected without a pose here, so the distance would show a meaningless
 * ~20,000 km; omit it instead, as Waystones already does for cross-dimension waystones.
 */
@Mixin(targets = "net.blay09.mods.waystones.client.gui.widget.WaystoneButton", remap = false)
public abstract class WaystoneButtonMixin {

    // Receiver must be LocalPlayer (its static type at the call site); WrapOperation needs the exact owner.
    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE", target = "distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private double sable$detectUnloadedSubLevel(final LocalPlayer player, final Vec3 waystonePos, final Operation<Double> original, @Share("sableUnknownDistance") final LocalBooleanRef unknown) {
        unknown.set(sable$isOnUnloadedSubLevel(player.level(), waystonePos));
        return original.call(player, waystonePos);
    }

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I"))
    private int sable$hideUnloadedSubLevelDistance(final GuiGraphics graphics, final Font font, final String text, final int x, final int y, final int color, final Operation<Integer> original, @Share("sableUnknownDistance") final LocalBooleanRef unknown) {
        if (unknown.get()) {
            return 0;
        }
        return original.call(graphics, font, text, x, y, color);
    }

    @Unique
    private static boolean sable$isOnUnloadedSubLevel(final Level level, final Vec3 pos) {
        // Loaded here: the projected distance is accurate.
        if (Sable.HELPER.getContaining(level, pos.x, pos.z) != null) {
            return false;
        }
        // Only unknown when it's a plot-yard coordinate; a genuinely far waystone keeps its real distance.
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        return container != null && container.inBounds(BlockPos.containing(pos));
    }
}
