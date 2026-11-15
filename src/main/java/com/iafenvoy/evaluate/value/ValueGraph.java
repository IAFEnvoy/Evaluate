package com.iafenvoy.evaluate.value;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import it.unimi.dsi.fastutil.objects.*;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;

/**
 * Minecraft-independent minimum-cost propagation.
 */
public final class ValueGraph<K> {
    private static final MathContext CYCLE_PRECISION = MathContext.DECIMAL128;
    private static final BigDecimal CYCLE_TOLERANCE = BigDecimal.valueOf(8 * Math.ulp(1.0));
    private static final int MAX_CYCLE_UPDATES = 256;
    private static final int MAX_CYCLE_WORK = 1_000_000;
    private final Object2DoubleMap<K> fixed = new Object2DoubleLinkedOpenHashMap<>();
    private final List<Conversion<K>> conversions = new ArrayList<>();
    private final Set<K> rejectedCycles = new LinkedHashSet<>();

    public void setFixed(K key, double value) {
        checkValue(value);
        this.fixed.put(key, value);
    }

    private static void checkValue(double value) {
        if (!Double.isFinite(value) || value < 0)
            throw new IllegalArgumentException("Value must be finite and non-negative");
    }

    public void add(Conversion<K> conversion) {
        this.conversions.add(conversion);
    }

    public Set<K> rejectedCycles() {
        return Set.copyOf(this.rejectedCycles);
    }

    public Object2DoubleMap<K> calculate() {
        this.rejectedCycles.clear();
        ListMultimap<K, Conversion<K>> byOutput = MultimapBuilder.linkedHashKeys().arrayListValues().build();
        for (Conversion<K> conversion : this.conversions)
            if (!this.fixed.containsKey(conversion.output()))
                byOutput.put(conversion.output(), conversion);
        Object2DoubleMap<K> values = new Object2DoubleLinkedOpenHashMap<>(this.fixed);
        for (Set<K> component : this.components(byOutput)) {
            K first = component.iterator().next();
            boolean cyclic = component.size() > 1 || byOutput.get(first).stream().anyMatch(c -> c.ingredients().containsKey(first));
            if (cyclic) {
                // Publish a component only after it settles, so rejected values cannot contaminate descendants.
                if (!this.calculateCycle(component, byOutput, values)) this.rejectedCycles.addAll(component);
            } else {
                double minimum = Double.POSITIVE_INFINITY;
                for (Conversion<K> conversion : byOutput.get(first)) {
                    double candidate = this.cost(conversion, values);
                    if (candidate < minimum) minimum = candidate;
                }
                if (Double.isFinite(minimum)) values.put(first, minimum);
            }
        }
        Object2DoubleMap<K> result = new Object2DoubleLinkedOpenHashMap<>();
        for (Object2DoubleMap.Entry<K> entry : values.object2DoubleEntrySet()) {
            double value = entry.getDoubleValue();
            if (Double.isFinite(value) && value > 0) result.put(entry.getKey(), value);
        }
        return Object2DoubleMaps.unmodifiable(result);
    }

    /**
     * Iterative Kosaraju traversal, returning strongly connected components with dependencies first.
     */
    private List<Set<K>> components(ListMultimap<K, Conversion<K>> byOutput) {
        SetMultimap<K, K> dependencies = LinkedHashMultimap.create();
        SetMultimap<K, K> dependents = LinkedHashMultimap.create();
        byOutput.forEach((output, recipe) -> recipe.ingredients().keySet().forEach(input -> {
            if (byOutput.containsKey(input)) {
                dependencies.put(output, input);
                dependents.put(input, output);
            }
        }));
        List<K> finished = new ArrayList<>();
        Set<K> visited = new HashSet<>();
        ArrayDeque<Traversal<K>> traversal = new ArrayDeque<>();
        for (K root : byOutput.keySet()) {
            if (!visited.add(root)) continue;
            traversal.push(new Traversal<>(root, dependencies.get(root).iterator()));
            while (!traversal.isEmpty()) {
                Traversal<K> frame = traversal.peek();
                if (frame.inputs().hasNext()) {
                    K input = frame.inputs().next();
                    if (visited.add(input)) traversal.push(new Traversal<>(input, dependencies.get(input).iterator()));
                } else finished.add(traversal.pop().key());
            }
        }
        visited.clear();
        List<Set<K>> components = new ArrayList<>();
        ArrayDeque<K> pending = new ArrayDeque<>();
        for (int i = finished.size() - 1; i >= 0; i--) {
            K root = finished.get(i);
            if (!visited.add(root)) continue;
            Set<K> component = new LinkedHashSet<>();
            pending.push(root);
            while (!pending.isEmpty()) {
                K key = pending.pop();
                component.add(key);
                for (K dependent : dependents.get(key))
                    if (visited.add(dependent)) pending.push(dependent);
            }
            components.add(component);
        }
        Collections.reverse(components);
        return components;
    }

