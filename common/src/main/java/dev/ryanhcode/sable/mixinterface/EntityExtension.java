package dev.ryanhcode.sable.mixinterface;

import net.minecraft.world.phys.Vec3;

public interface EntityExtension {
    void sable$setPosSuperRaw(Vec3 pos);

    Vec3 sable$vanillaCollide(Vec3 vec3);

    void sable$withPos(Vec3 pos, Runnable run);
}
