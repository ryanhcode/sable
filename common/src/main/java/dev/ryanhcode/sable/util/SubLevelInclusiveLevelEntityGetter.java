package dev.ryanhcode.sable.util;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * A {@link LevelEntityGetter} that delegates all calls to a child, taking into account sub-levels and their plots
 *
 * @param <T>
 */
public class SubLevelInclusiveLevelEntityGetter<T extends EntityAccess> implements LevelEntityGetter<T> {
    public static final int MAX_GET_SIDE_LENGTH = 100_000;

    private final Level level;
    private final LevelEntityGetter<T> delegate;

    public SubLevelInclusiveLevelEntityGetter(final Level level, final LevelEntityGetter<T> delegate) {
        this.level = level;
        this.delegate = delegate;
    }

    private static void logError(final AABB aabb) {
        Sable.LOGGER.error("Aborting entity get for abnormally large AABB: {}", aabb, new Throwable("Stack Trace"));
    }

    @Override
    public @Nullable T get(final int i) {
        return this.delegate.get(i);
    }

    @Override
    public @Nullable T get(final UUID uUID) {
        return this.delegate.get(uUID);
    }

    @Override
    public @NotNull Iterable<T> getAll() {
        return this.delegate.getAll();
    }

    @Override
    public <U extends T> void get(final EntityTypeTest<T, U> entityTypeTest, final AbortableIterationConsumer<U> abortableIterationConsumer) {
        this.delegate.get(entityTypeTest, abortableIterationConsumer);
    }

    @Override
    public void get(AABB aABB, final Consumer<T> consumer) {
        if (aABB.getSize() > MAX_GET_SIDE_LENGTH) {
            logError(aABB);
            return;
        }

        final SubLevel subLevel = Sable.HELPER.getContaining(this.level, aABB.getCenter());

        this.delegate.get(aABB, consumer);

        final BoundingBox3d bb = new BoundingBox3d(aABB);
        final Matrix4d bakedMatrix = new Matrix4d();
        if (subLevel != null) {
            aABB = bb.transform(subLevel.logicalPose(), bb).toMojang();

            this.delegate.get(aABB, consumer);
        }

        final Iterable<SubLevel> intersecting = Sable.HELPER.getAllIntersecting(this.level, new BoundingBox3d(bb));

        for (final SubLevel otherSubLevel : intersecting) {
            if (otherSubLevel == subLevel) {
                continue;
            }

            final AABB localBounds = bb.set(aABB).transformInverse(otherSubLevel.logicalPose(), bakedMatrix, bb).toMojang();

            this.delegate.get(localBounds, consumer);
        }
    }

    @Override
    public <U extends T> void get(final EntityTypeTest<T, U> entityTypeTest, AABB aABB, final AbortableIterationConsumer<U> abortableIterationConsumer) {
        if (aABB.getSize() > MAX_GET_SIDE_LENGTH) {
            logError(aABB);
            return;
        }

        final SubLevel subLevel = Sable.HELPER.getContaining(this.level, aABB.getCenter());
        this.delegate.get(entityTypeTest, aABB, abortableIterationConsumer);

        final BoundingBox3d bb = new BoundingBox3d(aABB);
        if (subLevel != null) {
            aABB = bb.transform(subLevel.logicalPose(), bb).toMojang();

            this.delegate.get(entityTypeTest, aABB, abortableIterationConsumer);
        }

        final Iterable<SubLevel> intersecting = Sable.HELPER.getAllIntersecting(this.level, new BoundingBox3d(bb));

        for (final SubLevel otherSubLevel : intersecting) {
            if (otherSubLevel == subLevel) {
                continue;
            }

            final AABB localBounds = bb.set(aABB).transformInverse(otherSubLevel.logicalPose(), bb).toMojang();

            this.delegate.get(entityTypeTest, localBounds, abortableIterationConsumer);
        }
    }

    public void getIgnoringSubLevels(final AABB aABB, final Consumer<T> consumer) {
        this.delegate.get(aABB, consumer);
    }

    public <U extends T> void getIgnoringSubLevels(final EntityTypeTest<T, U> entityTypeTest, final AABB aABB, final AbortableIterationConsumer<U> abortableIterationConsumer) {
        this.delegate.get(entityTypeTest, aABB, abortableIterationConsumer);
    }
}
