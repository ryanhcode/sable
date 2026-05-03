package dev.ryanhcode.sable.compatibility;

import net.minecraft.world.entity.Entity;
import java.util.ServiceLoader;

public interface ContraptionCompat {
    ContraptionCompat INSTANCE = ServiceLoader.load(ContraptionCompat.class)
            .findFirst()
            .orElse(entity -> false);

    boolean isContraption(Entity entity);
}