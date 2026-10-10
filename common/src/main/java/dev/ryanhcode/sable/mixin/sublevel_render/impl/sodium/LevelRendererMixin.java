package dev.ryanhcode.sable.mixin.sublevel_render.impl.sodium;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.world.LevelRendererExtension;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * On the Sodium render path, makes the chunk compiled-state check apply to chunks inside sub-levels.
 */
@Mixin(value = LevelRenderer.class, priority = 1002)
public class LevelRendererMixin {

    @Shadow
    @Nullable
    private ClientLevel level;

    /**
     * @author RyanH
     * @reason Sable sodium compatibility
     */
    @Overwrite
    public boolean isSectionCompiled(final BlockPos pos) {
        final ClientSubLevelContainer container = SubLevelContainer.getContainer(this.level);

        if (container != null && container.inBounds(pos)) {
            final ClientSubLevel subLevel = (ClientSubLevel) Sable.HELPER.getContaining(this.level, pos);

            if (subLevel == null) {
                return false;
            } else {
                final SubLevelRenderData renderData = subLevel.getRenderData();
                final SectionPos sectionPos = SectionPos.of(pos);
                return renderData.isSectionCompiled(sectionPos.x(), sectionPos.y(), sectionPos.z());
            }
        }

        final SodiumWorldRenderer sodiumRenderer = ((LevelRendererExtension) this).sodium$getWorldRenderer();

        return sodiumRenderer.isSectionReady(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }
}
