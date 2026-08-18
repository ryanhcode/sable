package dev.ryanhcode.sable.command.argument.selector;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.commands.CommandSourceStack;

import java.util.Collection;
import java.util.List;

public class SubLevelTargetNone extends SubLevelTarget {
    public static SubLevelTargetNone INSTANCE = new SubLevelTargetNone();
    private SubLevelTargetNone() {}
    @Override
    public Collection<ServerSubLevel> getSubLevels(CommandSourceStack source) throws CommandSyntaxException {
        return List.of();
    }
}
