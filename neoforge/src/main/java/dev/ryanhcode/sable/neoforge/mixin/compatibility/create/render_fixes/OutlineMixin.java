package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.render_fixes;

import net.createmod.catnip.outliner.Outline;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mixin into {@link net.createmod.catnip.outliner.Outline} so Create outlines work correctly with sub-levels.
 */

@Mixin(Outline.class)
public class OutlineMixin {
}