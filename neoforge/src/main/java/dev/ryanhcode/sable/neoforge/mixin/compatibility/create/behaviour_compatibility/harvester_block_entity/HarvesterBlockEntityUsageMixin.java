package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.behaviour_compatibility.harvester_block_entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.actors.harvester.HarvesterMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.behavior_compatibility.harvester_block_entity.DummyMovementContext;
import dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.harvester.HarvesterTicker;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HarvesterMovementBehaviour.class)
public class HarvesterBlockEntityUsageMixin {

    @WrapOperation(method = "lambda$visitNewPosition$0", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/actors/harvester/HarvesterMovementBehaviour;collectOrDropItem(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;Lnet/minecraft/world/item/ItemStack;)V"))
    public void sable$replaceDropItem(final HarvesterMovementBehaviour instance, final MovementContext movementContext, final ItemStack itemStack, final Operation<Void> original) {
        if (movementContext instanceof DummyMovementContext) {
            HarvesterTicker.dropItem(movementContext.world, itemStack, movementContext.localPos);
        } else {
            original.call(instance, movementContext, itemStack);
        }
    }
}
