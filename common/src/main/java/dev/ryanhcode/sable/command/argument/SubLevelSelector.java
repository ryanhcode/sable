package dev.ryanhcode.sable.command.argument;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.command.SableCommandHelper;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.*;

public class SubLevelSelector {

    private final SubLevelSelectorType type;
    private final List<Pair<SubLevelSelectorModifierType, SubLevelSelectorModifierType.Modifier>> modifiers;

    public SubLevelSelector(final SubLevelSelectorType type, final List<Pair<SubLevelSelectorModifierType, SubLevelSelectorModifierType.Modifier>> modifiers) {
        this.type = type;
        this.modifiers = modifiers;
    }

    public SubLevelSelectorType getSelectorType() {
        return this.type;
    }

    public Collection<ServerSubLevel> getSubLevels(final CommandSourceStack source) throws CommandSyntaxException {
        if (this.type == null) {
            return List.of();
        }

        final ServerLevel level = source.getLevel();
        final ServerSubLevelContainer container = SableCommandHelper.requireSubLevelContainer(source);

        final Iterable<ServerSubLevel> containerBodies = container.getAllSubLevels();
        final Collection<ServerSubLevel> bodies = new ObjectArrayList<>();

        for (final ServerSubLevel subLevel : containerBodies) {
            bodies.add(subLevel);
        }

        if (bodies.isEmpty()) {
            return Collections.emptySet();
        }

        final ActiveSableCompanion helper = Sable.HELPER;
        final Collection<ServerSubLevel> collectedSubLevels = switch (this.type) {
            case ALL -> new HashSet<>(bodies);
            case NEAREST -> {
                double closest = Double.MAX_VALUE;
                ServerSubLevel closestSubLevel = null;

                for (final ServerSubLevel body : bodies) {
                    final Vec3 sourcePosition = helper.projectOutOfSubLevel(source.getLevel(), source.getPosition());
                    final double distance = body.logicalPose().position().distance(sourcePosition.x, sourcePosition.y, sourcePosition.z);

                    if (distance < closest) {
                        closest = distance;
                        closestSubLevel = body;
                    }
                }

                yield Collections.singleton(closestSubLevel);
            }
            case RANDOM -> {
                final List<ServerSubLevel> list = new ArrayList<>(bodies);
                yield Collections.singleton(list.get(level.random.nextInt(list.size())));
            }
            case INSIDE -> {
                final ServerSubLevel subLevel = (ServerSubLevel) helper.getContaining(level, source.getPosition());
                if (subLevel != null) {
                    yield Collections.singleton(subLevel);
                } else {
                    yield Collections.emptySet();
                }
            }
            case TRACKING -> {
                if (source.getEntity() == null) {
                    yield Collections.emptySet();
                }

                final ServerSubLevel subLevel = (ServerSubLevel) Sable.HELPER.getTrackingSubLevel(source.getEntity());

                if (subLevel != null) {
                    yield Collections.singleton(subLevel);
                } else {
                    yield Collections.emptySet();
                }
            }
            case VIEWED -> {
                if (source.getEntity() != null) {
                    final HitResult res = source.getEntity().pick(100.0, 1.0f, true);

                    if (res instanceof final BlockHitResult blockHitResult) {
                        final ServerSubLevel containing = (ServerSubLevel) helper.getContaining(level, blockHitResult.getBlockPos());
                        if (containing != null) {
                            yield Collections.singleton(containing);
                        } else {
                            yield Collections.emptySet();
                        }
                    } else {
                        yield Collections.emptySet();
                    }
                } else {
                    yield Collections.emptySet();
                }
            }
            case LATEST -> {
                final List<ServerSubLevel> subLevels = container.getAllSubLevels();
                if (subLevels.isEmpty()) {
                    yield Collections.emptySet();
                }
                yield Collections.singleton(subLevels.getLast());
            }
        };

        List<ServerSubLevel> modifiedSubLevels = new ObjectArrayList<>(collectedSubLevels);

        final Vector3d position = new Vector3d(source.getPosition().x, source.getPosition().y, source.getPosition().z);
        this.modifiers.sort(
                Comparator.comparingInt(a -> a.first().getFilterPriority().ordinal())
        );
        for (final Pair<SubLevelSelectorModifierType, SubLevelSelectorModifierType.Modifier> modifier : this.modifiers) {
            modifiedSubLevels = modifier.right().apply(modifiedSubLevels, position);
        }

        return modifiedSubLevels;
    }

}