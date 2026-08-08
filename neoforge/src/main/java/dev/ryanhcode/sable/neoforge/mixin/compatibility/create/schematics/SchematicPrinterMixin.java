package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.schematics;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.schematics.SchematicPrinter;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.create.schematics.SchematicLevelExtension;
import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.create.schematics.SchematicPrinterExtension;
import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.create.schematics.StructureTemplateExtension;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Mixin into {@link com.simibubi.create.content.schematics.SchematicPrinter} to load and transform sub-level templates in schematics.
 */

@Mixin(SchematicPrinter.class)
public class SchematicPrinterMixin implements SchematicPrinterExtension {


    @Shadow private SchematicLevel blockReader;

    @Inject(method = "loadSchematic", at = @At("TAIL"))
    private void sable$loadSchematic(final ItemStack blueprint,
                                     final Level originalWorld,
                                     final boolean processNBT,
                                     final CallbackInfo ci,
                                     @Local final StructureTransform transform) {
        final List<SchematicLevelExtension.SchematicSubLevel> schematicSubLevels = ((SchematicLevelExtension) this.blockReader).sable$getSubLevels();
        for (final SchematicLevelExtension.SchematicSubLevel schematicSubLevel : schematicSubLevels) {
            final Vec3 transformedPos = transform.applyWithoutOffset(JOMLConversion.toMojang(schematicSubLevel.position()));
            JOMLConversion.toJOML(transformedPos, schematicSubLevel.position());

            final double radians = switch (transform.rotation) {
                case NONE -> 0.0;
                case CLOCKWISE_90 -> -Math.PI / 2.0;
                case CLOCKWISE_180 -> Math.PI;
                case COUNTERCLOCKWISE_90 -> Math.PI / 2.0;
            };

            schematicSubLevel.orientation().rotateLocalY(radians);
        }

    }

    @WrapOperation(method = "loadSchematic", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;placeInWorld(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Lnet/minecraft/util/RandomSource;I)Z"))
    private boolean sable$setupRenderer(final StructureTemplate template,
                                        final ServerLevelAccessor serverLevelAccessor,
                                        final BlockPos blockPos,
                                        final BlockPos blockPos2,
                                        final StructurePlaceSettings structurePlaceSettings,
                                        final RandomSource randomSource,
                                        final int i,
                                        final Operation<Boolean> original,
                                        @Local(argsOnly = true) final Level level) {
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

    @Override
    public SchematicLevel sable$getSchematicLevel() {
        return this.blockReader;
    }
}
