package dev.ryanhcode.sable.sublevel.teleport;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.EntityStickExtension;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundStopTrackingSubLevelPacket;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.compatibility.ContraptionCompat;
import dev.ryanhcode.sable.sublevel.storage.SubLevelOccupancySavedData;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.*;

public final class SubLevelDimensionTeleport {

    private SubLevelDimensionTeleport() {}

    private record CapturedEntity(CompoundTag nbt, @Nullable Vec3 plotLocalPos, Vec3 worldVelocity,
                                  String typeName, String className, UUID sourceUuid, boolean isContraption,
                                  UUID familyMemberId) {}
    private record CapturedPlayer(ServerPlayer player, Vec3 sourceWorldPos, float yRot, float xRot) {}
    private record PlotTranslation(int minX, int maxX, int minZ, int maxZ, long offsetX, long offsetZ) {}
    private record CapturedRope(
            int pointCount,
            double collisionRadius,
            UUID startSubLevelId,
            Vector3d startLocalOffset,
            UUID endSubLevelId,
            Vector3d endLocalOffset
    ) {}

    /**
     *
     * @param source
     * @param targetLevel
     * @param targetPosition
     * @param targetOrientation
     * @return
     */
    public static @Nullable ServerSubLevel teleport(
            final ServerSubLevel source,
            final ServerLevel targetLevel,
            final Vector3dc targetPosition,
            final @Nullable Quaterniondc targetOrientation
    ) {
        final ServerLevel sourceLevel = source.getLevel();//Source Body
        final ServerSubLevelContainer sourceContainer = SubLevelContainer.getContainer(sourceLevel);//Current Dimensiom
        final ServerSubLevelContainer targetContainer = SubLevelContainer.getContainer(targetLevel);//Target Dimension

        if (sourceContainer == null || targetContainer == null) return null;

        // 2. Resolve the Root Source instantly
        UUID rootId = source.sable$getRootParentId();

        if (rootId == null) {
            rootId = resolveLegacyRoot(source, sourceContainer);
            source.sable$setRootParentId(rootId);
        }

        final ServerSubLevel rootSource = (ServerSubLevel) sourceContainer.getSubLevel(rootId);

        if (rootSource == null) {
            return null; // Failsafe
        }

        final UUID sourceUuid = rootSource.getUniqueId();

        // 3. Check for same-dimension teleport
        if (sourceLevel == targetLevel) {
            return sameDimTeleport(rootSource, sourceLevel, targetPosition, targetOrientation);
        }

        //Collect family
        final List<ServerSubLevel> family = new ArrayList<>();
        family.add(rootSource);

        final List<ServerSubLevel> collected = collectFamilyTransitive(rootSource, sourceContainer);
        for (final ServerSubLevel member : collected) {
            if (!member.getUniqueId().equals(sourceUuid)) {
                family.add(member);
            }
        }

        Sable.LOGGER.info("Teleporting family of {} sub-level(s) (main + {} children)", family.size(), family.size() - 1);

        final Map<UUID, CompoundTag> savedPlots = new HashMap<>();
        final Map<UUID, Pose3d> savedPoses = new HashMap<>();
        final Map<UUID, Vector3d> savedLinVel = new HashMap<>();
        final Map<UUID, Vector3d> savedAngVel = new HashMap<>();
        final Map<UUID, String> savedNames = new HashMap<>();
        final Map<UUID, CompoundTag> savedUserData = new HashMap<>();

        for (final ServerSubLevel member : family) {
            savedPlots.put(member.getUniqueId(), member.getPlot().save());
            savedPoses.put(member.getUniqueId(), new Pose3d(member.logicalPose()));

            final RigidBodyHandle h = RigidBodyHandle.of(member);

            final Vector3d lv = new Vector3d();
            final Vector3d av = new Vector3d();
            h.getLinearVelocity(lv);
            h.getAngularVelocity(av);
            savedLinVel.put(member.getUniqueId(), lv);
            savedAngVel.put(member.getUniqueId(), av);

            savedNames.put(member.getUniqueId(), member.getName());
            savedUserData.put(member.getUniqueId(), member.getUserDataTag());
        }

        final List<CapturedPlayer> capturedPlayers = capturePlayersInBounds(sourceLevel, family);
        final List<CapturedEntity> capturedEntities = captureEntitiesInBoundsForFamily(sourceLevel, family, sourceContainer);
        final List<CapturedRope> capturedRopes = captureRopesForFamily(
                sourceContainer.physicsSystem(), family);


        final List<Vector2i> targetPlots = new ArrayList<>();
        final Set<Long> claimedSlots = new HashSet<>();

        for (int i = 0; i < family.size(); i++) {

            final Vector2i p = findFirstFreePlotExcluding(targetContainer, claimedSlots);

            if (p == null) {
                Sable.LOGGER.error("Not enough free plots for family of {}", family.size());
                return null;
            }

            final long key = ((long) p.x << 32) | (p.y & 0xFFFFFFFFL);
            claimedSlots.add(key);
            targetPlots.add(p);

        }

        for (final ServerSubLevel sub : family) {

            final ServerLevelPlot p = sub.getPlot();
            final Vector2i origin = sourceContainer.getOrigin();
            final int localX = p.plotPos.x - origin.x;
            final int localZ = p.plotPos.z - origin.y;

            sourceContainer.removeSubLevel(sub, SubLevelRemovalReason.UNLOADED);
            sourceContainer.getOccupancy().clear(sourceContainer.getIndex(localX, localZ));
            SubLevelOccupancySavedData.getOrLoad(sourceLevel).setDirty();

            try {
                sourceContainer.getHoldingChunkMap().queueDeletion(sub);
            } catch (final Exception e) {
                Sable.LOGGER.error("Failed to queue deletion for {}", sub.getUniqueId(), e);
            }
        }


        final int logPlotSize = sourceContainer.getLogPlotSize();
        final int blockShift = logPlotSize + 4;
        final int plotSizeBlocks = 1 << blockShift;
        final int sectionShift = (sourceLevel.getMinBuildHeight() - targetLevel.getMinBuildHeight()) >> 4;

        final List<PlotTranslation> translations = new ArrayList<>();

        for (int i = 0; i < family.size(); i++) {

            final ServerLevelPlot plot = family.get(i).getPlot();
            final Vector2i targetPlotCoord = targetPlots.get(i);

            final long offsetX = ((long)(targetPlotCoord.x + targetContainer.getOrigin().x) - plot.plotPos.x) << blockShift;
            final long offsetZ = ((long)(targetPlotCoord.y + targetContainer.getOrigin().y) - plot.plotPos.z) << blockShift;
            final int minX = plot.plotPos.x << blockShift;
            final int maxX = minX + plotSizeBlocks - 1;
            final int minZ = plot.plotPos.z << blockShift;
            final int maxZ = minZ + plotSizeBlocks - 1;

            translations.add(new PlotTranslation(minX, maxX, minZ, maxZ, offsetX, offsetZ));
        }

        final Pose3d mainSourcePose = savedPoses.get(sourceUuid);

        //Load every family member
        ServerSubLevel destination = null;
        final Map<UUID, PlotTranslation> translationsByMember = new HashMap<>();
        final Map<UUID, Pose3d> targetPosesByMember = new HashMap<>();
        final Map<UUID, ServerSubLevel> oldUuidToNewMember = new HashMap<>();


        for (int i = 0; i < family.size(); i++) {

            final ServerSubLevel member = family.get(i);
            final Vector2i targetPlotCoord = targetPlots.get(i);
            final UUID memberId = member.getUniqueId();
            final PlotTranslation childTranslation = translations.get(i);

            final ServerSubLevel newMember = (ServerSubLevel) targetContainer.allocateSubLevel(
                    memberId, targetPlotCoord.x, targetPlotCoord.y, savedPoses.get(memberId));
            //Could be removed for debugging
            if (newMember == null) {
                Sable.LOGGER.error("Failed to allocate family member {}", memberId);
                continue;
            }

            if (i == 0) destination = newMember;

            final CompoundTag childTag = savedPlots.get(memberId).copy();
            if (sectionShift != 0) rewriteSectionIndices(childTag, sectionShift);

            rewriteBlockEntityPositions(childTag, childTranslation.offsetX(), childTranslation.offsetZ());
            rewriteInternalBlockPosRefs(childTag, translations);

            if (childTag.contains("Contraption")) {
                rewriteContraptionTagAnchorsUniversal(childTag.getCompound("Contraption"), translations);
            }

            final CompoundTag meta = childTag.copy();
            meta.putInt("plot_x", targetPlotCoord.x);
            meta.putInt("plot_z", targetPlotCoord.y);
            try {
                newMember.getPlot().load(meta);
            } catch (final Exception e) {
                Sable.LOGGER.error("plot.load failed for {}", memberId, e);
            }

            //Poses and rotation
            final Pose3d sourcePose = savedPoses.get(memberId);
            final Vector3d relPos = new Vector3d(sourcePose.position()).sub(mainSourcePose.position());

            Quaterniond delta = null;
            if (targetOrientation != null) {
                delta = new Quaterniond(targetOrientation)
                        .mul(new Quaterniond(mainSourcePose.orientation()).invert());
            }

            final Quaterniondc childTargetOrient;
            if (delta != null) {
                delta.transform(relPos);
                childTargetOrient = new Quaterniond(delta).mul(new Quaterniond(sourcePose.orientation()));
            } else {
                childTargetOrient = sourcePose.orientation();
            }

            final Vector3d childTargetPos = new Vector3d(targetPosition).add(relPos);

            final SubLevelPhysicsSystem targetPhysics = targetContainer.physicsSystem();
            targetPhysics.getPipeline().resetVelocity(newMember);
            targetPhysics.getPipeline().teleport(newMember, childTargetPos, childTargetOrient);
            newMember.logicalPose().position().set(childTargetPos);
            newMember.logicalPose().orientation().set(childTargetOrient);
            newMember.updateLastPose();
            newMember.updateBoundingBox();


            translationsByMember.put(memberId, childTranslation);
            targetPosesByMember.put(memberId, new Pose3d(newMember.logicalPose()));
            oldUuidToNewMember.put(memberId, newMember);


            final Vector3d lv = savedLinVel.get(memberId);
            final Vector3d av = savedAngVel.get(memberId);
            /*if (lv != null && av != null) {
                final Vector3d lvOut = new Vector3d(lv);
                final Vector3d avOut = new Vector3d(av);
                if (delta != null) {
                    delta.transform(lvOut);
                    delta.transform(avOut);
                }
                //targetPhysics.getPipeline().addLinearAndAngularVelocity(newMember, lvOut, avOut);
            }*/
            //Allow for physics transfer across dimensions
            if (savedNames.get(memberId) != null) newMember.setName(savedNames.get(memberId));
            if (savedUserData.get(memberId) != null) newMember.setUserDataTag(savedUserData.get(memberId));
            newMember.updateBoundingBox();


            targetContainer.trackingSystem().onSubLevelAdded(newMember);
            targetPhysics.getPipeline().wakeUp(newMember);


        }


        if (destination == null) return null;
        //Dont know how good this is working.
        // 1. Rebuild Ropes first so the server physics engine is fully linked up
        respawnCapturedRopes(targetContainer.physicsSystem(), capturedRopes, oldUuidToNewMember);

        // 2. Respawn Entities
        respawnCapturedEntities(targetLevel, capturedEntities, translationsByMember, savedPoses, targetPosesByMember);
        // Force all tracking clients to drop source sub-level visuals
        for (final ServerSubLevel oldMember : family) {
            final ChunkPos plotPos = oldMember.getPlot().plotPos;
            final Vector2i origin = sourceContainer.getOrigin();
            final long l = ChunkPos.asLong(plotPos.x - origin.x, plotPos.z - origin.y);

            for (final UUID trackingUuid : oldMember.getTrackingPlayers()) {
                final ServerPlayer trackingPlayer = sourceLevel.getServer().getPlayerList().getPlayer(trackingUuid);
                if (trackingPlayer != null) {
                    trackingPlayer.connection.send(
                            new ClientboundCustomPayloadPacket(
                                    new ClientboundStopTrackingSubLevelPacket(l)
                            )
                    );
                }
            }
            oldMember.getTrackingPlayers().clear();
        }
        // 3. Teleport the Players
        teleportCapturedPlayers(capturedPlayers, targetLevel, mainSourcePose, destination.logicalPose());
        Sable.LOGGER.info("Successfully teleported family of {} sub-level(s)", family.size());
        return destination;
    }

