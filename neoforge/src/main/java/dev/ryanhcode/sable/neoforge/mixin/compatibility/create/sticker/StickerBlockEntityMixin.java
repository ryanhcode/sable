package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.sticker;

import com.simibubi.create.content.contraptions.chassis.StickerBlock;
import com.simibubi.create.content.contraptions.chassis.StickerBlockEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueItem;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.create.StickerBlockEntityExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

/**
 * Mixin into {@link com.simibubi.create.content.contraptions.chassis.StickerBlockEntity} so stickers can create fixed constraints between sub-levels.
 */

@Mixin(StickerBlockEntity.class)
public abstract class StickerBlockEntityMixin extends SmartBlockEntity implements StickerBlockEntityExtension {

    @Unique
    private static final double DISTANCE_TOLERANCE = 0.0625;

    @Unique
    private static final double ANGLE_TOLERANCE = 30.0;

    @Unique
    private FixedConstraintHandle sable$handle;
    @Unique
    private BlockPos sable$attachedPos;
    @Unique
    private Vector3d sable$constraintPos1;
    @Unique
    private Vector3d sable$constraintPos2;
    @Unique
    private Quaterniond sable$constraintOrientation;
    @Unique
    private boolean sable$hadConstraint;
    @Unique
    private boolean sable$hasConstraint;

    /**
     * kinda hacky but we ball
     */
    @Unique
    private boolean sable$doNotSaveConstraint;

