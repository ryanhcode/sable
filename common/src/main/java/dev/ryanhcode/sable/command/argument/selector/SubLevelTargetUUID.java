package dev.ryanhcode.sable.command.argument.selector;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ryanhcode.sable.api.command.SubLevelArgumentType;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.commands.CommandSourceStack;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class SubLevelTargetUUID extends SubLevelTarget {
    private final UUID target;

    public SubLevelTargetUUID(final UUID target) {
        this.target = target;
    }

    @Override
    public Collection<ServerSubLevel> getSubLevels(final CommandSourceStack source) throws CommandSyntaxException {
        final ServerSubLevel subLevel = (ServerSubLevel) ServerSubLevelContainer.getContainer(source.getLevel()).getSubLevel(this.target);

        if (subLevel == null) {
            throw SubLevelArgumentType.ERROR_CANNOT_FIND_SUB_LEVEL.create();
        }

        return List.of(subLevel);
    }
}