    /**
     * Walks up the parent chain for sub-levels missing a cached root ID.
     * @param target
     * @param container
     * @return
     */
    private static UUID resolveLegacyRoot(final ServerSubLevel target, final ServerSubLevelContainer container) {
        ServerSubLevel current = target;
        boolean foundParent = true;

        final Set<UUID> climbingHistory = new HashSet<>();
        climbingHistory.add(current.getUniqueId());

        while (foundParent) {
            foundParent = false;
            for (final SubLevel potentialParentObj : container.getAllSubLevels()) {
                if (!(potentialParentObj instanceof ServerSubLevel potentialParent)) continue;
                if (potentialParent == current) continue;

                // Inside resolveLegacyRoot, around line 204:
                final Set<UUID> children = collectDirectChildIds(potentialParent, container);
                if (children.contains(current.getUniqueId())) {
                    if (climbingHistory.add(potentialParent.getUniqueId())) {
                        current = potentialParent;
                        foundParent = true;
                        current.sable$setRootParentId(null);
                        break;
                    } else {
                        // Loop detection stopper.
                        break;
                    }
                }
            }
        }
        return current.getUniqueId();
    }

    /**
     * Linear scan of the target container's occupancy grid. Returns the first unoccupied slot not in the exclusion set.
     * @param container
     * @param claimed
     * @return
     */
    private static Vector2i findFirstFreePlotExcluding(ServerSubLevelContainer container, Set<Long> claimed) {
        final int sideLength = 1 << container.getLogSideLength();
        for (int x = 0; x < sideLength; x++) {
            for (int z = 0; z < sideLength; z++) {
                long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
                if (claimed.contains(key)) continue;
                if (!container.getOccupancy().get(container.getIndex(x, z))) {
                    return new Vector2i(x, z);
                }
            }
        }
        return null;
    }


