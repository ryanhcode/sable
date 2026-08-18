package dev.ryanhcode.sable.mixin.command;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.command.argument.SubLevelSuggestionProvider;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ClientSuggestionProvider.class)
public class ClientSuggestionProviderMixin implements SubLevelSuggestionProvider {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Override
    public @Nullable SubLevel getSelectedSubLevel() {
        if (this.minecraft.hitResult == null) {
            return null;
        }
        return Sable.HELPER.getContainingClient(this.minecraft.hitResult.getLocation());
    }
}
