package com.iafenvoy.evaluate.client;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleLinkedOpenHashMap;

import java.util.OptionalDouble;
import java.util.function.IntConsumer;

/**
 * Client-thread cache; request ids survive invalidation so old replies cannot repopulate it.
 */
public final class ValueQueryCache<K> {
    private static final int MAX_VALUES = 1024;
    private static final int MAX_PENDING = 64;
    private final Object2DoubleLinkedOpenHashMap<K> values = new Object2DoubleLinkedOpenHashMap<>();
    private final Int2ObjectMap<K> pending = new Int2ObjectOpenHashMap<>();
    private int nextRequestId;

    public OptionalDouble get(K key, IntConsumer request) {
        if (this.values.containsKey(key)) {
            double value = this.values.getAndMoveToLast(key);
            return value > 0 ? OptionalDouble.of(value) : OptionalDouble.empty();
        }
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
        this.values.putAndMoveToLast(key, Double.isFinite(value) && value > 0 ? value : 0);
        if (this.values.size() > MAX_VALUES) this.values.removeFirstDouble();
    }

    public void clear() {
        this.values.clear();
        this.pending.clear();
    }
}
