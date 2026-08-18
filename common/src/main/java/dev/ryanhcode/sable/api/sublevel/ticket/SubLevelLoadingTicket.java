package dev.ryanhcode.sable.api.sublevel.ticket;

import java.util.UUID;

public record SubLevelLoadingTicket<T>(SubLevelLoadingTicketType<T> type, UUID subLevelId, T key) {

    public String toCompactString() {
        return "Ticket[" + this.type.name() + " (" + this.key + ")]";
    }

    public String toString() {
        final String type = String.valueOf(this.type);
        return "SubLevelLoadingTicket[" + type + " " + this.subLevelId + " (" + this.key + ")]";
    }
}
