package dev.ryanhcode.sable.mixin.command;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.command.data_accessor.SubLevelDataAccessor;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.commands.data.DataCommands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Function;

/**
 * Mixin into {@link net.minecraft.server.commands.data.DataCommands} to register the sub-level data accessor provider.
 */
@Mixin(DataCommands.class)
public class DataCommandsMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableList;of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Lcom/google/common/collect/ImmutableList;", remap = false))
    private static <E> ImmutableList<Function<String, DataCommands.DataProvider>> sable$allProviders(final E e1, final E e2, final E e3, final Operation<ImmutableList<Function<String, DataCommands.DataProvider>>> original) {
        @SuppressWarnings("unchecked")
        final ImmutableList<Function<String, DataCommands.DataProvider>> providers = (ImmutableList<Function<String, DataCommands.DataProvider>>) ((Operation) original).call(e1, e2, e3);
        final ObjectArrayList<Function<String, DataCommands.DataProvider>> mutableList = new ObjectArrayList<>(providers);
        mutableList.add(SubLevelDataAccessor.PROVIDER);

        return  ImmutableList.copyOf(mutableList);
    }
}
