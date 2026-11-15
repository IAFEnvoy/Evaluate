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

    @Test
    void evictsLeastRecentlyUsedAnswersIncludingUnknowns() {
        ValueQueryCache<Integer> cache = new ValueQueryCache<>();
        List<Integer> requests = new ArrayList<>();
        for (int key = 0; key < 1024; key++) {
            cache.get(key, requests::add);
            cache.receive(requests.getLast(), key == 0 ? 0 : key + 0.5);
        }
        assertTrue(cache.get(0, requests::add).isEmpty()); // Reading an unknown answer also refreshes its age.
        cache.get(1024, requests::add);
        cache.receive(requests.getLast(), 1024.5);
        assertTrue(cache.get(0, requests::add).isEmpty());
        assertEquals(2.5, cache.get(2, requests::add).orElseThrow());
        assertEquals(1025, requests.size());
        assertTrue(cache.get(1, requests::add).isEmpty());
        assertEquals(1026, requests.size());
    }

    @Test
    void boundsPendingRequestsAndAcceptsNewOnesAfterAReply() {
        ValueQueryCache<Integer> cache = new ValueQueryCache<>();
        List<Integer> requests = new ArrayList<>();
        for (int key = 0; key < 65; key++) cache.get(key, requests::add);
        assertEquals(64, requests.size());
        cache.get(0, requests::add);
        assertEquals(64, requests.size());
        cache.receive(requests.getFirst(), Double.NaN);
        assertTrue(cache.get(0, requests::add).isEmpty());
        assertEquals(64, requests.size()); // Invalid answers are cached as unknown.
        cache.get(64, requests::add);
        assertEquals(65, requests.size());
        cache.receive(requests.getLast(), 0.25);
        assertEquals(0.25, cache.get(64, requests::add).orElseThrow());
    }
}
