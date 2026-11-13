package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.schematics;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.schematics.client.SchematicHandler;
import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.create.schematics.SchematicLevelExtension;
import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.create.schematics.StructureTemplateExtension;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Mixin into {@link com.simibubi.create.content.schematics.client.SchematicHandler} to place sub-level templates into the schematic render world.
 */

@Mixin(SchematicHandler.class)
public class SchematicHandlerMixin {

    @WrapOperation(method = "setupRenderer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;placeInWorld(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Lnet/minecraft/util/RandomSource;I)Z"))
    private boolean sable$setupRenderer(final StructureTemplate template,
                                        final ServerLevelAccessor serverLevelAccessor,
                                        final BlockPos blockPos,
                                        final BlockPos blockPos2,
                                        final StructurePlaceSettings structurePlaceSettings,
                                        final RandomSource randomSource,
                                        final int i,
                                        final Operation<Boolean> original,
                                        @Local final Level level) {
        if (serverLevelAccessor instanceof final SchematicLevel schematicLevel) {
            final StructureTemplateExtension extension = (StructureTemplateExtension) template;
            final List<StructureTemplateExtension.SubLevelTemplate> subLevelTemplates = extension.sable$getSubLevels();
            final SchematicLevelExtension schematicLevelExtension = (SchematicLevelExtension) schematicLevel;

            for (final StructureTemplateExtension.SubLevelTemplate subLevelTemplate : subLevelTemplates) {
                final SchematicLevel subSchematicLevel = new SchematicLevel(level);

                subLevelTemplate.template().placeInWorld(
                        subSchematicLevel, BlockPos.ZERO, BlockPos.ZERO, new StructurePlaceSettings(), level.getRandom(), Block.UPDATE_CLIENTS);

                schematicLevelExtension.sable$getSubLevels()
                        .add(new SchematicLevelExtension.SchematicSubLevel(subLevelTemplate.uuid(), subLevelTemplate.position(), subLevelTemplate.orientation(), subSchematicLevel));
            }

        }
        return original.call(template, serverLevelAccessor, blockPos, blockPos2, structurePlaceSettings, randomSource, i);
    }

}
