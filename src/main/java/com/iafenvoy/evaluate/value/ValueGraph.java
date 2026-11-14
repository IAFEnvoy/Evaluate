package com.iafenvoy.evaluate.value;

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
    private final Map<K, Double> fixed = new LinkedHashMap<>();
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

    public Map<K, Double> calculate() {
        this.rejectedCycles.clear();
        Map<K, List<Conversion<K>>> byOutput = new LinkedHashMap<>();
        for (Conversion<K> conversion : this.conversions)
            if (!this.fixed.containsKey(conversion.output()))
                byOutput.computeIfAbsent(conversion.output(), k -> new ArrayList<>()).add(conversion);
        Map<K, Double> values = new LinkedHashMap<>(this.fixed);
        for (Set<K> component : this.components(byOutput)) {
            K first = component.iterator().next();
            boolean cyclic = component.size() > 1 || byOutput.get(first).stream().anyMatch(c -> c.ingredients().containsKey(first));
            if (cyclic) {
                // Publish a component only after it settles, so rejected values cannot contaminate descendants.
                if (!this.calculateCycle(component, byOutput, values)) this.rejectedCycles.addAll(component);
            } else {
                Double minimum = null;
                for (Conversion<K> conversion : byOutput.get(first)) {
                    Double candidate = this.cost(conversion, values);
                    if (candidate != null && (minimum == null || candidate < minimum)) minimum = candidate;
                }
                if (minimum != null) values.put(first, minimum);
            }
        }
        Map<K, Double> result = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (Double.isFinite(value) && value > 0) result.put(key, value);
        });
        return Map.copyOf(result);
    }

    /**
     * Iterative Kosaraju traversal, returning strongly connected components with dependencies first.
     */
    private List<Set<K>> components(Map<K, List<Conversion<K>>> byOutput) {
        Map<K, Set<K>> dependencies = new LinkedHashMap<>();
        Map<K, Set<K>> dependents = new HashMap<>();
        byOutput.forEach((output, recipes) -> {
            Set<K> inputs = new LinkedHashSet<>();
            recipes.forEach(c -> c.ingredients().keySet().forEach(input -> {
                if (byOutput.containsKey(input)) inputs.add(input);
            }));
            dependencies.put(output, inputs);
            inputs.forEach(input -> dependents.computeIfAbsent(input, k -> new LinkedHashSet<>()).add(output));
        });
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
                for (K dependent : dependents.getOrDefault(key, Set.of()))
                    if (visited.add(dependent)) pending.push(dependent);
            }
            components.add(component);
        }
        Collections.reverse(components);
        return components;
    }

    private boolean calculateCycle(Set<K> component, Map<K, List<Conversion<K>>> byOutput, Map<K, Double> values) {
        // Extended exponent range is needed only inside cycles: double underflow must never look like convergence.
        // Public values, fixed values, and acyclic calculations remain doubles.
        Map<K, BigDecimal> working = new HashMap<>();
        Map<K, Set<K>> dependents = new HashMap<>();
        for (K output : component)
            for (Conversion<K> conversion : byOutput.get(output))
                for (K input : conversion.ingredients().keySet()) {
                    if (component.contains(input))
                        dependents.computeIfAbsent(input, k -> new LinkedHashSet<>()).add(output);
                    else if (values.containsKey(input))
                        working.computeIfAbsent(input, k -> BigDecimal.valueOf(values.get(k)));
                }
        Map<K, Integer> updates = new HashMap<>();
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
            if (updates.merge(output, 1, Integer::sum) > updateLimit) return false;
            working.put(output, minimum);
            enqueue(dependents.getOrDefault(output, Set.of()), queue, queued);
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
        for (Map.Entry<K, Integer> ingredient : conversion.ingredients().entrySet()) {
            BigDecimal value = values.get(ingredient.getKey());
            if (value == null) return null;
            if (value.signum() > 0) {
                allFree = false;
                if (ingredient.getValue() > 0) positiveInput = true;
            }
            total = total.add(value.multiply(BigDecimal.valueOf(ingredient.getValue()), CYCLE_PRECISION), CYCLE_PRECISION);
        }
        if (allFree || positiveInput && total.signum() <= 0) return BigDecimal.ZERO;
        if (total.signum() <= 0) return null;
        BigDecimal result = total.divide(BigDecimal.valueOf(conversion.count()), CYCLE_PRECISION);
        return Double.isFinite(result.doubleValue()) ? result : null;
    }

    private Double cost(Conversion<K> conversion, Map<K, Double> values) {
        double total = conversion.cost();
        boolean allFree = total == 0;
        boolean positiveInput = total > 0;
        for (Map.Entry<K, Integer> ingredient : conversion.ingredients().entrySet()) {
            Double value = values.get(ingredient.getKey());
            if (value == null) return null; // Unknown is never treated as a free ingredient.
            if (value > 0) {
                allFree = false;
                if (ingredient.getValue() > 0) positiveInput = true;
            }
            total = Math.fma(value, ingredient.getValue(), total);
        }
        if (!Double.isFinite(total)) return null;
        if (allFree || positiveInput && total <= 0) return 0.0;
        double result = total / conversion.count();
        // Overflow and positive costs below the double range must not propagate as infinity or free items.
        return result > 0 && Double.isFinite(result) ? result : null;
    }

    private record Traversal<K>(K key, Iterator<K> inputs) {
    }
}
