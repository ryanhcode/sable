package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.schematics;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.AllSpecialTextures;
import com.simibubi.create.content.schematics.client.SchematicAndQuillHandler;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SchematicAndQuillHandler.class)
public abstract class SchematicAndQuillHandlerMixin {

    @Shadow
    public BlockPos firstPos;

    @Shadow
    protected abstract AABB getCurrentSelectionBox();

    @Shadow
    protected abstract Outliner outliner();

    @Shadow
    private Object outlineSlot;

    @Shadow
    public BlockPos secondPos;

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;containing(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;"))
    private BlockPos sable$containing(Position position, Operation<BlockPos> original) {
        if (this.firstPos != null) {
            final SubLevel subLevel = Sable.HELPER.getContainingClient(this.firstPos);

            if (subLevel != null) {
                position = subLevel.logicalPose().transformPositionInverse(new Vec3(position.x(), position.y(), position.z()));
            }
        }

        return original.call(position);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/BlockHitResult;getBlockPos()Lnet/minecraft/core/BlockPos;"))
    public BlockPos sable$preventMovingSelectedOutOfPlot(final BlockHitResult instance, final Operation<BlockPos> original) {
        if (this.firstPos != null) { //check sublevel and keep us in the plot
            final SubLevel selectedSublevel = Sable.HELPER.getContainingClient(this.firstPos);

            Vec3 loc = instance.getBlockPos().getCenter();
            final SubLevel hitSublevel = Sable.HELPER.getContainingClient(loc);
            if (hitSublevel != selectedSublevel) {
                if (hitSublevel != null) {
                    loc = hitSublevel.logicalPose().transformPosition(loc);
                }

                if (selectedSublevel != null) {
                    loc = selectedSublevel.logicalPose().transformPositionInverse(loc);
                }
            }

            return BlockPos.containing(loc);
        }


        return original.call(instance);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void sable$renderSubLevelBoxes(final CallbackInfo ci) {

        final ClientLevel level = Minecraft.getInstance().level;
        final ClientSubLevelContainer container = SubLevelContainer.getContainer(level);

        final ActiveSableCompanion helper = Sable.HELPER;
        if (this.firstPos != null && container.inBounds(this.firstPos) &&
                helper.getContaining(level, this.firstPos) == null) {
            this.firstPos = null;
        }

        if (this.secondPos != null && container.inBounds(this.secondPos) &&
                helper.getContaining(level, this.secondPos) == null) {
            this.secondPos = null;
        }

        final AABB currentSelectionBox = this.getCurrentSelectionBox();

        if (currentSelectionBox != null) {
            final BoundingBox3d bounds = new BoundingBox3d(currentSelectionBox);

            final SubLevel containingSubLevel = helper.getContainingClient(bounds.center(new Vector3d()));
            if (containingSubLevel != null) {
                bounds.transform(containingSubLevel.logicalPose(), bounds);
            }

            final Iterable<SubLevel> intersecting = helper.getAllIntersecting(level, bounds);

            for (final SubLevel subLevel : intersecting) {
                if (subLevel == containingSubLevel) continue;

                final BoundingBox3ic plotBounds = subLevel.getPlot().getBoundingBox();

                this.outliner().chaseAABB(this.outlineSlot.hashCode() + " sub_level " + subLevel.getUniqueId(), plotBounds.toAABB())
                        .colored(0x86a4e3)
                        .withFaceTextures(AllSpecialTextures.CHECKERED, AllSpecialTextures.HIGHLIGHT_CHECKERED)
                        .lineWidth(1 / 16f);
            }
        }
    }
}
