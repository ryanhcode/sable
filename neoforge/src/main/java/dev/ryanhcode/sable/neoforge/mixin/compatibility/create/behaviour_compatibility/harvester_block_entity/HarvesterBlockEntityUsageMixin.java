package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.behaviour_compatibility.harvester_block_entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.actors.harvester.HarvesterMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.harvester.HarvesterMovementBehaviourExtension;
import dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.harvester.HarvesterTicker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

@Mixin(HarvesterMovementBehaviour.class)
public class HarvesterBlockEntityUsageMixin implements HarvesterMovementBehaviourExtension {

    @Unique
    private Reference<Level> sable$manualLevel;

    @Unique
    private BlockPos sable$selfPos = null;

    @Override
    public BlockPos sable$getSelfPos() {
        return this.sable$selfPos;
    }

    @Override
    public void sable$setSelfPos(final BlockPos sable$selfPos) {
        this.sable$selfPos = sable$selfPos;
    }

    @Override
    public Level sable$getManualLevel() {
        return this.sable$manualLevel.get();
    }

    @Override
    public void sable$setManualLevel(final Level level) {
        this.sable$manualLevel = new WeakReference<>(level);
    }

    @Redirect(method = "visitNewPosition", at = @At(value = "FIELD", target = "Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;world:Lnet/minecraft/world/level/Level;", opcode = Opcodes.GETFIELD))
    public Level sable$replaceWorld(final MovementContext instance) {
        if (instance == null) { // we're only going to be passing in null from our mixin, so this is valid
            return this.sable$getManualLevel();
        } else {
            return instance.world;
        }
    }

    @WrapOperation(method = "lambda$visitNewPosition$0", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/actors/harvester/HarvesterMovementBehaviour;collectOrDropItem(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;Lnet/minecraft/world/item/ItemStack;)V"))
    public void sable$replaceDropItem(final HarvesterMovementBehaviour instance, final MovementContext movementContext, final ItemStack itemStack, final Operation<Void> original) {
        if (movementContext == null) {
            if (this.sable$getManualLevel() != null && this.sable$getSelfPos() != null) {
                HarvesterTicker.dropItem(this.sable$getManualLevel(), itemStack, this.sable$getSelfPos());
            }
        } else {
            original.call(instance, movementContext, itemStack);
        }
    }
}
