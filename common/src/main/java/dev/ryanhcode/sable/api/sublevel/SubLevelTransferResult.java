package dev.ryanhcode.sable.api.sublevel;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.util.Map;
import java.util.UUID;

/**
 * The replacement instances produced by a successful cross-level transfer.
 * Persistent UUIDs are preserved, but runtime IDs and Java object identities change.
 *
 * @param root         replacement for the requested root sub-level
 * @param replacements all replacements indexed by persistent UUID
 */
public record SubLevelTransferResult(ServerSubLevel root, Map<UUID, ServerSubLevel> replacements) {
    public SubLevelTransferResult {
        replacements = Map.copyOf(replacements);
    }
}