    private boolean calculateCycle(Set<K> component, ListMultimap<K, Conversion<K>> byOutput, Object2DoubleMap<K> values) {
        // Extended exponent range is needed only inside cycles: double underflow must never look like convergence.
        // Public values, fixed values, and acyclic calculations remain doubles.
        Map<K, BigDecimal> working = new HashMap<>();
        SetMultimap<K, K> dependents = LinkedHashMultimap.create();
        for (K output : component)
            for (Conversion<K> conversion : byOutput.get(output))
                for (K input : conversion.ingredients().keySet()) {
                    if (component.contains(input))
                        dependents.put(input, output);
                    else if (values.containsKey(input))
                        working.computeIfAbsent(input, k -> BigDecimal.valueOf(values.getDouble(k)));
                }
        Object2IntOpenHashMap<K> updates = new Object2IntOpenHashMap<>();
        ArrayDeque<K> queue = new ArrayDeque<>(component);
        Set<K> queued = new HashSet<>(component);
        int updateLimit = (int) Math.max(64, Math.min(MAX_CYCLE_UPDATES, component.size() * 2L));
        long work = 0;
        while (!queue.isEmpty()) {
            K output = queue.removeFirst();
            queued.remove(output);
            BigDecimal minimum = null;
            for (Conversion<K> conversion : byOutput.get(output)) {
                work += 1L + conversion.ingredients().size();
                if (work > MAX_CYCLE_WORK) return false;
                BigDecimal candidate = this.cycleCost(conversion, working);
                if (candidate != null && (minimum == null || candidate.compareTo(minimum) < 0)) minimum = candidate;
            }
            BigDecimal old = working.get(output);
            if (minimum == null || old != null && !improves(minimum, old)) continue;
            if (updates.addTo(output, 1) >= updateLimit) return false;
            working.put(output, minimum);
            enqueue(dependents.get(output), queue, queued);
        }
        for (K output : component) {
            BigDecimal value = working.get(output);
            if (value == null) continue;
            double result = value.doubleValue();
            if (Double.isFinite(result) && (result > 0 || value.signum() == 0)) values.put(output, result);
        }
        return true;
    }

    private static <K> void enqueue(Set<K> keys, ArrayDeque<K> queue, Set<K> queued) {
        keys.forEach(key -> {
            if (queued.add(key)) queue.addLast(key);
        });
    }

    private static boolean improves(BigDecimal candidate, BigDecimal previous) {
        // Relative tolerance remains meaningful below the double range, unlike an absolute subnormal ULP.
        return candidate.compareTo(previous) < 0 && (candidate.signum() == 0
                || previous.subtract(candidate).compareTo(previous.multiply(CYCLE_TOLERANCE, CYCLE_PRECISION)) > 0);
    }

    private BigDecimal cycleCost(Conversion<K> conversion, Map<K, BigDecimal> values) {
        BigDecimal total = BigDecimal.valueOf(conversion.cost());
        boolean allFree = total.signum() == 0;
        boolean positiveInput = total.signum() > 0;
        for (Object2IntMap.Entry<K> ingredient : conversion.ingredients().object2IntEntrySet()) {
            BigDecimal value = values.get(ingredient.getKey());
            if (value == null) return null;
            if (value.signum() > 0) {
                allFree = false;
                if (ingredient.getIntValue() > 0) positiveInput = true;
            }
            total = total.add(value.multiply(BigDecimal.valueOf(ingredient.getIntValue()), CYCLE_PRECISION), CYCLE_PRECISION);
        }
        if (allFree || positiveInput && total.signum() <= 0) return BigDecimal.ZERO;
        if (total.signum() <= 0) return null;
        BigDecimal result = total.divide(BigDecimal.valueOf(conversion.count()), CYCLE_PRECISION);
        return Double.isFinite(result.doubleValue()) ? result : null;
    }

    private double cost(Conversion<K> conversion, Object2DoubleMap<K> values) {
        double total = conversion.cost();
        boolean allFree = total == 0;
        boolean positiveInput = total > 0;
        for (Object2IntMap.Entry<K> ingredient : conversion.ingredients().object2IntEntrySet()) {
            double value = values.getOrDefault(ingredient.getKey(), Double.NaN);
            if (Double.isNaN(value)) return Double.NaN; // Unknown is never treated as a free ingredient.
            if (value > 0) {
                allFree = false;
                if (ingredient.getIntValue() > 0) positiveInput = true;
            }
            total = Math.fma(value, ingredient.getIntValue(), total);
        }
        if (!Double.isFinite(total)) return Double.NaN;
        if (allFree || positiveInput && total <= 0) return 0.0;
        double result = total / conversion.count();
        // Overflow and positive costs below the double range must not propagate as infinity or free items.
        return result > 0 && Double.isFinite(result) ? result : Double.NaN;
    }

    private record Traversal<K>(K key, Iterator<K> inputs) {
    }
}
