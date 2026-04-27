package dev.ryanhcode.sable.neoforge.platform;

import dev.ryanhcode.sable.platform.SablePlatform;
import net.createmod.catnip.levelWrappers.WrappedServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.common.CommonHooks;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class SablePlatformImpl implements SablePlatform {

    @Override
    public boolean isWrappedLevel(@Nullable final Level level) {
        if (FMLLoader.getLoadingModList().getModFileById("create") != null) {
            return level instanceof WrappedServerLevel;
        }

        return false;
    }

    @Override
    public boolean isBlockstateLadder(final BlockState state, final Level level, final BlockPos pos, final LivingEntity entity) {
        return CommonHooks.isLivingOnLadder(state, level, pos, entity).isPresent();
    }
}
