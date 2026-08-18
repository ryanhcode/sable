package dev.ryanhcode.sable.command.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.ryanhcode.sable.api.command.SubLevelArgumentType;
import dev.ryanhcode.sable.command.argument.modifier_type.*;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.network.chat.Component;

public class SubLevelSelectorModifiers {

    public static final SimpleCommandExceptionType EXPECTED_END_OF_MODIFIER =
            new SimpleCommandExceptionType(Component.translatable("argument.sable.sub_level.expected_end_of_modifier"));
    public static final SimpleCommandExceptionType EXPECTED_POSITIVE_INTEGER =
            new SimpleCommandExceptionType(Component.translatable("argument.sable.sub_level.expected_positive_integer"));
    public static final SimpleCommandExceptionType EXPECTED_POSITIVE_DECIMAL =
            new SimpleCommandExceptionType(Component.translatable("argument.sable.sub_level.expected_positive_decimal"));
    public static final SimpleCommandExceptionType EXPECTED_SORTING_TYPE =
            new SimpleCommandExceptionType(Component.translatable("argument.sable.sub_level.expected_sorting"));
    public static final SimpleCommandExceptionType EXPECTED_POSITIVE_RANGE =
            new SimpleCommandExceptionType(Component.translatable("argument.sable.sub_level.expected_positive_range"));

    private static void registerDoubleArgument(final String name, final boolean onlyPositive, final SubLevelDoubleFilter.Factory factory) {
        SubLevelSelectorModifierType.registerType(name, (reader) -> {
            final int i = reader.getCursor();
            final double value = reader.readDouble();
            if (onlyPositive && value < 0) {
                reader.setCursor(i);
                throw EXPECTED_POSITIVE_DECIMAL.createWithContext(reader);
            }
            return factory.create(value);
        }, SubLevelSelectorModifierType.FilterPriority.FILTER);
    }

    private static void registerDoubleRangeArgument(final String name, final boolean onlyPositive, final SubLevelDoubleRangeFilter.Factory factory) {
        SubLevelSelectorModifierType.registerType(name, (reader) -> {
            final int i = reader.getCursor();
            final MinMaxBounds.Doubles doubles = MinMaxBounds.Doubles.fromReader(reader);
            if (onlyPositive && ((
                        doubles.min().isPresent() && doubles.min().get() < 0
                    ) || (
                        doubles.max().isPresent() && doubles.max().get() < 0
                    ))) {
                reader.setCursor(i);
                throw EXPECTED_POSITIVE_RANGE.createWithContext(reader);
            }
            return factory.create(doubles);
        }, SubLevelSelectorModifierType.FilterPriority.FILTER);
    }

