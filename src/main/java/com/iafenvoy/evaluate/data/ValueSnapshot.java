package com.iafenvoy.evaluate.data;

import com.iafenvoy.evaluate.api.FixedValue;
import com.iafenvoy.evaluate.api.ValueKey;
import com.iafenvoy.evaluate.api.ValueOverride;
import com.iafenvoy.saturn.util.matcher.ItemMatcher;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Keep stack-sensitive declarations live instead of caching their answer by item id.
 */
public record ValueSnapshot(Map<ValueKey.ItemValue, Double> values, List<FixedValue> fixedValues,
                            List<ValueOverride> overrides, Map<ValueKey.ItemValue, Double> worldValues) {
    public static final ValueSnapshot EMPTY = new ValueSnapshot(Map.of(), List.of(), List.of(), Map.of());

    public ValueSnapshot {
        values = Map.copyOf(values);
        overrides = List.copyOf(overrides);
        fixedValues = List.copyOf(fixedValues);
        worldValues = Map.copyOf(worldValues);
    }

    public OptionalDouble get(ItemStack stack) {
        if (stack.isEmpty()) return OptionalDouble.empty();
        Double worldValue = this.worldValues.get(ValueKey.ItemValue.of(stack));
        if (worldValue != null) return worldValue > 0 ? OptionalDouble.of(worldValue) : OptionalDouble.empty();
        Optional<ValueOverride> override = ItemMatcher.find(this.overrides.stream(), stack);
        if (override.isPresent())
            return override.get().value() > 0 ? OptionalDouble.of(override.get().value()) : OptionalDouble.empty();
        Optional<FixedValue> explicit = ItemMatcher.find(this.fixedValues.stream(), stack);
        if (explicit.isPresent()) {
            double value = explicit.get().value();
            return value > 0 ? OptionalDouble.of(value) : OptionalDouble.empty();
        }
        Double value = this.values.get(ValueKey.ItemValue.of(stack));
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }
}