    /**
     * DFS from root.
     * Discovers children two ways:
     * UUIDs found in NBT data, and rope endpoints that land inside another sub-level's bounding box.
     * @param root
     * @param container
     * @return
     */
    private static List<ServerSubLevel> collectFamilyTransitive(final ServerSubLevel root, final ServerSubLevelContainer container) {
        final List<ServerSubLevel> family = new ArrayList<>();
        final Set<UUID> visited = new HashSet<>();
        final Deque<ServerSubLevel> stack = new ArrayDeque<>();

        stack.push(root);

        while (!stack.isEmpty()) {
            final ServerSubLevel current = stack.pop();
            if (!visited.add(current.getUniqueId())) continue;

            family.add(current);

            // --- 1. NBT Discovery ---
            final Set<UUID> directChildren = collectDirectChildIds(current, container);
            for (final UUID childId : directChildren) {
                if (!visited.contains(childId)) {
                    final SubLevel maybe = container.getSubLevel(childId);
                    if (maybe instanceof ServerSubLevel childSubLevel) {
                        stack.push(childSubLevel);
                    }
                }
            }

            // --- 2. Manual Spatial Discovery (Rope Tethers) ---
            final SubLevelPhysicsSystem physics = container.physicsSystem();

            for (final dev.ryanhcode.sable.api.physics.object.ArbitraryPhysicsObject obj : physics.getArbitraryObjects()) {
                if (obj instanceof dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject rope) {
                    if (!rope.isActive()) continue;

                    // Check if rope starts on current ship
                    if (rope.getStartAttachmentSubLevel() == current) {
                        final List<Vector3d> points = rope.getPoints();
                        if (points.size() > 1) {
                            // Grab the end point where the other ship should be
                            Vector3d endPoint = points.get(points.size() - 1);

                            // Manual Sweep: Check every ship in the container
                            for (final SubLevel potential : container.getAllSubLevels()) {
                                if (!(potential instanceof ServerSubLevel ship)) continue;
                                if (visited.contains(ship.getUniqueId())) continue;

                                // Check if the rope point is inside the ship's physical bounds
                                if (ship.boundingBox().contains(endPoint.x, endPoint.y, endPoint.z)) {
                                    stack.push(ship);
                                    break; // Found the neighbor for this rope
                                }
                            }
                        }
                    }
                }
            }
        }
        return family;
    }

