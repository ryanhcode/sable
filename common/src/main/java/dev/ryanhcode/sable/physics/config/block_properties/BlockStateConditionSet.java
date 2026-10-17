package dev.ryanhcode.sable.physics.config.block_properties;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.List;

public record BlockStateConditionSet(List<BlockStateCondition> blockStateConditions) {

    public static final Codec<BlockStateConditionSet> CODEC = Codec.STRING.comapFlatMap(BlockStateConditionSet::parse, BlockStateConditionSet::toString).stable();

    public static DataResult<BlockStateConditionSet> parse(final String value) {
        final String[] parts = value.split(",");
        final List<BlockStateCondition> conditions = new ArrayList<>();
        try {
            for (final String part : parts) {
                conditions.add(BlockStateCondition.parse(part));
            }
        } catch (final IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
        return DataResult.success(new BlockStateConditionSet(conditions));
    }

    @Override
    public String toString() {
        return String.join(",", this.blockStateConditions.stream().map(BlockStateCondition::toString).toList());
    }

    public boolean matches(final StateDefinition<Block, BlockState> stateDefinition, final BlockState state) {
        for (final BlockStateCondition condition : this.blockStateConditions) {
            final Property<?> property = stateDefinition.getProperty(condition.property());
            if (property == null) {
                return false;
            }

            final Comparable<?> expectedValue = property.getValue(condition.value()).orElse(null);
            if (expectedValue == null) {
                return false;
            }

            if (!state.getValue(property).equals(expectedValue)) {
                return false;
            }
        }
        return true;
    }

    public record BlockStateCondition(String property, String value) {
        public static BlockStateCondition parse(final String value) {
            final String[] parts = value.split("=");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid block state condition: " + value);
            }
            return new BlockStateCondition(parts[0], parts[1]);
        }

        @Override
        public String toString() {
            return this.property + "=" + this.value;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        List<BlockStateCondition> conditions = new ArrayList<>();

        public Builder expect(String property, String value) {
            this.conditions.add(new BlockStateCondition(property, value));
            return this;
        }

        public <T extends Comparable<T>> Builder expect(Property<T> property, T value) {
            return this.expect(property.getName(), property.getName(value));
        }

        public BlockStateConditionSet build() {
            if (this.conditions.isEmpty()) {
                throw new IllegalStateException("conditions must not be empty");
            }
            return new BlockStateConditionSet(this.conditions);
        }
    }
}
