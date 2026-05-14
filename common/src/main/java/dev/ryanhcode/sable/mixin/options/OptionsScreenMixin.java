package dev.ryanhcode.sable.mixin.options;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.ryanhcode.sable.config.SubLevelSettingsScreen;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Adds a button to access the sable menu on integrated servers to the {@link OptionsScreen}
 */
@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen {

    @Shadow @Final private Options options;

    protected OptionsScreenMixin(final Component component) {
        super(component);
    }

    @ModifyReturnValue(method = "createOnlineButton", at = @At("RETURN"))
    public LayoutElement sable$createSableButton(final LayoutElement original) {
        if (this.minecraft.level == null || !this.minecraft.hasSingleplayerServer()) {
            return original;
        }

        final LinearLayout layout = LinearLayout.vertical();

        final Button sableButton = Button.builder(SubLevelSettingsScreen.TITLE, (event) -> {
            this.minecraft.setScreen(new SubLevelSettingsScreen(this, this.options, SubLevelSettingsScreen.TITLE));
        }).pos(0, 30).size(150, 20).build();

        layout.addChild(original);
        layout.spacing(5);
        layout.addChild(sableButton);
        return original;
    }


}
