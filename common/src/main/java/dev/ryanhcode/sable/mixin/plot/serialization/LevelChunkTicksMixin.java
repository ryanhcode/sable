package dev.ryanhcode.sable.mixin.plot.serialization;

import dev.ryanhcode.sable.mixinterface.plot.serialization.LevelChunkTicksExtension;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.SavedTick;
import net.minecraft.world.ticks.ScheduledTick;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.Set;

/**
 * Provides {@link LevelChunkTicks} with the ability to copy scheduled ticks, used for sub-level chunk serialization.
 */
@Mixin(LevelChunkTicks.class)
public class LevelChunkTicksMixin<T> implements LevelChunkTicksExtension<T> {

    @Shadow
    private @Nullable List<SavedTick<T>> pendingTicks;

    @Shadow
    @Final
    private Set<ScheduledTick<?>> ticksPerPosition;

    @SuppressWarnings("unchecked")
    @Override
    public void sable$copy(final LevelChunkTicks<T> ticks) {
        this.pendingTicks = ((LevelChunkTicksMixin<T>) (Object) ticks).pendingTicks;
        for (final SavedTick<T> savedTick : this.pendingTicks) {
            this.ticksPerPosition.add(ScheduledTick.probe(savedTick.type(), savedTick.pos()));
        }
    }
}
