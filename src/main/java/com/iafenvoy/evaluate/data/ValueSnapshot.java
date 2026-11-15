package com.iafenvoy.evaluate.data;

import com.iafenvoy.evaluate.api.FixedValue;
import com.iafenvoy.evaluate.api.ValueKey;
import com.iafenvoy.evaluate.api.ValueOverride;
import com.iafenvoy.saturn.util.matcher.ItemMatcher;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMaps;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Keep stack-sensitive declarations live instead of caching their answer by item id.
 */
public record ValueSnapshot(Object2DoubleMap<ValueKey.ItemValue> values, List<FixedValue> fixedValues,
                            List<ValueOverride> overrides, Object2DoubleMap<ValueKey.ItemValue> worldValues) {
    public static final ValueSnapshot EMPTY = new ValueSnapshot(Object2DoubleMaps.emptyMap(), List.of(), List.of(), Object2DoubleMaps.emptyMap());

    public ValueSnapshot {
        values = Object2DoubleMaps.unmodifiable(new Object2DoubleOpenHashMap<>(values));
        overrides = List.copyOf(overrides);
        fixedValues = List.copyOf(fixedValues);
        worldValues = Object2DoubleMaps.unmodifiable(new Object2DoubleOpenHashMap<>(worldValues));
    }

    public OptionalDouble get(ItemStack stack) {
        if (stack.isEmpty()) return OptionalDouble.empty();
        ValueKey.ItemValue key = ValueKey.ItemValue.of(stack);
        if (this.worldValues.containsKey(key)) {
            double worldValue = this.worldValues.getDouble(key);
            return worldValue > 0 ? OptionalDouble.of(worldValue) : OptionalDouble.empty();
        }
        Optional<ValueOverride> override = ItemMatcher.find(this.overrides.stream(), stack);
        if (override.isPresent())
            return override.get().value() > 0 ? OptionalDouble.of(override.get().value()) : OptionalDouble.empty();
        Optional<FixedValue> explicit = ItemMatcher.find(this.fixedValues.stream(), stack);
        if (explicit.isPresent()) {
            double value = explicit.get().value();
            return value > 0 ? OptionalDouble.of(value) : OptionalDouble.empty();
        }
        return this.values.containsKey(key) ? OptionalDouble.of(this.values.getDouble(key)) : OptionalDouble.empty();
    }
}
