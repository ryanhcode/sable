package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.schematics;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.simibubi.create.content.schematics.SchematicAndQuillItem;
import com.simibubi.create.content.schematics.SchematicExport;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.companion.math.*;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SableNBTUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector3ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Mixin into {@link com.simibubi.create.content.schematics.SchematicExport} to include sub-level data when exporting schematics.
 */

@Mixin(SchematicExport.class)
public class SchematicExportMixin {

    @Inject(method = "saveSchematic", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;fillFromWorld(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Vec3i;ZLnet/minecraft/world/level/block/Block;)V", shift = At.Shift.BEFORE))
    private static void sable$saveSchematic(final Path dir,
                                            final String fileName,
                                            final boolean overwrite,
                                            final Level level,
                                            final BlockPos first,
                                            final BlockPos second,
                                            final CallbackInfoReturnable<SchematicExport.SchematicExportResult> cir,
                                            @Share("containingSubLevel") final LocalRef<SubLevel> containingSubLevelRef,
                                            @Share("intersectingSubLevels") final LocalRef<Iterable<SubLevel>> intersectingRef
    ) {
        final BoundingBox3d schematicBounds = new BoundingBox3d(first.getX(), first.getY(), first.getZ(),
                second.getX() + 1, second.getY() + 1, second.getZ() + 1);

        final BoundingBox bb = BoundingBox.fromCorners(first, second);
        final BlockPos totalOrigin = new BlockPos(bb.minX(), bb.minY(), bb.minZ());

        final ActiveSableCompanion helper = Sable.HELPER;
        final SubLevel containingSubLevel = helper.getContaining(level, schematicBounds.center(new Vector3d()));
        if (containingSubLevel != null) {
            final Pose3d containingPose = containingSubLevel.logicalPose();
            schematicBounds.transform(containingPose, schematicBounds);
        }
        containingSubLevelRef.set(containingSubLevel);

        final Iterable<SubLevel> intersecting = helper.getAllIntersecting(level, schematicBounds);
        intersectingRef.set(intersecting);

        final SubLevelSchematicSerializationContext context = new SubLevelSchematicSerializationContext(SubLevelSchematicSerializationContext.Type.SAVE, new BoundingBox3i(first, second));
        context.setSetupTransform((block) -> ((BlockPos) block));
        context.setPlaceTransform((block) -> ((BlockPos) block).subtract(totalOrigin));

        for (final SubLevel subLevel : intersecting) {
            if (subLevel == containingSubLevel) continue;

            final BoundingBox3ic plotBounds = subLevel.getPlot().getBoundingBox();
            final BlockPos origin = new BlockPos(plotBounds.minX(), plotBounds.minY(), plotBounds.minZ());

            Vec3 pos = subLevel.logicalPose().transformPosition(Vec3.atLowerCornerOf(origin));
            final Quaterniond orientation = new Quaterniond(subLevel.logicalPose().orientation());

            if (containingSubLevel != null) {
                final Pose3d containingPose = containingSubLevel.logicalPose();
                pos = containingPose.transformPositionInverse(pos);
                orientation.premul(containingPose.orientation().conjugate(new Quaterniond()));
            }

            final Vector3d position = JOMLConversion.toJOML(pos.subtract(Vec3.atLowerCornerOf(totalOrigin)));

            context.getMappings().put(subLevel.getUniqueId(), new SubLevelSchematicSerializationContext.SchematicMapping(
                    position, orientation, UUID.randomUUID(), block -> ((BlockPos)block).offset(origin.multiply(-1))
            ));
        }

        SubLevelSchematicSerializationContext.setCurrentContext(context);
    }

    @Inject(method = "saveSchematic", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/schematics/SchematicAndQuillItem;clampGlueBoxes(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/nbt/CompoundTag;)V", shift = At.Shift.AFTER))
    private static void sable$saveSchematicPost(
            final Path dir,
            final String fileName,
            final boolean overwrite,
            final Level level,
            final BlockPos first,
            final BlockPos second,
            final CallbackInfoReturnable<SchematicExport.SchematicExportResult> cir,
            @Local final CompoundTag data,
            @Share("containingSubLevel") final LocalRef<SubLevel> containingSubLevelRef,
            @Share("intersectingSubLevels") final LocalRef<Iterable<SubLevel>> intersectingRef
    ) {
        final ListTag list = new ListTag();

        final SubLevel containingSubLevel = containingSubLevelRef.get();
        final SubLevelSchematicSerializationContext context = SubLevelSchematicSerializationContext.getCurrentContext();

        for (final SubLevel subLevel : intersectingRef.get()) {
            if (subLevel == containingSubLevel) continue;
            final BoundingBox3ic plotBounds = subLevel.getPlot().getBoundingBox();
            final Vector3ic size = plotBounds.size(new Vector3i());
            final BlockPos origin = new BlockPos(plotBounds.minX(), plotBounds.minY(), plotBounds.minZ());
            final BlockPos bounds = new BlockPos(size.x() + 1, size.y() + 1, size.z() + 1);

            final StructureTemplate structure = new StructureTemplate();
            structure.fillFromWorld(level, origin, bounds, true, Blocks.AIR);
            final CompoundTag subLevelData = structure.save(new CompoundTag());
            SchematicAndQuillItem.replaceStructureVoidWithAir(subLevelData);
            SchematicAndQuillItem.clampGlueBoxes(level, new AABB(Vec3.atLowerCornerOf(origin), Vec3.atLowerCornerOf(origin.offset(bounds))), subLevelData);

            final SubLevelSchematicSerializationContext.SchematicMapping mapping = context.getMapping(subLevel);

            subLevelData.putUUID("uuid", mapping.newUUID());
            subLevelData.put("position", SableNBTUtils.writeVector3d(mapping.newCorner()));
            subLevelData.put("orientation", SableNBTUtils.writeQuaternion(mapping.newOrientation()));

            list.add(subLevelData);
        }

        SubLevelSchematicSerializationContext.setCurrentContext(null);

        if (!list.isEmpty()) {
            data.put("sub_levels", list);
        }
    }

}
