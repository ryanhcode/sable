package dev.ryanhcode.sable.command.argument;

import dev.ryanhcode.sable.sublevel.SubLevel;
import org.jetbrains.annotations.Nullable;

public interface SubLevelSuggestionProvider {
    default @Nullable SubLevel getSelectedSubLevel() {
        return null;
    }
}
