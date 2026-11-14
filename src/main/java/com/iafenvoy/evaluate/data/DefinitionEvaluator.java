package com.iafenvoy.evaluate.data;

import com.iafenvoy.evaluate.Evaluate;
import com.iafenvoy.evaluate.api.*;
import com.iafenvoy.evaluate.value.ValueGraph;
import com.iafenvoy.saturn.util.matcher.ItemMatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class DefinitionEvaluator {
    private DefinitionEvaluator() {
    }

    static ValueSnapshot evaluate(ReloadableServerResources resources, Map<Identifier, Evaluator> evaluators, List<ValueOverride> overrides) {
        ValueGraph<ValueKey> graph = new ValueGraph<>();
        Map<ValueKey.Item, ItemStack> candidates = new LinkedHashMap<>();
        Consumer<ItemStack> collect = stack -> {
            if (!stack.isEmpty()) candidates.putIfAbsent(ValueKey.Item.of(stack), stack.copyWithCount(1));
        };
        BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR).forEach(item -> collect.accept(new ItemStack(item)));
        List<FixedValue> fixedValues = new ArrayList<>();
        evaluators.forEach((id, evaluator) -> evaluator.evaluate(new EvaluationContext(resources, graph, collect, fixedValues, id)));
        overrides.forEach(override -> override.item().collectSamples(collect));

        candidates.forEach((key, stack) -> ItemMatcher.find(fixedValues.stream(), stack).ifPresent(d -> graph.setFixed(key, d.value())));
        Map<ValueKey.Item, Double> result = new LinkedHashMap<>();
        graph.calculate().forEach((key, value) -> {
            if (key instanceof ValueKey.Item item) result.put(item, value);
        });
        candidates.forEach((key, stack) -> ItemMatcher.find(overrides.stream(), stack).ifPresent(override -> {
            if (override.value() > 0) result.put(key, override.value());
            else result.remove(key);
        }));
        if (!graph.rejectedCycles().isEmpty())
            Evaluate.LOGGER.warn("Rejected {} nodes in cyclic value groups that exceeded calculation limits", graph.rejectedCycles().size());
        Evaluate.LOGGER.info("Evaluated {} item values from {} datapack evaluators and {} final overrides", result.size(), evaluators.size(), overrides.size());
        return new ValueSnapshot(result, fixedValues, overrides);
    }
}
