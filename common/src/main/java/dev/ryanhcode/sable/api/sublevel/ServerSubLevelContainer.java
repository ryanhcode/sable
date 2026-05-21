package dev.ryanhcode.sable.api.sublevel;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicket;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelTicketInfo;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelOccupancySavedData;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.SubLevelTicketsSavedData;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.sublevel.system.SubLevelTrackingSystem;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.*;

/**
 * Holds all sub-levels and plots in a {@link ServerLevel}
 */
public class ServerSubLevelContainer extends SubLevelContainer {

    /**
     * The physics system in this container
     */
    private @Nullable SubLevelPhysicsSystem physics;

    /**
     * The tracking system in this container
     */
    private @Nullable SubLevelTrackingSystem tracking;

    /**
     * The holding chunk map for this sub-level container.
     */
    private SubLevelHoldingChunkMap holdingChunkMap;

    /**
     * All active sub-level loading tickets
     */
    protected final Object2ObjectMap<ServerSubLevel, ObjectSet<SubLevelLoadingTicket<?>>> activeTickets = new Object2ObjectOpenHashMap<>();

    /**
     * All sub-level loading tickets
     */
    protected final Object2ObjectMap<UUID, SubLevelTicketInfo> allTickets = new Object2ObjectOpenHashMap<>();

    /**
     * Creates a new sub-level container with the given side length and plot size.
     *
     * @param level         the level of the plotgrid
     * @param logSideLength the log_2 of the amount of chunks in the side of the plotgrid
     * @param logPlotSize   the log_2 of the amount of chunks in the side of a plot
     * @param originX       the X coordinate in plots of the origin of the plotgrid
     * @param originZ       the Z coordinate in plots of the origin of the plotgrid
     */
    public ServerSubLevelContainer(final Level level, final int logSideLength, final int logPlotSize, final int originX, final int originZ) {
        super(level, logSideLength, logPlotSize, originX, originZ);
    }

    /**
     * Initialize after method construction is done
     */
    public void initialize() {
        this.holdingChunkMap = new SubLevelHoldingChunkMap(this.getLevel(), this);

        this.loadForceLoadedSubLevels();
    }

    /**
     * Called every tick for the plotgrid.
     */
    @Override
    public void tick() {
        super.tick();
        this.holdingChunkMap.processChanges();
    }

    /**
     * Sets the internal physics system.
     */
    @ApiStatus.Internal
    public void takePhysicsSystem(final SubLevelPhysicsSystem physics) {
        this.physics = physics;
    }

    /**
     * Sets the internal tracking system.
     */
    @ApiStatus.Internal
    public void takeTrackingSystem(final SubLevelTrackingSystem tracking) {
        this.tracking = tracking;
    }

    /**
     * @return the physics pipeline in this container
     */
    public @NotNull SubLevelPhysicsSystem physicsSystem() {
        assert this.physics != null;
        return this.physics;
    }

    /**
     * @return the physics pipeline in this container
     */
    public @NotNull SubLevelTrackingSystem trackingSystem() {
        assert this.tracking != null;
        return this.tracking;
    }

    /**
     * Removes a sub-level with a local plot coordinate
     */
    @Override
    public void removeSubLevel(final int x, final int z, final SubLevelRemovalReason reason) {
        final ServerSubLevel subLevel = (ServerSubLevel) this.getSubLevel(x, z);
        if (subLevel == null) {
            throw new IllegalStateException("No sub-level at " + x + ", " + z);
        }

        if (reason == SubLevelRemovalReason.REMOVED) {
            subLevel.deleteAllEntities();
        }

        super.removeSubLevel(x, z, reason);

        if (reason == SubLevelRemovalReason.REMOVED) {
            final ServerLevel level = this.getLevel();
            SubLevelOccupancySavedData.getOrLoad(level).setDirty();
            this.holdingChunkMap.queueDeletion(subLevel);
        }
    }

    @Override
    protected SubLevel createSubLevel(final int globalPlotX, final int globalPlotZ, final Pose3d pose, final UUID uuid) {
        final ServerLevel level = this.getLevel();
        final ServerSubLevel subLevel = new ServerSubLevel(level, globalPlotX, globalPlotZ, pose);
        subLevel.setUniqueId(uuid);

        final Vector3d position = pose.position();
        final BlockPos blockPos = BlockPos.containing(position.x, position.y, position.z);

        if (level.isLoaded(blockPos)) {
            final Holder<Biome> holder = level.getBiome(blockPos);
            final Optional<ResourceKey<Biome>> key = holder.unwrapKey();

            //noinspection OptionalIsPresent
            if (key.isPresent()) {
                subLevel.getPlot().setBiome(key.get());
            }
        }

        return subLevel;
    }

