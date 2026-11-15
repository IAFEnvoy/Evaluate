package com.iafenvoy.evaluate.value;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;

import java.util.Map;
import java.util.Objects;

/**
 * Negative ingredient amounts credit returned containers or other byproducts.
 */
public record Conversion<K>(int count, K output, Object2IntMap<K> ingredients, double cost) {
    public Conversion {
        if (count <= 0) throw new IllegalArgumentException("Output count must be positive");
        Objects.requireNonNull(output);
        if (!Double.isFinite(cost) || cost < 0)
            throw new IllegalArgumentException("Cost must be finite and non-negative");
        Object2IntMap<K> copy = new Object2IntLinkedOpenHashMap<>();
        for (Object2IntMap.Entry<K> entry : ingredients.object2IntEntrySet()) {
            Objects.requireNonNull(entry.getKey());
            if (entry.getIntValue() != 0) copy.put(entry.getKey(), entry.getIntValue());
        }
        ingredients = Object2IntMaps.unmodifiable(copy);
    }

    public Conversion(int count, K output, Map<K, Integer> ingredients, double cost) {
        this(count, output, new Object2IntLinkedOpenHashMap<>(ingredients), cost);
    }

    public Conversion(int count, K output, Object2IntMap<K> ingredients) {
        this(count, output, ingredients, 0);
    }

    public Conversion(int count, K output, Map<K, Integer> ingredients) {
        this(count, output, ingredients, 0);
    }
}
