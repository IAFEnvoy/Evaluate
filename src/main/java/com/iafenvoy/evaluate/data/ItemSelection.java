package com.iafenvoy.evaluate.data;

import com.iafenvoy.saturn.util.matcher.ItemMatcher;
import com.iafenvoy.saturn.util.matcher.builtin.IngredientEntry;
import com.mojang.serialization.Codec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;

import java.util.List;
import java.util.function.Consumer;

/**
 * All item selection is decoded and matched by Saturn, including third-party entry types.
 */
public record ItemSelection(List<Entry> entries) implements ItemMatcher {
    public static final Codec<ItemSelection> CODEC = ENTRIES_CODEC.xmap(ItemSelection::new, ItemSelection::entries);

    public ItemSelection {
        entries = List.copyOf(entries);
    }

    @Override
    public int priority() {
        return DEFAULT_PRIORITY;
    }

    /**
     * Ingredient displays provide representative component variants for graph construction.
     */
    public void collectSamples(Consumer<ItemStack> consumer) {
        for (Entry entry : this.entries)
            if (entry instanceof IngredientEntry(Ingredient ingredient1))
                collectDisplay(ingredient1.display(), consumer);
    }

    private static void collectDisplay(SlotDisplay display, Consumer<ItemStack> consumer) {
        if (display instanceof SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate stack1))
            consumer.accept(stack1.create());
        else if (display instanceof SlotDisplay.Composite(List<SlotDisplay> contents))
            contents.forEach(child -> collectDisplay(child, consumer));
        else if (display instanceof SlotDisplay.WithRemainder remainder) collectDisplay(remainder.input(), consumer);
    }
}
