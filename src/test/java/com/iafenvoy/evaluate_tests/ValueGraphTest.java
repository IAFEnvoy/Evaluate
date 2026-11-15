package com.iafenvoy.evaluate_tests;

import com.iafenvoy.evaluate.value.Conversion;
import com.iafenvoy.evaluate.value.ValueGraph;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ValueGraphTest {
    private static final double TOLERANCE = 1.0E-12;

    @Test
    void conversionsKeepAnImmutableCopyOfPrimitiveCounts() {
        Object2IntLinkedOpenHashMap<String> ingredients = new Object2IntLinkedOpenHashMap<>();
        ingredients.put("source", 2);
        ingredients.put("unused", 0);
        Conversion<String> conversion = new Conversion<>(1, "output", ingredients);
        ingredients.put("source", 100);
        assertEquals(2, conversion.ingredients().getInt("source"));
        assertFalse(conversion.ingredients().containsKey("unused"));
        assertThrows(UnsupportedOperationException.class, () -> conversion.ingredients().put("source", 3));
        ValueGraph<String> graph = new ValueGraph<>();
        graph.setFixed("source", 1.25);
        graph.add(conversion);
        assertEquals(2.5, graph.calculate().getDouble("output"));
    }

    @Test
    void propagatesCountsAndChoosesCheapestKnownAlternative() {
        ValueGraph<String> graph = new ValueGraph<>();
        graph.setFixed("log", 32);
        graph.setFixed("expensive_log", 64);
        graph.add(new Conversion<>(4, "plank", Map.of("log", 1)));
        graph.add(new Conversion<>(4, "plank", Map.of("expensive_log", 1)));
        graph.add(new Conversion<>(4, "stick", Map.of("plank", 2)));
        graph.add(new Conversion<>(1, "unknown", Map.of("unseeded", 1)));
        assertEquals(Map.of("log", 32.0, "expensive_log", 64.0, "plank", 8.0, "stick", 4.0), graph.calculate());
    }

    @Test
    void fixedValuesLockAgainstCheaperCandidatesAndPropagate() {
        ValueGraph<String> graph = new ValueGraph<>();
        graph.setFixed("base", 10);
        graph.setFixed("locked", 100);
        graph.add(new Conversion<>(1, "locked", Map.of("base", 1)));
        graph.add(new Conversion<>(1, "child", Map.of("locked", 2)));
        Map<String, Double> result = graph.calculate();
        assertEquals(100, result.get("locked"));
        assertEquals(200, result.get("child"));
    }

    @Test
    void subtractsContainerValueAndDistinguishesUnknownFromFree() {
        ValueGraph<String> graph = new ValueGraph<>();
        graph.setFixed("milk_bucket", 900);
        graph.setFixed("bucket", 768);
        graph.setFixed("water", 0.0);
        graph.add(new Conversion<>(1, "cake", Map.of("milk_bucket", 3, "bucket", -3)));
        graph.add(new Conversion<>(1, "water_bucket", Map.of("bucket", 1, "water", 1000)));
        graph.add(new Conversion<>(1, "unknown", Map.of("bucket", 1, "missing", 1)));
        assertEquals(396, graph.calculate().get("cake"));
        assertEquals(768, graph.calculate().get("water_bucket"));
        assertFalse(graph.calculate().containsKey("unknown"));
        assertFalse(graph.calculate().containsKey("water"));
    }

    @Test
    void preservesDecimalsAndSubUnitValuesThroughPacking() {
        ValueGraph<String> graph = new ValueGraph<>();
        graph.setFixed("ingot", 256);
        graph.add(new Conversion<>(9, "nugget", Map.of("ingot", 1)));
        graph.add(new Conversion<>(1, "repacked", Map.of("nugget", 9)));
        graph.setFixed("tiny_source", 1);
        graph.add(new Conversion<>(3, "tiny", Map.of("tiny_source", 1)));
        graph.add(new Conversion<>(1, "three_tiny", Map.of("tiny", 3)));
        Map<String, Double> result = graph.calculate();
        assertEquals(256.0 / 9, result.get("nugget"), TOLERANCE);
        assertEquals(256.0, result.get("repacked"), TOLERANCE);
        assertEquals(1.0 / 3, result.get("tiny"), TOLERANCE);
        assertEquals(1.0, result.get("three_tiny"), TOLERANCE);
    }

    @Test
    void zeroNetCostIsFreeAndCanParticipateInLaterRecipes() {
        ValueGraph<String> graph = new ValueGraph<>();
        graph.setFixed("container", 10);
        graph.setFixed("full_container", 10);
        graph.add(new Conversion<>(1, "free", Map.of("full_container", 1, "container", -1)));
        graph.add(new Conversion<>(1, "out", Map.of("free", 1, "container", 1)));
        assertFalse(graph.calculate().containsKey("free"));
        assertEquals(10, graph.calculate().get("out"));
    }

    @Test
    void unseededAndBalancedCyclesDoNotInventValues() {
        ValueGraph<String> graph = new ValueGraph<>();
        graph.add(new Conversion<>(1, "a", Map.of("b", 1)));
        graph.add(new Conversion<>(1, "b", Map.of("a", 1)));
        assertTrue(graph.calculate().isEmpty());
        graph.setFixed("seed", 10);
        graph.add(new Conversion<>(1, "a", Map.of("seed", 1)));
        assertEquals(10, graph.calculate().get("b"));
    }

    @Test
    void rejectsFractionalGainCyclesAndTheirStaleDescendants() {
        ValueGraph<String> graph = new ValueGraph<>();
        graph.setFixed("seed", 1);
        graph.add(new Conversion<>(1, "a", Map.of("seed", 1)));
        graph.add(new Conversion<>(2, "b", Map.of("a", 1)));
        graph.add(new Conversion<>(1, "a", Map.of("b", 1)));
        graph.add(new Conversion<>(1, "child", Map.of("a", 1000)));
        Map<String, Double> result = assertTimeoutPreemptively(Duration.ofSeconds(3), graph::calculate);
        assertFalse(graph.rejectedCycles().isEmpty());
        assertEquals(Map.of("seed", 1.0), result);
    }

    @Test
    void unrelatedRecipesCannotHideGainCyclesAtTheFloatingPointFloor() {
        ValueGraph<String> graph = new ValueGraph<>();
        graph.setFixed("seed", 1);
        graph.add(new Conversion<>(1, "a", Map.of("seed", 1)));
        graph.add(new Conversion<>(2, "b", Map.of("a", 1)));
        graph.add(new Conversion<>(1, "a", Map.of("b", 1)));
        graph.add(new Conversion<>(1, "child", Map.of("a", 1000)));
        for (int i = 0; i < 600; i++) graph.add(new Conversion<>(1, "unrelated_" + i, Map.of(), 10));
        Map<String, Double> result = assertTimeoutPreemptively(Duration.ofSeconds(3), graph::calculate);
        assertEquals(Set.of("a", "b"), graph.rejectedCycles());
        assertFalse(result.containsKey("a"));
        assertFalse(result.containsKey("b"));
        assertFalse(result.containsKey("child"));
        assertEquals(601, result.size());
        assertEquals(10, result.get("unrelated_599"));
    }

    @Test
    void detectsGainCyclesEvenWhenTheSeedIsAlreadySubnormal() {
        ValueGraph<String> graph = new ValueGraph<>();
        graph.setFixed("seed", Double.MIN_VALUE);
        graph.add(new Conversion<>(1, "a", Map.of("seed", 1)));
        graph.add(new Conversion<>(2, "b", Map.of("a", 1)));
        graph.add(new Conversion<>(1, "a", Map.of("b", 1)));
        Map<String, Double> result = assertTimeoutPreemptively(Duration.ofSeconds(3), graph::calculate);
        assertEquals(Set.of("a", "b"), graph.rejectedCycles());
        assertEquals(Map.of("seed", Double.MIN_VALUE), result);
    }

    @Test
    void rejectsRapidSelfGainBeforeUnderflowCanLookLikeConvergence() {
        ValueGraph<String> graph = new ValueGraph<>();
        graph.add(new Conversion<>(1, "a", Map.of(), 1));
        graph.add(new Conversion<>(Integer.MAX_VALUE, "a", Map.of("a", 1)));
        assertTrue(assertTimeoutPreemptively(Duration.ofSeconds(3), graph::calculate).isEmpty());
        assertEquals(Set.of("a"), graph.rejectedCycles());
    }

    @Test
    void recomputesDescendantsFromIndependentAlternativesAfterRejectingACycle() {
        ValueGraph<String> graph = new ValueGraph<>();
        graph.setFixed("seed", 1);
        graph.add(new Conversion<>(1, "a", Map.of("seed", 1)));
        graph.add(new Conversion<>(2, "b", Map.of("a", 1)));
        graph.add(new Conversion<>(1, "a", Map.of("b", 1)));
        graph.add(new Conversion<>(1, "child", Map.of("a", 1)));
        graph.add(new Conversion<>(1, "child", Map.of(), 7));
        graph.add(new Conversion<>(1, "grandchild", Map.of("child", 3)));
        Map<String, Double> result = graph.calculate();
        assertEquals(Map.of("seed", 1.0, "child", 7.0, "grandchild", 21.0), result);
        assertEquals(Set.of("a", "b"), graph.rejectedCycles());
    }

    @Test
    void preservesConvergentCyclesWithARealPositiveCost() {
        ValueGraph<String> graph = new ValueGraph<>();
        graph.setFixed("ingredient", 10);
        graph.add(new Conversion<>(1, "a", Map.of(), 100));
        graph.add(new Conversion<>(2, "a", Map.of("b", 1, "ingredient", 1)));
        graph.add(new Conversion<>(1, "b", Map.of("a", 1)));
        Map<String, Double> result = graph.calculate();
        assertTrue(graph.rejectedCycles().isEmpty());
        assertEquals(10, result.get("a"), TOLERANCE);
        assertEquals(10, result.get("b"), TOLERANCE);
    }

    @Test
    void handlesLongBalancedCyclesWithoutRecursiveTraversal() {
        ValueGraph<String> graph = new ValueGraph<>();
        graph.setFixed("seed", 0.1);
        graph.add(new Conversion<>(1, "node_0", Map.of("seed", 1)));
        int size = 4096;
        for (int i = size - 1; i >= 0; i--)
            graph.add(new Conversion<>(1, "node_" + i, Map.of("node_" + ((i + 1) % size), 1)));
        Map<String, Double> result = assertTimeoutPreemptively(Duration.ofSeconds(3), graph::calculate);
        assertTrue(graph.rejectedCycles().isEmpty());
        assertEquals(size + 1, result.size());
        assertEquals(0.1, result.get("node_2048"));
    }

    @Test
    void fixedValuesBreakCyclesAndFreeValuesStillPropagate() {
        ValueGraph<String> graph = new ValueGraph<>();
        graph.setFixed("locked", 1);
        graph.add(new Conversion<>(2, "b", Map.of("locked", 1)));
        graph.add(new Conversion<>(1, "locked", Map.of("b", 1)));
        graph.add(new Conversion<>(1, "free_a", Map.of(), 0));
        graph.add(new Conversion<>(1, "free_b", Map.of("free_a", 1)));
        graph.add(new Conversion<>(1, "free_a", Map.of("free_b", 1)));
        graph.add(new Conversion<>(1, "child", Map.of("free_a", 1000, "locked", 1)));
        assertEquals(Map.of("locked", 1.0, "b", 0.5, "child", 1.0), graph.calculate());
        assertTrue(graph.rejectedCycles().isEmpty());
    }

    @Test
    void preservesSubnormalValuesInBalancedCyclesWithoutTurningUnderflowIntoFreeInputs() {
        ValueGraph<String> graph = new ValueGraph<>();
        graph.setFixed("seed", Double.MIN_VALUE);
        graph.add(new Conversion<>(1, "a", Map.of("seed", 1)));
        graph.add(new Conversion<>(1, "b", Map.of("a", 1)));
        graph.add(new Conversion<>(1, "a", Map.of("b", 1)));
        graph.add(new Conversion<>(2, "underflow", Map.of("a", 1)));
        graph.add(new Conversion<>(1, "child", Map.of("underflow", 1), 1));
        assertEquals(Map.of("seed", Double.MIN_VALUE, "a", Double.MIN_VALUE, "b", Double.MIN_VALUE), graph.calculate());
        assertTrue(graph.rejectedCycles().isEmpty());
    }

    @Test
    void handlesLongAcyclicChainsWithDependenciesInsertedLast() {
        ValueGraph<String> graph = new ValueGraph<>();
        graph.setFixed("node_0", 3.5);
        int size = 8192;
        for (int i = size; i > 0; i--)
            graph.add(new Conversion<>(1, "node_" + i, Map.of("node_" + (i - 1), 1)));
        Map<String, Double> result = assertTimeoutPreemptively(Duration.ofSeconds(3), graph::calculate);
        assertEquals(size + 1, result.size());
        assertEquals(3.5, result.get("node_" + size));
        assertTrue(graph.rejectedCycles().isEmpty());
    }

    @Test
    void boundsSlowlyConvergingCyclesInsteadOfPublishingAnIntermediateValue() {
        ValueGraph<String> graph = new ValueGraph<>();
        graph.add(new Conversion<>(1, "a", Map.of(), 100));
        graph.add(new Conversion<>(1000, "a", Map.of("a", 999), 1));
        assertTrue(assertTimeoutPreemptively(Duration.ofSeconds(3), graph::calculate).isEmpty());
        assertEquals(Set.of("a"), graph.rejectedCycles());
    }

    @Test
    void supportsValuesAboveLongRangeAndSkipsNonFiniteResults() {
        ValueGraph<String> graph = new ValueGraph<>();
        graph.setFixed("large", 1.0E100);
        graph.add(new Conversion<>(2, "same", Map.of("large", 2)));
        graph.setFixed("largest", Double.MAX_VALUE);
        graph.add(new Conversion<>(1, "too_large", Map.of("largest", 2)));
        assertEquals(1.0E100, graph.calculate().get("same"));
        assertFalse(graph.calculate().containsKey("too_large"));
        for (double invalid : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> graph.setFixed("invalid", invalid));
            assertThrows(IllegalArgumentException.class, () -> new Conversion<>(1, "invalid", Map.of(), invalid));
        }
    }

    @Test
    void snapshotsAreImmutableAndFixedValuesCanBeReplaced() {
        ValueGraph<String> graph = new ValueGraph<>();
        graph.setFixed("a", 10);
        Map<String, Double> first = graph.calculate();
        graph.setFixed("a", 20);
        assertEquals(10, first.get("a"));
        assertEquals(20, graph.calculate().get("a"));
        assertThrows(UnsupportedOperationException.class, () -> first.put("b", 1.0));
    }

    @Test
    void combinesEvaluatorCandidatesWithRelationshipsAndExplicitOverrides() {
        ValueGraph<String> graph = new ValueGraph<>();
        graph.add(new Conversion<>(1, "base", Map.of(), 2.5));
        graph.add(new Conversion<>(1, "base", Map.of(), 1.25));
        graph.add(new Conversion<>(3, "output", Map.of("base", 2)));
        assertEquals(2.5 / 3, graph.calculate().get("output"), TOLERANCE);
        graph.setFixed("base", 4.5);
        graph.add(new Conversion<>(1, "child", Map.of("output", 2)));
        assertEquals(3.0, graph.calculate().get("output"));
        assertEquals(6.0, graph.calculate().get("child"));
    }

    @Test
    void ignoresRoundingDriftInBalancedCyclesAndPreservesTinyValues() {
        ValueGraph<String> graph = new ValueGraph<>();
        graph.setFixed("seed", 0.1);
        graph.add(new Conversion<>(1, "a", Map.of("seed", 1)));
        graph.add(new Conversion<>(9, "b", Map.of("a", 1)));
        graph.add(new Conversion<>(1, "a", Map.of("b", 9)));
        graph.setFixed("tiny", 1.0E-200);
        graph.add(new Conversion<>(2, "half", Map.of("tiny", 1)));
        Map<String, Double> result = assertTimeoutPreemptively(Duration.ofSeconds(3), graph::calculate);
        assertTrue(graph.rejectedCycles().isEmpty());
        assertEquals(0.1, result.get("a"), TOLERANCE);
        assertEquals(5.0E-201, result.get("half"));
    }
}
