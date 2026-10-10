package dev.ryanhcode.sable.api.sublevel.ticket;

import java.util.UUID;

/**
 * A request to keep a specific sub-level loaded, scoped to a {@link SubLevelLoadingTicketType}. Multiple tickets can
 * reference the same sub-level with different keys.
 */
public record SubLevelLoadingTicket<T>(SubLevelLoadingTicketType<T> type, UUID subLevelId, T key) {

    public String toCompactString() {
        return "Ticket[" + this.type.name() + " (" + this.key + ")]";
    }

    public String toString() {
        final String type = String.valueOf(this.type);
        return "SubLevelLoadingTicket[" + type + " " + this.subLevelId + " (" + this.key + ")]";
    }
}
