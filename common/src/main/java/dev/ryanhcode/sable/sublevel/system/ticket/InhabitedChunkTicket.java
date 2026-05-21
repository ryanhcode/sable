package dev.ryanhcode.sable.sublevel.system.ticket;

import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.ApiStatus;

import java.util.UUID;

/**
 * A ticket for a chunk force-loaded by a sub-level in the {@link PhysicsChunkTicketManager}
 */
@ApiStatus.Internal
public final class InhabitedChunkTicket {
    private final UUID uuid;
    private final Ticket<UUID> ticket;
    private long lastInhabitedTick;

    /**
     * @param pos               the position of the chunk
     * @param lastInhabitedTick the last tick ({@link Level#getGameTime()}) the chunk was inhabited
     */
    public InhabitedChunkTicket(final UUID uuid, final long lastInhabitedTick, final Ticket<UUID> ticket) {
        this.uuid = uuid;
        this.lastInhabitedTick = lastInhabitedTick;
        this.ticket = ticket;
    }

    public long lastInhabitedTick() {
        return this.lastInhabitedTick;
    }

    public void setLastInhabitedTick(final long lastInhabitedTick) {
        this.lastInhabitedTick = lastInhabitedTick;
    }

    public Ticket<UUID> getTicket() {
        return this.ticket;
    }

    @Override
    public int hashCode() {
        return this.uuid.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof final InhabitedChunkTicket other) {
            return this.uuid.equals(other.uuid);
        }

        return false;
    }
}
