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
 * Hides the misleading distance for a waystone whose sub-level isn't loaded on this client.
 *
 * <p>A waystone on a sub-level stores the far-away plot-yard coordinate. While the contraption is
 * loaded, Sable's {@code Entity#distanceToSqr} overwrite projects it to the real location and the
 * distance is accurate. While it is unloaded here the client has no pose to project against, so the
 * distance would render as a meaningless ~20,000 km. The client can't recover the real distance
 * without the server streaming last-known poses, so instead we omit the distance entirely - the same
 * thing Waystones already does for cross-dimension waystones.
 */
@Mixin(targets = "net.blay09.mods.waystones.client.gui.widget.WaystoneButton", remap = false)
public abstract class WaystoneButtonMixin {

    // `player` is statically a LocalPlayer at the call site, so the receiver parameter must be typed
    // as LocalPlayer (MixinExtras requires the exact owner type, not a supertype).
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
        // Loaded here: the projected distance is accurate, leave it alone.
        if (Sable.HELPER.getContaining(level, pos.x, pos.z) != null) {
            return false;
        }
        // Otherwise, only treat it as unknown when the position is actually a reserved plot-yard
        // coordinate - a genuinely far-away waystone keeps its real (large) distance.
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        return container != null && container.inBounds(BlockPos.containing(pos));
    }
}
