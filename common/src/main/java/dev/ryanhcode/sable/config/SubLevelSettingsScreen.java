package dev.ryanhcode.sable.config;

import dev.ryanhcode.sable.SableServerConfig;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.physics.config.PhysicsConfigData;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.ModConfigSpec;

public class SubLevelSettingsScreen extends OptionsSubScreen {
    public static final Component TITLE = Component.translatable("options.sable_menu");

    public SubLevelSettingsScreen(final Screen optionsScreen, final Options options, final Component component) {
        super(optionsScreen, options, component);
    }

    @Override
    protected void addOptions() {
        final IntegratedServer singleplayerServer = this.minecraft.getSingleplayerServer();

        final ModConfigSpec.Range<?> range = SableServerConfig.SUB_LEVEL_SUBSTEPS_PER_TICK.getSpec().getRange();
        this.list.addBig(new OptionInstance<>(
                "options.physics_steps",
                OptionInstance.cachedConstantTooltip(Component.translatable("options.physics_steps.tooltip")),
                (component, substeps) -> Options.genericValueLabel(component, Component.translatable("options.physics_steps_template", substeps * 20)),
                new OptionInstance.IntRange((Integer) range.getMin(), (Integer) range.getMax(), false),
                SubLevelContainer.getContainer(singleplayerServer.overworld()).physicsSystem().getConfig().substepsPerTick,
                steps -> {
                    SableServerConfig.SUB_LEVEL_SUBSTEPS_PER_TICK.set(steps);
                    SableServerConfig.SPEC.save();
                    for (final ServerLevel level : singleplayerServer.getAllLevels()) {
                        final SubLevelPhysicsSystem physicsSystem = SubLevelContainer.getContainer(level).physicsSystem();
                        final PhysicsConfigData config = physicsSystem.getConfig();
                        config.updateFromConfig();
                        physicsSystem.getPipeline().updateConfigFrom(config);
                    }
                }
        ));
    }
}
