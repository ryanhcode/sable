package dev.ryanhcode.sable.api.sublevel.ticket;

import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class SubLevelTicketInfo {
    private final ObjectSet<SubLevelLoadingTicket<?>> tickets = new ObjectArraySet<>();

    @Nullable
    private GlobalSavedSubLevelPointer pointer = null;

    public SubLevelTicketInfo() {}

    public SubLevelTicketInfo(final GlobalSavedSubLevelPointer pointer, final ObjectSet<SubLevelLoadingTicket<?>> tickets) {
        this.pointer = pointer;
        this.tickets.addAll(tickets);
    }

    public @Nullable GlobalSavedSubLevelPointer getPointer() {
        return this.pointer;
    }

    public void setPointer(@Nullable final GlobalSavedSubLevelPointer pointer) {
        this.pointer = pointer;
    }

    public ObjectSet<SubLevelLoadingTicket<?>> tickets() {
        return this.tickets;
    }
}
