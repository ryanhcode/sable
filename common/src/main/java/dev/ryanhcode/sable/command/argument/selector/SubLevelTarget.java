package dev.ryanhcode.sable.command.argument.selector;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.commands.CommandSourceStack;

import java.util.Collection;

public abstract class SubLevelTarget {
    public abstract Collection<ServerSubLevel> getSubLevels(final CommandSourceStack source) throws CommandSyntaxException;
}