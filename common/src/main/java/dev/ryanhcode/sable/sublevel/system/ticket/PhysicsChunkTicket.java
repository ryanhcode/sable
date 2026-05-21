package dev.ryanhcode.sable.sublevel.system.ticket;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;
import java.util.Objects;

/**
 * A ticket for a chunk tracked by a {@link PhysicsChunkTicketManager}
 */
@ApiStatus.Internal
public final class PhysicsChunkTicket {
    private final SectionPos pos;
    private final Collection<SubLevel> residentSubLevels;
    private long lastInhabitedTick;

    /**
     * @param pos               the position of the chunk
     * @param lastInhabitedTick the last tick ({@link Level#getGameTime()}) the chunk was inhabited
     */
    public PhysicsChunkTicket(final SectionPos pos, final long lastInhabitedTick, final Collection<SubLevel> residentSubLevels) {
        this.pos = pos;
        this.lastInhabitedTick = lastInhabitedTick;
        this.residentSubLevels = residentSubLevels;
    }

    public SectionPos pos() {
        return this.pos;
    }

    public long lastInhabitedTick() {
        return this.lastInhabitedTick;
    }

    public void setLastInhabitedTick(final long lastInhabitedTick) {
        this.lastInhabitedTick = lastInhabitedTick;
    }

    public Collection<SubLevel> residentSubLevels() {
        return this.residentSubLevels;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        final var that = (PhysicsChunkTicket) obj;
        return Objects.equals(this.pos, that.pos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.pos, this.lastInhabitedTick);
    }

    @Override
    public String toString() {
        return "PhysicsChunkTicket[" +
                "pos=" + this.pos + ", " +
                "lastInhabitedTick=" + this.lastInhabitedTick + ", " +
                "residentSubLevels=" + this.residentSubLevels + ']';
    }
}
