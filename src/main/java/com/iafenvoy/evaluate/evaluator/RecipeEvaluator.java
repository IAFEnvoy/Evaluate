package com.iafenvoy.evaluate.evaluator;

import com.iafenvoy.evaluate.Evaluate;
import com.iafenvoy.evaluate.api.EvaluationContext;
import com.iafenvoy.evaluate.api.Evaluator;
import com.iafenvoy.evaluate.api.ValueKey;
import com.iafenvoy.evaluate.value.Conversion;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicitly maps recipes whose input quantities and static outputs are known.
 */
public record RecipeEvaluator(RecipeType<?> recipeType, double extraValue) implements Evaluator {
    public static final MapCodec<RecipeEvaluator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BuiltInRegistries.RECIPE_TYPE.byNameCodec().fieldOf("recipe_type").forGetter(RecipeEvaluator::recipeType),
            MiscCodecs.NON_NEGATIVE_DOUBLE.optionalFieldOf("extra_value", 0.0).forGetter(RecipeEvaluator::extraValue)
    ).apply(i, RecipeEvaluator::new));

    @Override
    public MapCodec<RecipeEvaluator> codec() {
        return CODEC;
    }

    @Override
    public void evaluate(EvaluationContext context) {
        int mapped = 0;
        for (RecipeHolder<?> holder : context.resources().getRecipeManager().getRecipes())
            try {
                Recipe<?> recipe = holder.value();
                if (recipe.getType() != this.recipeType || recipe.isSpecial()) continue;
                List<Ingredient> ingredients = recipe.placementInfo().ingredients();
                if (ingredients.isEmpty() || ingredients.stream().anyMatch(i -> i.isCustom() || i.isEmpty())) continue;
                int variant = 0;
                for (RecipeDisplay display : recipe.display()) {
                    if (!(display.result() instanceof SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate stack1)))
                        continue;
                    int inputs = recipe instanceof TransmuteRecipe && display instanceof ShapelessCraftingRecipeDisplay shapeless
                            ? shapeless.ingredients().size() : ingredients.size();
                    if (inputs > ingredients.size()) continue;
                    String id = holder.id().identifier() + "/" + variant++;
                    Map<ValueKey, Integer> costs = new LinkedHashMap<>();
                    boolean valid = true;
                    for (int slot = 0; slot < inputs; slot++) {
                        Ingredient ingredient = ingredients.get(slot);
                        List<ItemStack> alternatives = ingredient.getValues().stream().map(ItemStack::new).filter(ingredient).toList();
                        if (alternatives.isEmpty()) {
                            valid = false;
                            break;
                        }
                        // Each alternative has its own container refund before taking the minimum.
                        ValueKey group = context.group("recipe/" + id + "/ingredient/" + slot);
                        for (ItemStack stack : alternatives) {
                            Map<ValueKey, Integer> netCost = new LinkedHashMap<>();
                            netCost.put(context.item(stack), 1);
                            ItemStackTemplate remainder = stack.getCraftingRemainder();
                            if (remainder != null) {
                                ItemStack returned = remainder.create();
                                netCost.merge(context.item(returned), -returned.getCount(), Math::addExact);
                            }
                            if (alternatives.size() == 1)
                                netCost.forEach((key, amount) -> costs.merge(key, amount, Math::addExact));
                            else context.addConversion(new Conversion<>(1, group, netCost));
                        }
                        if (alternatives.size() > 1) costs.merge(group, 1, Math::addExact);
                    }
                    if (valid) {
                        ItemStack output = stack1.create();
                        if (!output.isEmpty()) {
                            context.addConversion(new Conversion<>(output.getCount(), context.item(output), costs, this.extraValue));
                            mapped++;
                        }
                    }
                }
            } catch (RuntimeException e) {
                Evaluate.LOGGER.warn("Skipping value inference for recipe {}: {}", holder.id().identifier(), e.toString());
            }
        Evaluate.LOGGER.debug("Mapped {} {} recipe relationships with extra value {}", mapped, this.recipeType, this.extraValue);
    }
}
