package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.trains;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.contraptions.render.ContraptionVisual;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.Translate;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.neoforge.compatibility.flywheel.FlywheelCompatNeoForge;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CarriageContraptionVisual.class)
public abstract class CarriageContraptionVisualMixin extends ContraptionVisual<CarriageContraptionEntity> {

    public CarriageContraptionVisualMixin(final VisualizationContext ctx, final CarriageContraptionEntity entity, final float partialTick) {
        super(ctx, entity, partialTick);
    }

    @WrapOperation(method = "animate", at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/lib/transform/PoseTransformStack;translate(Lorg/joml/Vector3fc;)Ldev/engine_room/flywheel/lib/transform/Translate;"))
    private Translate sable$translate(final PoseTransformStack instance, final Vector3fc vector3fc, Operation<Translate> original, @Local final Vector3f visualPosition, @Local(argsOnly = true) final float partialTick) {
        final Vec3 pos = this.entity.position();
        final SubLevelContainer container = SubLevelContainer.getContainer(this.entity.level());

        if (container == null) {
            original.call(instance, vector3fc);
            return instance;
        }

        final ChunkPos chunkPos = this.entity.chunkPosition();
        final boolean inBounds = container.inBounds(chunkPos);

        if (!inBounds) {
            original.call(instance, vector3fc);
            return instance;
        }

        final int plotX = (chunkPos.x >> container.getLogPlotSize()) - container.getOrigin().x;
        final int plotZ = (chunkPos.z >> container.getLogPlotSize()) - container.getOrigin().y;

        final FlywheelCompatNeoForge.SubLevelFlwRenderState state = FlywheelCompatNeoForge.getInfo(ChunkPos.asLong(plotX, plotZ));

        if (state == null) {
            original.call(instance, vector3fc);
            return instance;
        }

        final double entityX = Mth.lerp(partialTick, this.entity.xOld, pos.x);
        final double entityY = Mth.lerp(partialTick, this.entity.yOld, pos.y);
        final double entityZ = Mth.lerp(partialTick, this.entity.zOld, pos.z);

        final Vec3i origin = this.renderOrigin();
        final Vec3 renderPos = state.renderPose.transformPosition(new Vec3(entityX, entityY, entityZ)).subtract(origin.getX(), origin.getY(), origin.getZ());

        instance.translate(renderPos.x, renderPos.y, renderPos.z);
        instance.rotate(new Quaternionf(state.renderPose.orientation()));
        return instance;
    }
}
