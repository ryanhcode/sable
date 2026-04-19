package dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create_connected.redstone_links;

import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;

/**
 * A wrapper of set of IRedstoneLinkable that can be used to avoid a ThreadLocal by attaching the level to the network.
 *
 */
public class LevelAttachedRedstoneLinkableNetwork extends HashSet<IRedstoneLinkable> {
    
    private final Level level;
    
    public LevelAttachedRedstoneLinkableNetwork(final Level level, final Set<IRedstoneLinkable> original) {
        super(original);
        this.level = level;
    }
    
    public Level getLevel() {
        return this.level;
    }
    
}