    /**
     * Scans a sub-level's plot save data and user data tag for UUIDs referencing other live sub-levels.
     * @param subLevel
     * @param container
     * @return
     */
    private static Set<UUID> collectDirectChildIds(final ServerSubLevel subLevel, final ServerSubLevelContainer container) {
        final Set<UUID> ids = new HashSet<>();

        // Scan the plot save data
        collectSubLevelIdsRecursive(subLevel.getPlot().save(), ids, container);

        // Scan the custom user data
        if (subLevel.getUserDataTag() != null) {
            collectSubLevelIdsRecursive(subLevel.getUserDataTag(), ids, container);
        }

        return ids;
    }


    /**
     * Recursive NBT walker. Extracts UUIDs from IntArrayTag[4] and 36-char strings
     * @param tag
     * @param ids
     * @param container
     */
    private static void collectSubLevelIdsRecursive(final CompoundTag tag, final Set<UUID> ids, final ServerSubLevelContainer container) {
        for (final String key : tag.getAllKeys()) {
            final Tag child = tag.get(key);
            if (child == null) continue;

            UUID foundId = null;

            if (child instanceof IntArrayTag iat && iat.size() == 4) {
                try { foundId = NbtUtils.loadUUID(iat); } catch (Exception ignored) {}
            } else if (child instanceof StringTag strTag) {
                try {
                    String str = strTag.getAsString();
                    if (str.length() == 36) foundId = UUID.fromString(str);
                } catch (Exception ignored) {}
            }

            if (foundId != null && container.getSubLevel(foundId) != null) {
                ids.add(foundId);
            }

            if (child instanceof CompoundTag ct) {
                collectSubLevelIdsRecursive(ct, ids, container);
            } else if (child instanceof ListTag list) {
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i) instanceof CompoundTag listCt) {
                        collectSubLevelIdsRecursive(listCt, ids, container);
                    }
                }
            }
        }
    }


    /**
     * Serializes all non-player entities near any family member (inflated 4 blocks).
     * Determines plot ownership via plot-local position or contraption anchor. Despawns originals.
     * @param sourceLevel
     * @param family
     * @param sourceContainer
     * @return
     */
    private static List<CapturedEntity> captureEntitiesInBoundsForFamily(
            final ServerLevel sourceLevel, final List<ServerSubLevel> family, final ServerSubLevelContainer sourceContainer) {
        final List<CapturedEntity> captured = new ArrayList<>();
        final Set<UUID> seenEntityUuids = new HashSet<>();

        final int blockShift = sourceContainer.getLogPlotSize() + 4;
        final int plotSizeBlocks = 1 << blockShift;

        for (final ServerSubLevel member : family) {
            final BoundingBox3dc bounds = member.boundingBox();
            //INFLATE to catch balloons.
            final AABB aabb = new AABB(bounds.minX()-4, bounds.minY()-4, bounds.minZ()-4,
                    bounds.maxX()+4, bounds.maxY()+4, bounds.maxZ()+4);

            final List<Entity> entities = sourceLevel.getEntities(
                    EntityTypeTest.forClass(Entity.class),
                    aabb,
                    e -> !(e instanceof ServerPlayer) && !e.isRemoved()
            );

            for (final Entity entity : entities) {
                if (!seenEntityUuids.add(entity.getUUID())) continue; // Dedup across overlapping AABBs

                boolean isFamilyMember = false;
                for (final ServerSubLevel m : family) {
                    if (m.getUniqueId().equals(entity.getUUID())) { isFamilyMember = true; break; }
                }
                if (isFamilyMember) continue;

                final boolean isContraption = ContraptionCompat.INSTANCE.isContraption(entity);
                final CompoundTag nbt = new CompoundTag();
                if (!entity.saveAsPassenger(nbt)) continue;

                final Vec3 plotLocal = (entity instanceof EntityStickExtension stick) ? stick.sable$getPlotPosition() : null;

                UUID correctOwnerId = member.getUniqueId(); // Default to scanner

                // Mathematical Rigorous Plot Ownership Check
                if (plotLocal != null) {
                    for (final ServerSubLevel m : family) {
                        final ServerLevelPlot p = m.getPlot();
                        final int minX = p.plotPos.x << blockShift;
                        final int maxX = minX + plotSizeBlocks - 1;
                        final int minZ = p.plotPos.z << blockShift;
                        final int maxZ = minZ + plotSizeBlocks - 1;
                        if (plotLocal.x >= minX && plotLocal.x <= maxX && plotLocal.z >= minZ && plotLocal.z <= maxZ) {
                            correctOwnerId = m.getUniqueId();
                            break;
                        }
                    }
                } else if (isContraption && nbt.contains("Contraption")) {
                    final CompoundTag contraption = nbt.getCompound("Contraption");
                    if (contraption.contains("Anchor")) {
                        int[] a = contraption.getIntArray("Anchor");
                        if (a.length == 3) {
                            for (final ServerSubLevel m : family) {
                                final ServerLevelPlot p = m.getPlot();
                                final int minX = p.plotPos.x << blockShift;
                                final int maxX = minX + plotSizeBlocks - 1;
                                final int minZ = p.plotPos.z << blockShift;
                                final int maxZ = minZ + plotSizeBlocks - 1;
                                if (a[0] >= minX && a[0] <= maxX && a[2] >= minZ && a[2] <= maxZ) {
                                    correctOwnerId = m.getUniqueId();
                                    break;
                                }
                            }
                        }
                    }
                }

                final ResourceLocation typeId = EntityType.getKey(entity.getType());
                captured.add(new CapturedEntity(
                        nbt, plotLocal, entity.getDeltaMovement(),
                        typeId.toString(),
                        entity.getClass().getSimpleName(),
                        entity.getUUID(),
                        isContraption,
                        correctOwnerId));

                // Force Minecraft to despawn the original so the new one renders correctly
                entity.remove(Entity.RemovalReason.CHANGED_DIMENSION);
            }
        }
        return captured;
    }

    /**
     * Finds all non-removed players whose position falls within any family member's bounding box (inflated 4 blocks).
     * @param sourceLevel
     * @param family
     * @return
     */
    private static List<CapturedPlayer> capturePlayersInBounds(final ServerLevel sourceLevel, final List<ServerSubLevel> family) {
        final List<CapturedPlayer> captured = new ArrayList<>();

        for (final ServerPlayer player : sourceLevel.players()) {
            if (player.isRemoved()) continue;

            // Check if the player is standing inside the bounding box of ANY ship in the family
            for (final ServerSubLevel member : family) {
                final BoundingBox3dc bounds = member.boundingBox();
                final AABB aabb = new AABB(bounds.minX()-4, bounds.minY()-4, bounds.minZ()-4,
                        bounds.maxX()+4, bounds.maxY()+4, bounds.maxZ()+4);

                if (aabb.contains(player.getX(), player.getY(), player.getZ())) {
                    captured.add(new CapturedPlayer(player, player.position(), player.getYRot(), player.getXRot()));
                    break;
                }
            }
        }
        return captured;
    }


    /**
     *
     * @param captured
     * @param targetLevel
     * @param sourcePose
     * @param targetPose
     */
    private static void teleportCapturedPlayers(final List<CapturedPlayer> captured, final ServerLevel targetLevel,
                                                final Pose3d sourcePose, final Pose3d targetPose) {
        for (final CapturedPlayer cp : captured) {
            if (cp.player().isRemoved()) continue;

            Vector3d relPos = new Vector3d(cp.sourceWorldPos().x, cp.sourceWorldPos().y, cp.sourceWorldPos().z)
                    .sub(sourcePose.position());

            Quaterniond delta = new Quaterniond();
            if (targetPose.orientation() != null && sourcePose.orientation() != null) {
                delta.set(targetPose.orientation()).mul(new Quaterniond(sourcePose.orientation()).invert());
            }

            delta.transform(relPos);
            double newX = targetPose.position().x() + relPos.x;
            double newY = targetPose.position().y() + relPos.y;
            double newZ = targetPose.position().z() + relPos.z;

            Vec3 oldVel = cp.player().getDeltaMovement();
            Vector3d velJoml = new Vector3d(oldVel.x, oldVel.y, oldVel.z);
            delta.transform(velJoml);

            cp.player().teleportTo(targetLevel, newX, newY, newZ, cp.yRot(), cp.xRot());

            // Force momentum client-sync
            cp.player().setDeltaMovement(velJoml.x, velJoml.y, velJoml.z);
            cp.player().hurtMarked = true;
        }
    }


    /**
     *
     * @param targetLevel
     * @param captured
     * @param translationsByMember
     * @param sourcePosesByMember
     * @param targetPosesByMember
     */
    private static void respawnCapturedEntities(
            final ServerLevel targetLevel,
            final List<CapturedEntity> captured,
            final Map<UUID, PlotTranslation> translationsByMember,
            final Map<UUID, Pose3d> sourcePosesByMember,
            final Map<UUID, Pose3d> targetPosesByMember
    ) {
        final Collection<PlotTranslation> allTranslations = translationsByMember.values();

        for (final CapturedEntity ce : captured) {

            final Pose3d sourcePose = sourcePosesByMember.get(ce.familyMemberId());
            final Pose3d targetPose = targetPosesByMember.get(ce.familyMemberId());

            if (sourcePose == null || targetPose == null) continue;

            final ListTag posList = ce.nbt().getList("Pos", Tag.TAG_DOUBLE);
            if (posList.size() == 3) {
                double x = posList.getDouble(0);
                double y = posList.getDouble(1);
                double z = posList.getDouble(2);

                boolean inPlot = false;
                for (final PlotTranslation t : allTranslations) {
                    if (x >= t.minX() && x <= t.maxX() && z >= t.minZ() && z <= t.maxZ()) {
                        x += t.offsetX();
                        z += t.offsetZ();
                        inPlot = true;
                        break;
                    }
                }

                if (!inPlot) {
                    final Vector3d relPos = new Vector3d(x, y, z).sub(sourcePose.position());

                    if (targetPose.orientation() != null && sourcePose.orientation() != null && !targetPose.orientation().equals(sourcePose.orientation())) {
                        final Quaterniond delta = new Quaterniond(targetPose.orientation())
                                .mul(new Quaterniond(sourcePose.orientation()).invert());
                        delta.transform(relPos);
                    }

                    x = targetPose.position().x() + relPos.x;
                    y = targetPose.position().y() + relPos.y;
                    z = targetPose.position().z() + relPos.z;
                }

                final ListTag newPos = new ListTag();
                newPos.add(DoubleTag.valueOf(x));
                newPos.add(DoubleTag.valueOf(y));
                newPos.add(DoubleTag.valueOf(z));
                ce.nbt().put("Pos", newPos);
            }

            if (ce.nbt().contains("TileX") && ce.nbt().contains("TileZ")) {
                int tx = ce.nbt().getInt("TileX");
                int tz = ce.nbt().getInt("TileZ");
                for (final PlotTranslation t : allTranslations) {
                    if (tx >= t.minX() && tx <= t.maxX() && tz >= t.minZ() && tz <= t.maxZ()) {
                        ce.nbt().putInt("TileX", tx + (int)t.offsetX());
                        ce.nbt().putInt("TileZ", tz + (int)t.offsetZ());
                        break;
                    }
                }
            }

            if (ce.isContraption() && ce.nbt().contains("Contraption")) {
                rewriteContraptionTagAnchorsUniversal(ce.nbt().getCompound("Contraption"), allTranslations);
            }

            rewriteInternalBlockPosRefsInTag(ce.nbt(), allTranslations);

            final Entity entity = EntityType.loadEntityRecursive(ce.nbt(), targetLevel, e -> e);
            if (entity != null) {

                Vec3 vel = ce.worldVelocity();

                if (vel != null) {

                    Vector3d velVec = new Vector3d(vel.x, vel.y, vel.z);

                    if (targetPose.orientation() != null && sourcePose.orientation() != null && !targetPose.orientation().equals(sourcePose.orientation())) {

                        final Quaterniond delta = new Quaterniond(targetPose.orientation())
                                .mul(new Quaterniond(sourcePose.orientation()).invert());
                        delta.transform(velVec);

                    }
                    entity.setDeltaMovement(velVec.x, velVec.y, velVec.z);
                }

                targetLevel.addFreshEntity(entity);

                if (ce.plotLocalPos() != null && entity instanceof EntityStickExtension stick) {
                    Vec3 shiftedPlotLocal = ce.plotLocalPos();
                    for (final PlotTranslation t : allTranslations) {
                        if (shiftedPlotLocal.x >= t.minX() && shiftedPlotLocal.x <= t.maxX() && shiftedPlotLocal.z >= t.minZ() && shiftedPlotLocal.z <= t.maxZ()) {
                            shiftedPlotLocal = shiftedPlotLocal.add(t.offsetX(), 0, t.offsetZ());
                            break;
                        }
                    }
                    stick.sable$setPlotPosition(shiftedPlotLocal);
                }
            }
        }
    }


    /**
     *
     * @param sourcePhysics
     * @param family
     * @return
     */
    private static List<CapturedRope> captureRopesForFamily(
            final SubLevelPhysicsSystem sourcePhysics,
            final List<ServerSubLevel> family) {

        final Set<UUID> familyIds = new HashSet<>();

        for (final ServerSubLevel m : family) familyIds.add(m.getUniqueId());

        final List<dev.ryanhcode.sable.api.physics.object.ArbitraryPhysicsObject> snapshot =
                new ArrayList<>((Collection) sourcePhysics.getArbitraryObjects());

        final List<CapturedRope> captured = new ArrayList<>();

        for (final dev.ryanhcode.sable.api.physics.object.ArbitraryPhysicsObject obj : snapshot) {
            if (!(obj instanceof dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject rope)) continue;
            if (!rope.isActive()) continue;


            final ServerSubLevel startSL = rope.getStartAttachmentSubLevel();
            final UUID startId = (startSL != null) ? startSL.getUniqueId() : null;

            UUID endId = null;
            ServerSubLevel endSL = null;
            final List<Vector3d> pts = rope.getPoints();

            if (!pts.isEmpty()) {
                final Vector3d last = pts.get(pts.size() - 1);
                for (final ServerSubLevel m : family) {
                    if (m.boundingBox().contains(last.x, last.y, last.z)) {
                        endSL = m;
                        endId = m.getUniqueId();
                        break;
                    }
                }
            }

            final boolean startInFamily = startId != null && familyIds.contains(startId);
            final boolean endInFamily   = endId   != null && familyIds.contains(endId);

            if (!startInFamily && !endInFamily) continue;

            if (!startInFamily || !endInFamily) {
                Sable.LOGGER.info("Rope partially attached to family — destroying without respawn");
                sourcePhysics.removeObject(rope);
                continue;
            }

            // Both ends are family-owned record local offsets and rebuild later.
            final Vector3d startLocal = worldToBodyLocal(pts.get(0), startSL);
            final Vector3d endLocal   = worldToBodyLocal(pts.get(pts.size() - 1), endSL);

            captured.add(new CapturedRope(
                    pts.size(),
                    rope.getCollisionRadius(),
                    startId, startLocal,
                    endId,   endLocal
            ));

            sourcePhysics.removeObject(rope);
        }

        Sable.LOGGER.info("Captured {} rope(s) for rebuild", captured.size());
        return captured;
    }

    /**
     *
     * @param targetPhysics
     * @param captured
     * @param oldUuidToNewMember
     */
    private static void respawnCapturedRopes(
            final SubLevelPhysicsSystem targetPhysics,
            final List<CapturedRope> captured,
            final Map<UUID, ServerSubLevel> oldUuidToNewMember) {

        for (final CapturedRope cr : captured) {
            final ServerSubLevel newStart = oldUuidToNewMember.get(cr.startSubLevelId());
            final ServerSubLevel newEnd   = oldUuidToNewMember.get(cr.endSubLevelId());

            if (newStart == null || newEnd == null) {
                Sable.LOGGER.warn("Rope respawn: sub-level missing (start={}, end={}) — skipping",
                        cr.startSubLevelId(), cr.endSubLevelId());
                continue;
            }

            final Vector3d worldStart = bodyLocalToWorld(cr.startLocalOffset(), newStart);
            final Vector3d worldEnd   = bodyLocalToWorld(cr.endLocalOffset(),   newEnd);

            final int n = cr.pointCount();
            final List<Vector3d> newPts = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                final double t = (n == 1) ? 0.0 : (double) i / (n - 1);
                newPts.add(new Vector3d(worldStart).lerp(worldEnd, t));
            }

            final dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject newRope =
                    new dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject(newPts, cr.collisionRadius());

            // Set attachments registration so the spawn packet.
            newRope.setAttachment(
                    dev.ryanhcode.sable.api.physics.object.rope.RopeHandle.AttachmentPoint.START,
                    worldStart, newStart);
            newRope.setAttachment(
                    dev.ryanhcode.sable.api.physics.object.rope.RopeHandle.AttachmentPoint.END,
                    worldEnd, newEnd);

            targetPhysics.addObject(newRope);
        }
        Sable.LOGGER.info("Respawned {} rope(s)", captured.size());
    }

    /**
     * Rope function to get coords of points to local space
     * @param worldPoint
     * @param body
     * @return
     */
    private static Vector3d worldToBodyLocal(final Vector3dc worldPoint, final ServerSubLevel body) {
        final Pose3d pose = body.logicalPose();
        final Vector3d local = new Vector3d(worldPoint).sub(pose.position());
        new Quaterniond(pose.orientation()).invert().transform(local);
        return local;
    }

    /**
     *
     * @param localPoint
     * @param body
     * @return
     */
    private static Vector3d bodyLocalToWorld(final Vector3dc localPoint, final ServerSubLevel body) {
        final Pose3d pose = body.logicalPose();
        final Vector3d world = new Vector3d(localPoint);
        new Quaterniond(pose.orientation()).transform(world);
        world.add(pose.position());
        return world;
    }

    /**
     *
     * @param contraption
     * @param translations
     */
    private static void rewriteContraptionTagAnchorsUniversal(final CompoundTag contraption, final Collection<PlotTranslation> translations) {
        if (contraption.contains("Anchor")) {
            int[] a = contraption.getIntArray("Anchor");
            if (a.length == 3) {
                for (final PlotTranslation t : translations) {
                    if (a[0] >= t.minX() && a[0] <= t.maxX() && a[2] >= t.minZ() && a[2] <= t.maxZ()) {
                        contraption.putIntArray("Anchor", new int[]{a[0] + (int)t.offsetX(), a[1], a[2] + (int)t.offsetZ()});
                        break;
                    }
                }
            }
        }
        if (contraption.contains("SubContraptions", 9)) {
            final ListTag subs = contraption.getList("SubContraptions", 10);
            for (int i = 0; i < subs.size(); i++) {
                final CompoundTag sub = subs.getCompound(i);
                if (sub.contains("Contraption")) {
                    rewriteContraptionTagAnchorsUniversal(sub.getCompound("Contraption"), translations);
                } else {
                    rewriteContraptionTagAnchorsUniversal(sub, translations);
                }
            }
        }
    }

    /**
     *
     * @param plotTag
     * @param translations
     */
    private static void rewriteInternalBlockPosRefs(final CompoundTag plotTag, final Collection<PlotTranslation> translations) {
        final CompoundTag chunks = plotTag.getCompound("chunks");
        for (final String key : chunks.getAllKeys()) {
            final ListTag bes = chunks.getCompound(key).getList("block_entities", 10);
            for (int i = 0; i < bes.size(); i++) {
                rewriteInternalBlockPosRefsInTag(bes.getCompound(i), translations);
            }
        }
    }

    /**
     *
     * @param tag
     * @param translations
     * @return
     */
    private static void rewriteInternalBlockPosRefsInTag(final CompoundTag tag, final Collection<PlotTranslation> translations) {
        for (final String key : new ArrayList<>(tag.getAllKeys())) {
            Tag child = tag.get(key);

            if (child instanceof IntArrayTag iat && iat.size() == 3) {
                int[] arr = iat.getAsIntArray();
                for (final PlotTranslation t : translations) {
                    if (arr[0] >= t.minX() && arr[0] <= t.maxX() && arr[2] >= t.minZ() && arr[2] <= t.maxZ()) {
                        tag.putIntArray(key, new int[]{arr[0] + (int)t.offsetX(), arr[1], arr[2] + (int)t.offsetZ()});
                        break;
                    }
                }
            } else if (child instanceof CompoundTag ct) {
                if (ct.contains("x") && ct.contains("z") && ct.contains("y")) {
                    int cx = ct.getInt("x");
                    int cz = ct.getInt("z");
                    for (final PlotTranslation t : translations) {
                        if (cx >= t.minX() && cx <= t.maxX() && cz >= t.minZ() && cz <= t.maxZ()) {
                            ct.putInt("x", cx + (int)t.offsetX());
                            ct.putInt("z", cz + (int)t.offsetZ());
                            break;
                        }
                    }
                }
                if (ct.contains("X") && ct.contains("Z") && ct.contains("Y")) {
                    int cx = ct.getInt("X");
                    int cz = ct.getInt("Z");
                    for (final PlotTranslation t : translations) {
                        if (cx >= t.minX() && cx <= t.maxX() && cz >= t.minZ() && cz <= t.maxZ()) {
                            ct.putInt("X", cx + (int)t.offsetX());
                            ct.putInt("Z", cz + (int)t.offsetZ());
                            break;
                        }
                    }
                }
            } else if (child instanceof ListTag list) {
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i) instanceof CompoundTag listCt) {
                        rewriteInternalBlockPosRefsInTag(listCt, translations);
                    }
                }
            }//any block position refs nested more than one level deep needs this.
        }
    }

    /**
     *
     * @param plotTag
     * @param offsetX
     * @param offsetZ
     */
    private static void rewriteBlockEntityPositions(final CompoundTag plotTag, final long offsetX, final long offsetZ) {
        final CompoundTag chunks = plotTag.getCompound("chunks");
        for (final String key : chunks.getAllKeys()) {
            final ListTag bes = chunks.getCompound(key).getList("block_entities", 10);
            for (int i = 0; i < bes.size(); i++) {
                CompoundTag be = bes.getCompound(i);
                if (be.contains("x")) be.putInt("x", (int)(be.getInt("x") + offsetX));
                if (be.contains("z")) be.putInt("z", (int)(be.getInt("z") + offsetZ));
            }
        }
    }

    /**
     *
     * @param plotTag
     * @param shift
     */
    private static void rewriteSectionIndices(final CompoundTag plotTag, final int shift) {
        if (shift == 0) return;
        final CompoundTag chunks = plotTag.getCompound("chunks");
        for (final String key : chunks.getAllKeys()) {
            final CompoundTag chunk = chunks.getCompound(key);
            final CompoundTag oldSec = chunk.getCompound("sections");
            final CompoundTag newSec = new CompoundTag();
            int kept = 0, dropped = 0;
            final List<Integer> keptIndices = new ArrayList<>();
            final List<Integer> droppedIndices = new ArrayList<>();
            for (final String sKey : oldSec.getAllKeys()) {
                int oldIdx = Integer.parseInt(sKey);
                int newIdx = oldIdx + shift;
                if (newIdx >= 0) {
                    newSec.put(String.valueOf(newIdx), oldSec.getCompound(sKey));
                    kept++;
                    keptIndices.add(oldIdx);
                } else {
                    dropped++;
                    droppedIndices.add(oldIdx);
                }
            }
            Sable.LOGGER.info("Section shift chunk={} shift={} kept={} dropped={} keptOldIdx={} droppedOldIdx={}",
                    key, shift, kept, dropped, keptIndices, droppedIndices);
            chunk.put("sections", newSec);
        }
    }

    /**
     * Fast path when source and target are the same dimension. Resets velocity, repositions via physics pipeline, wakes the body.
     * @param source
     * @param level
     * @param targetPosition
     * @param targetOrientation
     * @return
     */
    private static @Nullable ServerSubLevel sameDimTeleport(final ServerSubLevel source, final ServerLevel level,
                                                            final Vector3dc targetPosition, final @Nullable Quaterniondc targetOrientation) {
        final ServerSubLevelContainer c = SubLevelContainer.getContainer(level);
        if (c == null) return null;
        final Quaterniondc orient = targetOrientation != null ? targetOrientation : source.logicalPose().orientation();
        c.physicsSystem().getPipeline().resetVelocity(source);
        c.physicsSystem().getPipeline().teleport(source, targetPosition, orient);
        c.physicsSystem().getPipeline().wakeUp(source);
        return source;
    }
}