    public SubLevelHoldingChunkMap getHoldingChunkMap() {
        return this.holdingChunkMap;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<ServerSubLevel> getAllSubLevels() {
        return (List<ServerSubLevel>) super.getAllSubLevels();
    }

    /**
     * @return the level of the plotgrid.
     */
    @Override
    public ServerLevel getLevel() {
        return (ServerLevel) super.getLevel();
    }

    /**
     * Adds a sub-level force-loading ticket
     *
     * @param subLevel the loaded sub-level to add the ticket to
     * @param ticketType the type of ticket to add to the sub-level
     * @param key the key of the ticket. This will be used to identify the ticket to remove it.
     *            Two tickets with the same key on the same sub-level cannot exist
     *
     * @return true if the ticket was added (and did not previously exist)
     */
    public <T> boolean addForceLoadTicket(final ServerSubLevel subLevel, final SubLevelLoadingTicketType<T> ticketType, final T key) {
        final UUID uuid = subLevel.getUniqueId();
        final SubLevelLoadingTicket<T> ticket = new SubLevelLoadingTicket<>(ticketType, uuid, key);

        final ObjectSet<SubLevelLoadingTicket<?>> loadedSet = this.activeTickets.computeIfAbsent(subLevel, (ignored) -> new ObjectArraySet<>());
        final SubLevelTicketInfo allSet = this.allTickets.computeIfAbsent(uuid, (ignored) -> new SubLevelTicketInfo());
        loadedSet.add(ticket);

        if (allSet.tickets().add(ticket)) {
            SubLevelTicketsSavedData.getOrLoad(this.getLevel()).setDirty();
            return true;
        }

        return false;
    }

    /**
     * Removes a sub-level force-loading ticket
     *
     * @param subLevel the loaded sub-level to remove the ticket from
     * @param ticketType the type of ticket to add to the sub-level
     * @param key the key of the ticket. This will be used to identify the ticket to remove it.
     *            Two tickets with the same key on the same sub-level cannot exist
     *
     * @return true if the ticket existed and was removed
     */
    public <T> boolean removeForceLoadTicket(final ServerSubLevel subLevel, final SubLevelLoadingTicketType<T> ticketType, final T key) {
        final UUID uuid = subLevel.getUniqueId();
        final SubLevelLoadingTicket<T> ticket = new SubLevelLoadingTicket<>(ticketType, uuid, key);

        final ObjectSet<SubLevelLoadingTicket<?>> loadedSet = this.activeTickets.get(subLevel);
        final SubLevelTicketInfo allSet = this.allTickets.get(subLevel.getUniqueId());

        if (loadedSet != null) {
            loadedSet.remove(ticket);

            if (loadedSet.isEmpty()) {
                this.activeTickets.remove(subLevel);
            }
        }

        if (allSet != null) {
            final boolean existed = allSet.tickets().remove(ticket);

            if (allSet.tickets().isEmpty()) {
                this.allTickets.remove(subLevel.getUniqueId());
            }

            if (existed) {
                SubLevelTicketsSavedData.getOrLoad(this.getLevel()).setDirty();
                return true;
            }
        }

        return false;
    }

    /**
     * Collect all force-loaded sub-levels (and sub-levels force-loaded through dependencies)
     */
    public Collection<ServerSubLevel> collectForceLoadedSubLevels() {
        if (this.activeTickets.isEmpty()) {
            return List.of();
        }
        final ObjectOpenHashSet<ServerSubLevel> subLevels = new ObjectOpenHashSet<>();

        for (final ServerSubLevel subLevel : this.activeTickets.keySet()) {
            if (subLevels.contains(subLevel)) {
                continue;
            }

            subLevels.addAll(SubLevelHelper.getLoadingDependencyChain(subLevel));
        }

        return subLevels;
    }

    /**
     * Loads sub-level tickets
     */
    @ApiStatus.Internal
    public void loadTickets(final Object2ObjectMap<UUID, SubLevelTicketInfo> tickets) {
        this.allTickets.putAll(tickets);
    }

    /**
     * @return an immutable view of all sub-level loading tickets
     */
    @ApiStatus.Internal
    public Map<UUID, SubLevelTicketInfo> getAllTickets() {
        return Collections.unmodifiableMap(this.allTickets);
    }

    /**
     * Loads all force-loaded sub-levels
     */
    private void loadForceLoadedSubLevels() {
        for (final Map.Entry<UUID, SubLevelTicketInfo> entry : this.allTickets.entrySet()) {
            final UUID uuid = entry.getKey();
            final GlobalSavedSubLevelPointer pointer = entry.getValue().getPointer();

            if (pointer != null) {
                this.holdingChunkMap.snatchAndLoad(pointer, uuid);
            } else {
                Sable.LOGGER.error("Cannot load force-loaded sub-level with ID {} because the ticket info was not saved with a pointer", uuid);
            }
        }
    }

    /**
     * Frees all native resources
     */
    @ApiStatus.Internal
    public void close() {
        final List<ServerSubLevel> subLevels = new ObjectArrayList<>(this.getAllSubLevels());

        if (!subLevels.isEmpty()) {
            final Map<UUID, SubLevelTicketInfo> tickets = this.getAllTickets();

            for (final ServerSubLevel subLevel : subLevels) {
                if (SableConfig.VERBOSE_SERIALIZATION_LOGGING.get() && !tickets.containsKey(subLevel.getUniqueId())) {
                    Sable.LOGGER.error("Sub-level {} was present after world closing, but is not force-loaded.", subLevel);
                }

                this.removeSubLevel(subLevel, SubLevelRemovalReason.UNLOADED);
            }
        }

        if (this.physics != null) {
            this.physics.getPipeline().dispose();
        }

        try {
            this.holdingChunkMap.close();
        } catch (final Exception e) {
            Sable.LOGGER.error("Failed closing sub-level holding chunk map", e);
        }
    }
}
