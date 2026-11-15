package dev.ryanhcode.sable.mixin.player_freezing;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Mixin into {@link net.minecraft.world.entity.player.Player} to implement {@link dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension}, managing the frozen-to sub-level state and teleporting on finalization.
 */
@Mixin(Player.class)
public abstract class PlayerMixin extends Entity implements PlayerFreezeExtension {

    @Unique
    @Nullable
    private UUID sable$frozenToSubLevel = null;
    @Unique
    @Nullable
    private Vector3dc sable$frozenToSubLevelAnchor = null;

    @Unique
    private int sable$frozenTicks = 0;

    public PlayerMixin(final EntityType<?> entityType, final Level level) {
        super(entityType, level);
    }

    @Override
    public @Nullable UUID sable$getFrozenToSubLevel() {
        return this.sable$frozenToSubLevel;
    }

    @Override
    public @Nullable Vector3dc sable$getFrozenToSubLevelAnchor() {
        return this.sable$frozenToSubLevelAnchor;
    }

    @Inject(method = "tick", at = @At(value = "HEAD"))
    private void sable$preFrozenTick(final CallbackInfo ci) {
        this.sable$tickStopFreezing();
    }

    @Override
    public void sable$tickStopFreezing() {
        if (this.sable$frozenToSubLevel != null) {
            if (this.sable$frozenTicks++ > 160) {
                this.sable$freezeTo(null, null);
            }
        }
    }

    @Override
    public void sable$freezeTo(final UUID subLevelID, final Vector3dc localPosition) {
        this.sable$frozenToSubLevel = subLevelID;
        this.sable$frozenToSubLevelAnchor = localPosition;

        if (this.sable$frozenToSubLevel != null) {
            final SubLevelContainer container = SubLevelContainer.getContainer(this.level());
            assert container != null;

            final SubLevel subLevel = container.getSubLevel(this.sable$frozenToSubLevel);

            if (subLevel != null) {
                ((EntityMovementExtension) this).sable$setTrackingSubLevel(subLevel);
            }
        }
    }

    @Override
    public void sable$teleport() {
        if (this.sable$frozenToSubLevel != null) {
            final SubLevelContainer container = SubLevelContainer.getContainer(this.level());
            assert container != null;

            final SubLevel subLevel = container.getSubLevel(this.sable$frozenToSubLevel);

            if (subLevel != null) {
                final Vector3d newPos = subLevel.lastPose().transformPosition(new Vector3d(this.sable$frozenToSubLevelAnchor));
                this.setPos(newPos.x, newPos.y, newPos.z);
                ((EntityMovementExtension) this).sable$setTrackingSubLevel(subLevel);
                ((LivingEntityMovementExtension) this).sable$getInheritedVelocity().zero();
            }
        }
    }
}
