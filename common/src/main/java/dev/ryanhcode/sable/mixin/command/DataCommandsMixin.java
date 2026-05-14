package dev.ryanhcode.sable.mixin.command;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.command.data_accessor.SubLevelDataAccessor;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.commands.data.DataCommands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Function;

@Mixin(DataCommands.class)
public class DataCommandsMixin {

    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableList;of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Lcom/google/common/collect/ImmutableList;", remap = false))
    private static ImmutableList<Function<String, DataCommands.DataProvider>> sable$allProviders(final ImmutableList<Function<String, DataCommands.DataProvider>> providers) {
        final ObjectArrayList<Function<String, DataCommands.DataProvider>> mutableList = new ObjectArrayList<>(providers);
        mutableList.add(SubLevelDataAccessor.PROVIDER);

        return ImmutableList.copyOf(mutableList);
    }
}
