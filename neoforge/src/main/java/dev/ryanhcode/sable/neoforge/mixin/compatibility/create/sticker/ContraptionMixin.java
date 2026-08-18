package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.sticker;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.Contraption;
import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.create.StickerBlockEntityExtension;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Contraption.class)
public class ContraptionMixin {

    @WrapOperation(method = "getBlockEntityNBT", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BlockEntity;saveWithFullMetadata(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;"))
    private CompoundTag sable$saveStickerNBT(final BlockEntity instance, final HolderLookup.Provider registries, final Operation<CompoundTag> original) {
        if (instance instanceof final StickerBlockEntityExtension extension) {
            extension.sable$saveToContraption(registries);
        }
        return original.call(instance, registries);
    }


}
