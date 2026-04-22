package dev.ryanhcode.sable.mixin.sculk_vibrations;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventDispatcher;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameEventDispatcher.class)
public class GameEventDispatcherMixin {

    @Final
    @Shadow
    private ServerLevel level;

    @Inject(method = "post", at = @At(value = "NEW", target = "java/util/ArrayList"))
    private void sable$useBBIntersection(final Holder<GameEvent> gameEvent, final Vec3 pos, final GameEvent.Context context, final CallbackInfo ci, @Share("bb") final LocalRef<BoundingBox3ic> bbRef,
                                   @Local(ordinal = 1) final LocalIntRef x1, @Local(ordinal = 2) final LocalIntRef y1, @Local(ordinal = 3) final LocalIntRef z1,
                                   @Local(ordinal = 4) final LocalIntRef x2, @Local(ordinal = 5) final LocalIntRef y2, @Local(ordinal = 6) final LocalIntRef z2) {
        final BoundingBox3ic bb = bbRef.get();
        if (bb != null) {
            x1.set(SectionPos.blockToSectionCoord(bb.minX()));
            y1.set(SectionPos.blockToSectionCoord(bb.minY()));
            z1.set(SectionPos.blockToSectionCoord(bb.minZ()));
            x2.set(SectionPos.blockToSectionCoord(bb.maxX()));
            y2.set(SectionPos.blockToSectionCoord(bb.maxY()));
            z2.set(SectionPos.blockToSectionCoord(bb.maxZ()));
        }
    }

    @WrapMethod(method = "post")
    private void sable$visitShipListeners(final Holder<GameEvent> gameEvent, final Vec3 pos, final GameEvent.Context context, final Operation<Void> original, @Share("bb") final LocalRef<BoundingBox3ic> bbRef) {
        final Vec3 globalPos = Sable.HELPER.projectOutOfSubLevel(this.level, pos);
        original.call(gameEvent, globalPos, context);
        if (bbRef.get() != null) {
            return;
        }

        // For the first non-nested call, propagate the call to sub-levels
        final int radius = gameEvent.value().notificationRadius();
        final BoundingBox3dc sourceBB = new BoundingBox3d(BlockPos.containing(globalPos)).expand(radius);
        final BoundingBox3i intersection = new BoundingBox3i();

        Sable.HELPER.getAllIntersecting(this.level, sourceBB).forEach(subLevel -> {
            final BoundingBox3d plotBB = new BoundingBox3d(subLevel.getPlot().getBoundingBox());
            final BoundingBox3dc sourceInPlotBB = sourceBB.transformInverse(subLevel.logicalPose(), new BoundingBox3d());
            bbRef.set(intersection.set(plotBB.intersect(sourceInPlotBB)));
            original.call(gameEvent, globalPos, context);
        });
    }
}
