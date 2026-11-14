package com.iafenvoy.evaluate.client;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.IntConsumer;

/**
 * Client-thread cache; request ids survive invalidation so old replies cannot repopulate it.
 */
public final class ValueQueryCache<K> {
    private static final int MAX_VALUES = 1024;
    private static final int MAX_PENDING = 64;
    private final LinkedHashMap<K, OptionalDouble> values = new LinkedHashMap<>(16, 0.75F, true);
    private final Map<Integer, K> pending = new HashMap<>();
    private int nextRequestId;

    public OptionalDouble get(K key, IntConsumer request) {
        OptionalDouble value = this.values.get(key);
        if (value != null) return value;
        if (!this.pending.containsValue(key) && this.pending.size() < MAX_PENDING) {
            int requestId = this.nextRequestId++;
            this.pending.put(requestId, key);
            request.accept(requestId);
        }
        return OptionalDouble.empty();
    }

    public void receive(int requestId, double value) {
        K key = this.pending.remove(requestId);
        if (key == null) return;
        this.values.put(key, Double.isFinite(value) && value > 0 ? OptionalDouble.of(value) : OptionalDouble.empty());
        if (this.values.size() > MAX_VALUES) this.values.pollFirstEntry();
    }

    public void clear() {
        this.values.clear();
        this.pending.clear();
    }
}