    public static void registerModifiers() {
        registerDoubleRangeArgument("distance", true, SubLevelDoubleRangeFilter.squared(
                (subLevel, sourcePos) -> subLevel.logicalPose().position().distanceSquared(sourcePos)
        ));
        registerDoubleRangeArgument("x", false, SubLevelDoubleRangeFilter.linear(
                (subLevel, sourcePos) -> subLevel.logicalPose().position().x()
        ));
        registerDoubleRangeArgument("y", false, SubLevelDoubleRangeFilter.linear(
                (subLevel, sourcePos) -> subLevel.logicalPose().position().y()
        ));
        registerDoubleRangeArgument("z", false, SubLevelDoubleRangeFilter.linear(
                (subLevel, sourcePos) -> subLevel.logicalPose().position().z()
        ));
        registerDoubleArgument("dx", false, SubLevelDoubleFilter.factory(
                (subLevel, sourcePos, value) -> {
                    final double dx = subLevel.logicalPose().position().x() - sourcePos.x();
                    if (value < 0) {
                        return dx < 0 && dx > value;
                    } else {
                        return dx > 0 && dx < value;
                    }
                }
        ));
        registerDoubleArgument("dy", false, SubLevelDoubleFilter.factory(
                (subLevel, sourcePos, value) -> {
                    final double dy = subLevel.logicalPose().position().y() - sourcePos.y();
                    if (value < 0) {
                        return dy < 0 && dy > value;
                    } else {
                        return dy > 0 && dy < value;
                    }
                }
        ));
        registerDoubleArgument("dz", false, SubLevelDoubleFilter.factory(
                (subLevel, sourcePos, value) -> {
                    final double dz = subLevel.logicalPose().position().z() - sourcePos.z();
                    if (value < 0) {
                        return dz < 0 && dz > value;
                    } else {
                        return dz > 0 && dz < value;
                    }
                }
        ));

        registerDoubleRangeArgument("vx", false, SubLevelDoubleRangeFilter.linear(
                (subLevel, sourcePos) -> subLevel.latestLinearVelocity.x
        ));
        registerDoubleRangeArgument("vy", false, SubLevelDoubleRangeFilter.linear(
                (subLevel, sourcePos) -> subLevel.latestLinearVelocity.y
        ));
        registerDoubleRangeArgument("vz", false, SubLevelDoubleRangeFilter.linear(
                (subLevel, sourcePos) -> subLevel.latestLinearVelocity.z
        ));
        registerDoubleRangeArgument("speed", true, SubLevelDoubleRangeFilter.squared(
                (subLevel, sourcePos) -> subLevel.latestLinearVelocity.lengthSquared()
        ));

        registerDoubleRangeArgument("mass", true, SubLevelDoubleRangeFilter.linear(
                (subLevel, sourcePos) -> subLevel.getMassTracker().getMass()
        ));

        registerDoubleRangeArgument("volume", true, SubLevelDoubleRangeFilter.linear(
                (subLevel, sourcePos) -> subLevel.getPlot().getBoundingBox().volume()
        ));
        registerDoubleRangeArgument("width", true, SubLevelDoubleRangeFilter.linear(
                (subLevel, sourcePos) -> subLevel.getPlot().getBoundingBox().width()
        ));
        registerDoubleRangeArgument("height", true, SubLevelDoubleRangeFilter.linear(
                (subLevel, sourcePos) -> subLevel.getPlot().getBoundingBox().height()
        ));
        registerDoubleRangeArgument("length", true, SubLevelDoubleRangeFilter.linear(
                (subLevel, sourcePos) -> subLevel.getPlot().getBoundingBox().length()
        ));

        SubLevelSelectorModifierType.registerType("name", (reader) -> {
            final String name = readUntilEndOfModifier(reader);
            return new SubLevelNameFilter(name);
        }, SubLevelSelectorModifierType.FilterPriority.FILTER);

        SubLevelSelectorModifierType.registerType("sort", (reader) -> {
            SubLevelArgumentType.setSelectorSuggestions(reader, "nearest", "furthest");
            final String filtering = tryReadString(reader, EXPECTED_SORTING_TYPE, "nearest", "furthest");
            expectEndOfModifier(reader);
            return new SubLevelSortModifier(filtering);
        }, SubLevelSelectorModifierType.FilterPriority.SORTING);

        SubLevelSelectorModifierType.registerType("limit", (reader) -> {
            final int limit = readPositiveIntStrict(reader);
            return new SubLevelLimitFilter(limit);
        }, SubLevelSelectorModifierType.FilterPriority.SORTING_SELECTION);
    }

    /**
     * Normal {@link StringReader#readInt()} will try to read a {@code .} as a decimal point and fail, this will ignore all non 0-9 characters and terminate
     */
    private static Integer readPositiveIntStrict(final StringReader reader) throws CommandSyntaxException {
        final StringBuilder builder = new StringBuilder();
        while (reader.canRead() && reader.peek() >= '0' && reader.peek() <= '9') {
            builder.append(reader.read());
        }
        if (builder.isEmpty()) {
            throw EXPECTED_POSITIVE_INTEGER.createWithContext(reader);
        }
        return Integer.parseInt(builder.toString());
    }

    private static boolean isEndOfModifier(final StringReader reader) {
        return reader.peek() == ',' || reader.peek() == ']';
    }

    private static String readUntilEndOfModifier(final StringReader reader) throws CommandSyntaxException {
        final StringBuilder builder = new StringBuilder();
        if (reader.canRead() && reader.peek() == '"') {
            reader.skip();
            boolean thereIsNoEscape = false;
            while (reader.canRead() && (thereIsNoEscape || reader.peek() != '"')) {
                if (!thereIsNoEscape && reader.peek() == '\\') {
                    thereIsNoEscape = true;
                    reader.skip();
                } else {
                    builder.append(reader.read());
                    thereIsNoEscape = false;
                }
            }
            if (reader.canRead()) {
                reader.skip();
            } else {
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedEndOfQuote().createWithContext(reader);
            }
        } else {
            while (reader.canRead() && !isEndOfModifier(reader)) {
                builder.append(reader.read());
            }
        }
        return builder.toString();
    }

    private static String tryReadString(final StringReader reader, final SimpleCommandExceptionType exception, final String... accepted) throws CommandSyntaxException {
        final StringBuilder builder = new StringBuilder();
        while (reader.canRead()) {
            if (isEndOfModifier(reader)) {
                throw exception.createWithContext(reader);
            }
            builder.append(reader.read());
            for (final String s : accepted) {
                if (builder.toString().equals(s)) {
                    return builder.toString();
                }
            }
        }
        throw exception.createWithContext(reader);
    }

    private static void expectEndOfModifier(final StringReader reader) throws CommandSyntaxException {
        if (!reader.canRead() || !isEndOfModifier(reader)) {
            throw EXPECTED_END_OF_MODIFIER.createWithContext(reader);
        }
    }

}
