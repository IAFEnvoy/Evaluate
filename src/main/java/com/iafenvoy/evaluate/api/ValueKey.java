package com.iafenvoy.evaluate.api;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Graph identities include component patches, so e.g. water and healing potions stay distinct.
 */
public sealed interface ValueKey {
    record Item(Identifier id, DataComponentPatch components) implements ValueKey {
        public Item(Identifier id) {
            this(id, DataComponentPatch.EMPTY);
        }

        public static Item of(ItemStack stack) {
            if (stack.isEmpty()) throw new IllegalArgumentException("Cannot value an empty stack");
            return new Item(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getComponentsPatch());
        }
    }

    /**
     * Internal alternative group, never exposed as a datapack target.
     */
    record Group(String id) implements ValueKey {
    }
}
