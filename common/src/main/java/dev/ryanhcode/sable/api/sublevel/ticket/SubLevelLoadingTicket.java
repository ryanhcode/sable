package dev.ryanhcode.sable.api.sublevel.ticket;

import java.util.Objects;
import java.util.UUID;

public final class SubLevelLoadingTicket<T> {
    private final SubLevelLoadingTicketType<T> type;
    private final UUID subLevelId;
    private final T key;

    public SubLevelLoadingTicket(final SubLevelLoadingTicketType<T> type, final UUID subLevelId, final T key) {
        this.subLevelId = subLevelId;
        this.type = type;
        this.key = key;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || this.getClass() != o.getClass()) return false;

        final SubLevelLoadingTicket<?> that = (SubLevelLoadingTicket<?>) o;
        return Objects.equals(this.type, that.type) && Objects.equals(this.subLevelId, that.subLevelId) && Objects.equals(this.key, that.key);
    }

    public String toString() {
        final String type = String.valueOf(this.type);
        return "SubLevelLoadingTicket[" + type + " " + this.subLevelId + " (" + this.key + ")]";
    }

    public SubLevelLoadingTicketType<T> getType() {
        return this.type;
    }

    public T getKey() {
        return this.key;
    }

    public UUID getSubLevelId() {
        return this.subLevelId;
    }
}
