package com.iafenvoy.evaluate_tests;

import com.iafenvoy.evaluate.client.ValueQueryCache;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValueQueryCacheTest {
    @Test
    void deduplicatesPendingAndCachesFractionalAndUnknownAnswers() {
        ValueQueryCache<String> cache = new ValueQueryCache<>();
        List<Integer> requests = new ArrayList<>();
        assertTrue(cache.get("item", requests::add).isEmpty());
        cache.get("item", requests::add);
        assertEquals(1, requests.size());
        cache.receive(requests.getFirst(), 0.125);
        assertEquals(0.125, cache.get("item", requests::add).orElseThrow());
        cache.get("unknown", requests::add);
        cache.receive(requests.getLast(), 0);
        assertTrue(cache.get("unknown", requests::add).isEmpty());
        assertEquals(2, requests.size());
    }

    @Test
    void ignoresRepliesFromBeforeReloadOrDisconnect() {
        ValueQueryCache<String> cache = new ValueQueryCache<>();
        List<Integer> requests = new ArrayList<>();
        cache.get("item", requests::add);
        int staleRequest = requests.getFirst();
        cache.clear();
        cache.get("item", requests::add);
        int currentRequest = requests.getLast();
        cache.receive(staleRequest, 10);
        assertTrue(cache.get("item", requests::add).isEmpty());
        cache.receive(currentRequest, 20.5);
        assertEquals(20.5, cache.get("item", requests::add).orElseThrow());
        assertEquals(2, requests.size());
    }
}
