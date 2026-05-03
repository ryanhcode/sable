package dev.ryanhcode.sable.neoforge.compatibility;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.ryanhcode.sable.compatibility.ContraptionCompat;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;

public class ContraptionCompatImpl implements ContraptionCompat {
    @Override
    public boolean isContraption(Entity entity) {
        if (!ModList.get().isLoaded("create")) return false;
        return entity instanceof AbstractContraptionEntity;
    }
}