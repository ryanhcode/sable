package dev.ryanhcode.sable.sublevel.storage;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.util.SableNBTUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.BitSet;

/**
 * Stores the map for which plots are occupied
 */
public class SubLevelOccupancySavedData extends SavedData {
    public static final String FILE_ID = "sable_sub_level_occupancy";
    private final ServerLevel level;

    private SubLevelOccupancySavedData(final ServerLevel level) {
        this.level = level;
    }

    public static SubLevelOccupancySavedData getOrLoad(final ServerLevel level) {
        return level.getChunkSource().getDataStorage().computeIfAbsent(
                new Factory<>(
                        () -> new SubLevelOccupancySavedData(level),
                        (tag, provider) -> SubLevelOccupancySavedData.load(level, tag),
                        DataFixTypes.LEVEL
                ),
                SubLevelOccupancySavedData.FILE_ID);
    }


    private static SubLevelOccupancySavedData load(final ServerLevel level, final CompoundTag tag) {
        final SubLevelOccupancySavedData data = new SubLevelOccupancySavedData(level);

        final long[] longArray = tag.getLongArray("sub_level_occupancy");

        if (longArray.length > 0) {
            final BitSet occupancyData = BitSet.valueOf(longArray);
            final SubLevelContainer container = SubLevelContainer.getContainer(level);
            assert container != null : "Sub-level container is null";

            // clone into the container
            final BitSet occupancy = container.getOccupancy();
            occupancy.clear();
            occupancy.or(occupancyData);

            // restore the last-known pose of each reserved (possibly unloaded) plot
            final ListTag poses = tag.getList("last_known_poses", Tag.TAG_COMPOUND);
            for (int i = 0; i < poses.size(); i++) {
                final CompoundTag entry = poses.getCompound(i);
                final Pose3d pose = SableNBTUtils.readPose3d(entry.getCompound("pose"));
                container.setLastKnownPose(entry.getInt("index"), pose);
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(final CompoundTag compoundTag, final HolderLookup.Provider provider) {
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);
        assert container != null : "Sub-level container is null";

        final BitSet occupancy = container.getOccupancy();

        final long[] longArray = occupancy.toLongArray();

        compoundTag.putLongArray("sub_level_occupancy", longArray);

        // persist each reserved plot's last-known pose so distance/cost queries stay accurate for
        // sub-levels that are still unloaded after a restart
        final ListTag poses = new ListTag();
        for (int index = occupancy.nextSetBit(0); index >= 0; index = occupancy.nextSetBit(index + 1)) {
            final Pose3d pose = container.getPersistablePose(index);
            if (pose == null) {
                continue;
            }
            final CompoundTag entry = new CompoundTag();
            entry.putInt("index", index);
            entry.put("pose", SableNBTUtils.writePose3d(pose));
            poses.add(entry);
        }
        compoundTag.put("last_known_poses", poses);

        return compoundTag;
    }
}