package dev.ryanhcode.sable.api.command;

import com.google.gson.JsonObject;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.ryanhcode.sable.command.argument.SubLevelSelectorModifierType;
import dev.ryanhcode.sable.command.argument.SubLevelSelectorType;
import dev.ryanhcode.sable.command.argument.SubLevelSuggestionProvider;
import dev.ryanhcode.sable.command.argument.selector.SubLevelTarget;
import dev.ryanhcode.sable.command.argument.selector.SubLevelTargetNone;
import dev.ryanhcode.sable.command.argument.selector.SubLevelTargetSelector;
import dev.ryanhcode.sable.command.argument.selector.SubLevelTargetUUID;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class SubLevelArgumentType implements ArgumentType<SubLevelTarget> {

    public static final Function<SuggestionsBuilder, SuggestionsBuilder> NO_SUGGESTIONS = b -> b;
    public static final SimpleCommandExceptionType ERROR_SINGLE_SUB_LEVEL_REQUIRED =
            new SimpleCommandExceptionType(Component.translatable("argument.sable.single_sub_level_required"));
    public static final SimpleCommandExceptionType ERROR_INVALID_SELECTOR =
            new SimpleCommandExceptionType(Component.translatable("argument.sable.invalid_selector"));
    public static final SimpleCommandExceptionType ERROR_UNEXPECTED_END_OF_INPUT =
            new SimpleCommandExceptionType(Component.translatable("argument.sable.unexpected_end_of_input"));
    public static final SimpleCommandExceptionType ERROR_INVALID_UUID = new SimpleCommandExceptionType(Component.translatable("argument.sable.invalid_uuid"));
    public static final SimpleCommandExceptionType ERROR_CANNOT_FIND_SUB_LEVEL = new SimpleCommandExceptionType(Component.translatable("argument.sable.cannot_find_sub_level"));
    private static final String STATIC_WORLD = "static_world";
    private static final Collection<String> EXAMPLES = Arrays.stream(SubLevelSelectorType.values())
            .map(type -> "@" + type.getChar()).toList();
    private static Function<SuggestionsBuilder, SuggestionsBuilder> SELECTOR_SUGGESTIONS = NO_SUGGESTIONS;
    private final boolean allowStaticLevel;
    private final boolean allowMultiple;

    public SubLevelArgumentType(final boolean allowStaticLevel, final boolean allowMultiple) {
        this.allowStaticLevel = allowStaticLevel;
        this.allowMultiple = allowMultiple;
    }

    public static Collection<ServerSubLevel> getSubLevels(final CommandContext<CommandSourceStack> ctx, final String name) throws CommandSyntaxException {
        return ctx.getArgument(name, SubLevelTarget.class).getSubLevels(ctx.getSource());
    }

    public static ServerSubLevel getSingleSubLevel(final CommandContext<CommandSourceStack> ctx, final String name) throws CommandSyntaxException {
        final Collection<ServerSubLevel> subLevels = ctx.getArgument(name, SubLevelTarget.class).getSubLevels(ctx.getSource());
        if (subLevels.size() > 1) {
            throw ERROR_SINGLE_SUB_LEVEL_REQUIRED.create();
        }

        if (subLevels.isEmpty()) {
            throw SableCommandHelper.ERROR_NO_SUB_LEVELS_FOUND.create();
        }

        return subLevels.stream().findFirst().orElseThrow();
    }

    public static SubLevelArgumentType singleSubLevel() {
        return new SubLevelArgumentType(false, false);
    }

    public static SubLevelArgumentType subLevels() {
        return new SubLevelArgumentType(false, true);
    }

    public static SubLevelArgumentType subLevelsOrLevel() {
        return new SubLevelArgumentType(true, true);
    }

    private static @NotNull List<Pair<SubLevelSelectorModifierType, SubLevelSelectorModifierType.Modifier>> parseSelectorArguments(final StringReader reader) throws CommandSyntaxException {
        final List<Pair<SubLevelSelectorModifierType, SubLevelSelectorModifierType.Modifier>> modifiers = new ObjectArrayList<>();
        setSelectorSuggestions(reader, "[");

        final List<Pair<String, @Nullable Message>> permittedPreEntryToken = new ArrayList<>(SubLevelSelectorModifierType.getAllNamesWithTooltip()
                .stream().map(s -> Pair.of(s.first() + "=", s.second())).toList());
        permittedPreEntryToken.add(Pair.of("]", null));
        boolean isFirstEntry = true;

        if (reader.canRead() && reader.peek() == '[') {
            reader.skip();

            setSelectorSuggestionsWithTooltip(reader, permittedPreEntryToken);
            while (reader.canRead() && reader.peek() != ']') {
                if (reader.peek() == ',') {
                    reader.skip();
                }
                setSelectorSuggestionsWithTooltip(reader, permittedPreEntryToken);

                final String propertyName = readUntilEndOrCharacter(reader, '=');

                if (!reader.canRead() || reader.peek() != '=') {
                    throw ERROR_UNEXPECTED_END_OF_INPUT.createWithContext(reader);
                }
                reader.skip();

                final SubLevelSelectorModifierType modifierType = SubLevelSelectorModifierType.getModifier(propertyName, reader);
                if (modifierType == null) {
                    throw ERROR_UNEXPECTED_END_OF_INPUT.createWithContext(reader);
                }
                final SubLevelSelectorModifierType.Modifier modifier = modifierType.getParser().parse(reader);
                modifiers.add(Pair.of(modifierType, modifier));

                setSelectorSuggestionsWithTooltip(reader, permittedPreEntryToken);
                if (isFirstEntry) {
                    permittedPreEntryToken.add(Pair.of(",", null));
                    isFirstEntry = false;
                }
            }

            if (reader.canRead() && reader.peek() == ']') {
                reader.skip();
            } else {
                throw ERROR_UNEXPECTED_END_OF_INPUT.createWithContext(reader);
            }
        }

        return modifiers;
    }

    public static void setSelectorSuggestions(final StringReader reader, final String... suggested) {
        setSelectorSuggestions(reader, Arrays.asList(suggested));
    }

    public static void setSelectorSuggestions(final StringReader reader, final List<String> suggested) {
        setSelectorSuggestionsWithTooltip(reader, suggested.stream().map(s -> Pair.of(s, (Message) null)).toList());
    }

    @SafeVarargs
    public static void setSelectorSuggestionsWithTooltip(final StringReader reader, final Pair<String, @Nullable Message>... suggested) {
        setSelectorSuggestionsWithTooltip(reader, Arrays.asList(suggested));
    }

    public static void setSelectorSuggestionsWithTooltip(final StringReader reader, final List<Pair<String, @Nullable Message>> suggested) {
        final int cursor = reader.getCursor();
        SELECTOR_SUGGESTIONS = builder -> {
            final SuggestionsBuilder nextSuggestion = builder.createOffset(cursor);
            final String input = builder.getInput().substring(cursor);
            for (final Pair<String, @Nullable Message> suggestion : suggested) {
                if (!suggestion.first().startsWith(input)) {
                    continue;
                }
                if (suggestion.second() != null) {
                    nextSuggestion.suggest(suggestion.first(), suggestion.second());
                } else {
                    nextSuggestion.suggest(suggestion.first());
                }
            }
            return nextSuggestion;
        };
    }

    public static String readUntilEndOrCharacter(final StringReader reader, final char character) throws CommandSyntaxException {
        final StringBuilder builder = new StringBuilder();
        while (reader.canRead() && reader.peek() != character) {
            builder.append(reader.read());
        }
        if (builder.isEmpty()) {
            throw ERROR_UNEXPECTED_END_OF_INPUT.create();
        }
        return builder.toString();
    }

    @Override
    public SubLevelTarget parse(final StringReader reader) throws CommandSyntaxException {
        final ObjectList<Pair<String, @Nullable Message>> allowedSelectors = new ObjectArrayList<>();
        if (this.allowStaticLevel) {
            allowedSelectors.add(Pair.of(STATIC_WORLD, Component.translatable("argument.sable.body.static_world")));
        }
        for (final SubLevelSelectorType selector : SubLevelSelectorType.values()) {
            allowedSelectors.add(Pair.of("@" + selector.getChar(), selector.getTooltip()));
        }
        setSelectorSuggestionsWithTooltip(reader, allowedSelectors);

        if (this.allowStaticLevel && reader.canRead(STATIC_WORLD.length()) && reader.peek() == STATIC_WORLD.charAt(0)) {
            final String staticWorld = reader.readString();

            if (!staticWorld.equals(STATIC_WORLD)) {
                throw ERROR_INVALID_SELECTOR.create();
            }

            return SubLevelTargetNone.INSTANCE;
        }

        if (!reader.canRead()) {
            throw ERROR_INVALID_SELECTOR.create();
        }

        final char firstChar = reader.peek();

        if (firstChar == '@') {
            reader.skip();
            return this.parseSelector(reader);
        } else {
            return this.parseUUID(reader);
        }
    }

    private SubLevelTarget parseSelector(final StringReader reader) throws CommandSyntaxException {
        if (!reader.canRead()) {
            throw ERROR_INVALID_SELECTOR.create();
        }

        final SubLevelSelectorType selectorType = SubLevelSelectorType.of(reader.read());
        if (selectorType == null) {
            throw ERROR_INVALID_SELECTOR.create();
        }

        int maximumResults = Integer.MAX_VALUE;

        if (selectorType.single()) {
            maximumResults = 1;
        }

        final List<Pair<SubLevelSelectorModifierType, SubLevelSelectorModifierType.Modifier>> modifiers = parseSelectorArguments(reader);

        for (final Pair<SubLevelSelectorModifierType, SubLevelSelectorModifierType.Modifier> modifierPair : modifiers) {
            maximumResults = Math.min(maximumResults, modifierPair.second().getMaxResults());
        }

        // If we don't allow multiple sub-levels and we have more than one, throw a fit
        if (maximumResults > 1 && !this.allowMultiple) {
            throw ERROR_SINGLE_SUB_LEVEL_REQUIRED.create();
        }

        return new SubLevelTargetSelector(selectorType, modifiers);
    }

    private SubLevelTarget parseUUID(final StringReader reader) throws CommandSyntaxException {
        final String s = reader.readString();
        try {
            return new SubLevelTargetUUID(UUID.fromString(s));
        } catch (final IllegalArgumentException e) {
            throw ERROR_INVALID_UUID.createWithContext(reader);
        }
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(final CommandContext<S> pContext, SuggestionsBuilder builder) {
        final StringReader stringreader = new StringReader(builder.getInput());
        final int start = builder.getStart();
        stringreader.setCursor(start);
        SELECTOR_SUGGESTIONS = NO_SUGGESTIONS;
        try {
            this.parse(stringreader);
        } catch (final CommandSyntaxException ignored) {
        }
        builder = SELECTOR_SUGGESTIONS.apply(builder);

        if (pContext.getSource() instanceof final SubLevelSuggestionProvider suggestionProvider) {
            final SubLevel subLevel = suggestionProvider.getSelectedSubLevel();
            if (subLevel != null) {
                final String uuid = subLevel.getUniqueId().toString();
                final String input = builder.getInput().substring(start);
                if (uuid.startsWith(input)) {
                    builder.suggest(uuid);
                }
            }
        }
        return builder.buildFuture();
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public static class Info implements ArgumentTypeInfo<SubLevelArgumentType, SubLevelArgumentType.Info.Template> {
        private static final byte FLAG_MULTIPLE = 1;
        private static final byte FLAG_STATIC_ALLOWED = 2;

        public void serializeToNetwork(final SubLevelArgumentType.Info.Template template, final FriendlyByteBuf byteBuf) {
            int serialized = 0;
            if (template.allowMultiple) {
                serialized |= FLAG_MULTIPLE;
            }

            if (template.allowStaticLevel) {
                serialized |= FLAG_STATIC_ALLOWED;
            }

            byteBuf.writeByte(serialized);
        }

        public SubLevelArgumentType.Info.Template deserializeFromNetwork(final FriendlyByteBuf arg) {
            final byte serialized = arg.readByte();
            return new SubLevelArgumentType.Info.Template((serialized & FLAG_MULTIPLE) != 0, (serialized & FLAG_STATIC_ALLOWED) != 0);
        }

        public void serializeToJson(final SubLevelArgumentType.Info.Template arg, final JsonObject jsonObject) {
            jsonObject.addProperty("amount", arg.allowMultiple ? "single" : "multiple");
            jsonObject.addProperty("type", arg.allowStaticLevel ? "players" : "entities");
        }

        public SubLevelArgumentType.Info.Template unpack(final SubLevelArgumentType arg) {
            return new Template(arg.allowMultiple, arg.allowStaticLevel);
        }

        public final class Template implements ArgumentTypeInfo.Template<SubLevelArgumentType> {
            final boolean allowMultiple;
            final boolean allowStaticLevel;

            Template(final boolean allowMultiple, final boolean allowStaticLevel) {
                this.allowMultiple = allowMultiple;
                this.allowStaticLevel = allowStaticLevel;
            }

            public SubLevelArgumentType instantiate(final CommandBuildContext commandBuildContext) {
                return new SubLevelArgumentType(this.allowStaticLevel, this.allowMultiple);
            }

            public ArgumentTypeInfo<SubLevelArgumentType, ?> type() {
                return SubLevelArgumentType.Info.this;
            }
        }
    }

}