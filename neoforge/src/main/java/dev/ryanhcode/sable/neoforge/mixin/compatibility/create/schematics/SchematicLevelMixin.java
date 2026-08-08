package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.schematics;

import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.create.schematics.SchematicLevelExtension;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

/**
 * Mixin into {@link net.createmod.catnip.levelWrappers.SchematicLevel} to store sub-level templates in the schematic world.
 */

@Mixin(SchematicLevel.class)
public class SchematicLevelMixin implements SchematicLevelExtension {

    @Unique
    private final List<SchematicLevelExtension.SchematicSubLevel> sable$subLevels = new ObjectArrayList<>();

    @Override
    public List<SchematicLevelExtension.SchematicSubLevel> sable$getSubLevels() {
        return this.sable$subLevels;
    }

}
