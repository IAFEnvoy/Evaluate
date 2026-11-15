package com.iafenvoy.evaluate.api;

import com.iafenvoy.evaluate.value.Conversion;
import com.iafenvoy.evaluate.value.ValueGraph;
import com.iafenvoy.saturn.util.matcher.ItemMatcher;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Per-reload inputs and contributions. Do not retain this context after evaluation.
 */
public record EvaluationContext(ReloadableServerResources resources, ValueGraph<ValueKey> graph,
                                Consumer<ItemStack> candidates, List<FixedValue> fixedValues, Identifier evaluatorId) {
    /**
     * Register a stack variant so datapack matchers also apply to evaluator inputs and outputs.
     */
    public ValueKey.ItemValue item(ItemStack stack) {
        ValueKey.ItemValue key = ValueKey.ItemValue.of(stack);
        this.candidates.accept(stack);
        return key;
    }

    /**
     * Internal keys are scoped to this evaluator's resource id so instances cannot share alternatives accidentally.
     */
    public ValueKey.GroupValue group(String name) {
        return new ValueKey.GroupValue("evaluator/" + this.evaluatorId + "/" + name);
    }

    /**
     * Register a fixed matcher value after collecting all evaluator stack variants.
     */
    public void setValue(ItemMatcher matcher, double value) {
        this.fixedValues.add(new FixedValue(matcher, value));
    }

    /**
     * Add an unlocked candidate; the lowest candidate wins, and explicit datapack values take precedence.
     */
    public void addValue(ItemStack stack, double value) {
        this.addConversion(new Conversion<>(1, this.item(stack), Map.of(), value));
    }

    /**
     * Use item(stack) to obtain item keys; groups can represent alternative inputs.
     */
    public void addConversion(Conversion<ValueKey> conversion) {
        this.graph.add(conversion);
    }
}
