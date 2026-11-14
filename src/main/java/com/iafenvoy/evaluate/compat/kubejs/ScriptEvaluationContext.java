package com.iafenvoy.evaluate.compat.kubejs;

import com.google.gson.JsonElement;
import com.iafenvoy.evaluate.api.EvaluationContext;
import com.iafenvoy.evaluate.api.ValueKey;
import com.iafenvoy.evaluate.data.ItemSelection;
import com.iafenvoy.evaluate.evaluator.ConstantEvaluator;
import com.iafenvoy.evaluate.value.Conversion;
import com.mojang.serialization.JsonOps;
import dev.latvian.mods.kubejs.util.JsonUtils;
import dev.latvian.mods.rhino.Context;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Script-friendly contributions to the same graph used by recipes and Java evaluators.
 */
public record ScriptEvaluationContext(EvaluationContext context) {

    public void set(Context cx, Object item, double value) {
        this.set(cx, item, value, EvaluateValuesEvent.DEFAULT_PRIORITY);
    }

    public void set(Context cx, Object item, double value, int priority) {
        RegistryOps<JsonElement> ops = this.context.resources().getRegistryLookup().createSerializationContext(JsonOps.INSTANCE);
        ItemSelection selection = ItemSelection.CODEC.parse(ops, JsonUtils.of(cx, item)).getOrThrow();
        new ConstantEvaluator(selection, priority, value).evaluate(this.context);
    }

    public void addValue(ItemStack stack, double value) {
        this.context.addValue(stack, value);
    }

    public void conversion(ItemStack output, List<ItemStack> ingredients) {
        this.conversion(output, ingredients, 0);
    }

    /**
     * Stack counts are ingredient/output amounts; extraValue applies once to the entire conversion.
     */
    public void conversion(ItemStack output, List<ItemStack> ingredients, double extraValue) {
        if (output.isEmpty()) throw new IllegalArgumentException("Output must not be empty");
        Map<ValueKey, Integer> inputs = new LinkedHashMap<>();
        for (ItemStack ingredient : ingredients) {
            if (ingredient.isEmpty()) throw new IllegalArgumentException("Ingredient must not be empty");
            inputs.merge(this.context.item(ingredient), ingredient.getCount(), Math::addExact);
        }
        this.context.addConversion(new Conversion<>(output.getCount(), this.context.item(output), inputs, extraValue));
    }
}
