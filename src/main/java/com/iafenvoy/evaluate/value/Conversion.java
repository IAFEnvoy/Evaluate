package com.iafenvoy.evaluate.value;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Negative ingredient amounts credit returned containers or other byproducts.
 */
public record Conversion<K>(int count, K output, Map<K, Integer> ingredients, double cost) {
    public Conversion {
        if (count <= 0) throw new IllegalArgumentException("Output count must be positive");
        Objects.requireNonNull(output);
        if (!Double.isFinite(cost) || cost < 0)
            throw new IllegalArgumentException("Cost must be finite and non-negative");
        Map<K, Integer> copy = new LinkedHashMap<>();
        ingredients.forEach((key, amount) -> {
            Objects.requireNonNull(key);
            if (amount != 0) copy.put(key, amount);
        });
        ingredients = Collections.unmodifiableMap(copy);
    }

    public Conversion(int count, K output, Map<K, Integer> ingredients) {
        this(count, output, ingredients, 0);
    }
}
