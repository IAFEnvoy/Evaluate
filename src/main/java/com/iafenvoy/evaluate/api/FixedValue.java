package com.iafenvoy.evaluate.api;

import com.iafenvoy.saturn.util.matcher.ItemMatcher;

import java.util.List;
import java.util.Objects;

/**
 * A stack-sensitive base value contributed by an evaluator.
 */
public record FixedValue(ItemMatcher matcher, double value) implements ItemMatcher {
    public FixedValue {
        Objects.requireNonNull(matcher);
        if (!Double.isFinite(value) || value < 0)
            throw new IllegalArgumentException("Value must be finite and non-negative");
    }

    @Override
    public List<Entry> entries() {
        return this.matcher.entries();
    }

    @Override
    public int priority() {
        return this.matcher.priority();
    }
}
