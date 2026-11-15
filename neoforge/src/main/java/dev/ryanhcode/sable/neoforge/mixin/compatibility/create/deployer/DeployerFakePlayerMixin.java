package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.deployer;

import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.TargetLocalInteractionEntity;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = DeployerFakePlayer.class, remap = false)
public abstract class DeployerFakePlayerMixin
        implements TargetLocalInteractionEntity {

    @Override
    public boolean sable$isAlreadyLocalTo(
            final SubLevel targetSubLevel
    ) {
        final Entity self = (Entity) (Object) this;

        /*
         * DeployerHandler moves its FakePlayer to the ray origin before
         * creating UseOnContext. Inside a Sable SubLevel, this position and
         * the rotation supplied by Create are both SubLevel-local.
         */
        final SubLevel sourceSubLevel =
                Sable.HELPER.getContaining(
                        self.level(),
                        self.position()
                );

        /*
         * Both instances originate from Sable's loaded SubLevel registry,
         * so identity comparison is intentional.
         */
        return sourceSubLevel != null
                && sourceSubLevel == targetSubLevel;
    }
}