    private StickerBlockEntityMixin(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @Shadow
    public abstract boolean isBlockStateExtended();

    @Shadow
    public abstract void playSound(boolean attach);

    @Override
    public void sable$removeConstraint() {
        if (this.sable$handle != null) {
            this.sable$handle.remove();
            this.sable$handle = null;
        }

        this.sable$attachedPos = null;
        this.sable$constraintPos1 = null;
        this.sable$constraintPos2 = null;
        this.sable$constraintOrientation = null;

        this.sable$hasConstraint = false;
        this.sendData();
    }

    @Override
    public void sable$tickConstraint() {
        if (this.isBlockStateExtended()) {
            final Direction direction = this.getBlockState().getValue(StickerBlock.FACING);

            if (this.sable$attachedPos != null && !SuperGlueEntity.isValidFace(this.level, this.sable$attachedPos, direction.getOpposite())) {
                this.sable$removeConstraint();
            }

            if ((this.sable$handle == null || !this.sable$handle.isValid())) {
                if (this.sable$attachedPos != null) {
                    final ActiveSableCompanion helper = Sable.HELPER;
                    final ServerSubLevel thisSubLevel = (ServerSubLevel) helper.getContaining(this.level, this.getBlockPos());
                    final ServerSubLevel otherSubLevel = (ServerSubLevel) helper.getContaining(this.level, this.sable$attachedPos);

                    if (thisSubLevel != otherSubLevel) {
                        this.sable$applyConstraint(thisSubLevel, otherSubLevel);
                    }

                    return;
                }

                this.sable$removeConstraint();

                final double gridHalfSize = 0.5 * 0.75;

                final Vector3dc rayStartPosition = JOMLConversion.atCenterOf(this.getBlockPos())
                        .add(direction.getStepX() * 0.5,direction.getStepY() * 0.5,direction.getStepZ() * 0.5);

                this.sable$tryAttach(rayStartPosition, direction);

                final Vector3d gridRayStartPosition = new Vector3d();

                // grid for more tolerance
                for (int xOffset = -1; xOffset <= 1; xOffset += 2) {
                    for (int zOffset = -1; zOffset <= 1; zOffset += 2) {
                        gridRayStartPosition.set(rayStartPosition);

                        final Direction secondaryDirection = direction.getAxis().isVertical() ? Direction.NORTH : direction.getClockWise();
                        final Direction tertiaryDirection = direction.getAxis().isVertical() ? Direction.EAST : Direction.UP;

                        gridRayStartPosition.add(secondaryDirection.getStepX() * xOffset * gridHalfSize, secondaryDirection.getStepY() * xOffset * gridHalfSize, secondaryDirection.getStepZ() * xOffset * gridHalfSize);
                        gridRayStartPosition.add(tertiaryDirection.getStepX() * zOffset * gridHalfSize, tertiaryDirection.getStepY() * zOffset * gridHalfSize, tertiaryDirection.getStepZ() * zOffset * gridHalfSize);

                        this.sable$tryAttach(gridRayStartPosition, direction);
                    }
                }
            }
        } else {
            this.sable$removeConstraint();
        }
    }

    /**
     * Attempts to attach to a block
     */
    @Unique
    private void sable$tryAttach(final Vector3dc rayStartPosition, final Direction direction) {
        if (this.sable$handle != null || this.sable$hasConstraint) return;

        final int dx = direction.getStepX();
        final int dy = direction.getStepY();
        final int dz = direction.getStepZ();
        final Vec3 start = JOMLConversion.toMojang(rayStartPosition);
        final Vec3 end = start.add(dx * DISTANCE_TOLERANCE, dy * DISTANCE_TOLERANCE, dz * DISTANCE_TOLERANCE);

        final BlockHitResult clip = this.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (clip.getType() != HitResult.Type.MISS && SuperGlueEntity.isValidFace(this.level, clip.getBlockPos(), direction.getOpposite())) {
            final Vec3 hitLocation = clip.getLocation();
            final BlockPos otherPos = clip.getBlockPos();
            final Vector3d from = JOMLConversion.toJOML(start);
            final Vector3d to = JOMLConversion.toJOML(hitLocation);

            final ActiveSableCompanion helper = Sable.HELPER;
            final ServerSubLevel thisSubLevel = (ServerSubLevel) helper.getContaining(this.level, this.getBlockPos());
            final ServerSubLevel otherSubLevel = (ServerSubLevel) helper.getContaining(this.level, otherPos);
            final Quaterniondc first = thisSubLevel != null ? thisSubLevel.logicalPose().orientation() : JOMLConversion.QUAT_IDENTITY;
            final Quaterniondc second = otherSubLevel != null ? otherSubLevel.logicalPose().orientation() : JOMLConversion.QUAT_IDENTITY;

            final Direction hitDirection = clip.getDirection().getOpposite();

            // we want to align both primary axes of the stickers
            final Vector3d globalDirectionA = first.transform(new Vector3d(dx, dy, dz));
            final Vector3d globalDirectionB = second.transform(new Vector3d(hitDirection.getStepX(), hitDirection.getStepY(), hitDirection.getStepZ()));

            // get the angle & axis of global alignment
            final Vector3d axis = new Vector3d(globalDirectionA).cross(globalDirectionB).normalize();
            final double dot = globalDirectionA.dot(globalDirectionB);

            // prevent NaN or invalid facing directions
            if (dot < 1e-6 || dot > 1.0) {
                return;
            }

            final double angle = Math.acos(dot);

            // tolerance it so we don't snap at extreme angles
            if (angle > Math.toRadians(ANGLE_TOLERANCE)) {
                return;
            }

            // apply constraint
            this.sable$attachedPos = otherPos;
            this.sable$constraintPos1 = from;
            this.sable$constraintPos2 = to;

            // alignment rotation has to be in global space
            this.sable$constraintOrientation = new Quaterniond().rotateAxis(-angle, axis)
                    .mul(second).premul(first.conjugate(new Quaterniond()));

            this.sable$applyConstraint(thisSubLevel, otherSubLevel);
        }
    }

    @Unique
    private void sable$applyConstraint(final ServerSubLevel thisSubLevel, final ServerSubLevel otherSubLevel) {
        if (!(this.level instanceof final ServerLevel serverLevel)) {
            throw new IllegalStateException("StickerBlockEntity must be on a ServerLevel to apply constraints.");
        }

        if (thisSubLevel == otherSubLevel) {
            this.sable$removeConstraint();
            return;
        }

        final FixedConstraintConfiguration constraint = new FixedConstraintConfiguration(
                this.sable$constraintPos1,
                this.sable$constraintPos2,
                this.sable$constraintOrientation);

        final ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        this.sable$handle = container.physicsSystem().getPipeline().addConstraint(thisSubLevel, otherSubLevel, constraint);
        this.sable$hasConstraint = true;
        this.sendData();
    }

    @Override
    public void remove() {
        super.remove();
        this.sable$removeConstraint();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    public void tick(final CallbackInfo ci) {
        if (this.level.isClientSide()) {
            if (this.sable$hadConstraint != this.sable$hasConstraint) {
                this.sable$hadConstraint = this.sable$hasConstraint;
                if (this.sable$hasConstraint) {
                    SuperGlueItem.spawnParticles(this.level, this.worldPosition, this.getBlockState().getValue(StickerBlock.FACING), true);
                    CatnipServices.PLATFORM.executeOnClientOnly(() -> () -> this.playSound(true));
                } else {
                    CatnipServices.PLATFORM.executeOnClientOnly(() -> () -> this.playSound(false));
                }
            }
            return;
        }

        this.sable$tickConstraint();
    }

    @Override
    public void sable$saveToContraption(final HolderLookup.Provider registries) {
        this.sable$doNotSaveConstraint = true;
        this.saveWithFullMetadata(registries);
        this.sable$doNotSaveConstraint = false;
    }

    @Inject(method = "write", at = @At("TAIL"))
    public void write(final CompoundTag compound, final HolderLookup.Provider registries, final boolean clientPacket, final CallbackInfo ci) {
        if (clientPacket) {
            compound.putBoolean("SableHasConstraint", this.sable$handle != null);
        } else if (this.sable$handle != null && !this.sable$doNotSaveConstraint) {
            final CompoundTag constraint = new CompoundTag();
            final BlockPos blockPos = this.getBlockPos();
            constraint.putInt("ThisX", blockPos.getX());
            constraint.putInt("ThisY", blockPos.getY());
            constraint.putInt("ThisZ", blockPos.getZ());

            constraint.putInt("X", this.sable$attachedPos.getX());
            constraint.putInt("Y", this.sable$attachedPos.getY());
            constraint.putInt("Z", this.sable$attachedPos.getZ());
            constraint.putDouble("FromX", this.sable$constraintPos1.x);
            constraint.putDouble("FromY", this.sable$constraintPos1.y);
            constraint.putDouble("FromZ", this.sable$constraintPos1.z);
            constraint.putDouble("ToX", this.sable$constraintPos2.x);
            constraint.putDouble("ToY", this.sable$constraintPos2.y);
            constraint.putDouble("ToZ", this.sable$constraintPos2.z);
            constraint.putDouble("QuatX", this.sable$constraintOrientation.x);
            constraint.putDouble("QuatY", this.sable$constraintOrientation.y);
            constraint.putDouble("QuatZ", this.sable$constraintOrientation.z);
            constraint.putDouble("QuatW", this.sable$constraintOrientation.w);
            compound.put("SableConstraint", constraint);
        }
    }

    @Inject(method = "read", at = @At("TAIL"))
    public void read(final CompoundTag compound, final HolderLookup.Provider registries, final boolean clientPacket, final CallbackInfo ci) {
        if (clientPacket) {
            this.sable$hasConstraint = compound.getBoolean("SableHasConstraint");
        } else {
            if (compound.contains("SableConstraint", CompoundTag.TAG_COMPOUND)) {
                final CompoundTag constraint = compound.getCompound("SableConstraint");
                final BlockPos thisPos = new BlockPos(constraint.getInt("ThisX"), constraint.getInt("ThisY"), constraint.getInt("ThisZ"));

                if (!Objects.equals(this.getBlockPos(), thisPos)) {
                    this.sable$removeConstraint();
                    return;
                }

                this.sable$attachedPos = new BlockPos(constraint.getInt("X"), constraint.getInt("Y"), constraint.getInt("Z"));
                this.sable$constraintPos1 = new Vector3d(constraint.getDouble("FromX"), constraint.getDouble("FromY"), constraint.getDouble("FromZ"));
                this.sable$constraintPos2 = new Vector3d(constraint.getDouble("ToX"), constraint.getDouble("ToY"), constraint.getDouble("ToZ"));
                this.sable$constraintOrientation = new Quaterniond(constraint.getDouble("QuatX"), constraint.getDouble("QuatY"), constraint.getDouble("QuatZ"), constraint.getDouble("QuatW"));
            } else {
                this.sable$attachedPos = null;
                this.sable$constraintPos1 = null;
                this.sable$constraintPos2 = null;
                this.sable$constraintOrientation = null;
            }
        }
    }
}
