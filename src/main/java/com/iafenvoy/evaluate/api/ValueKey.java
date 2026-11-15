package com.iafenvoy.evaluate.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Graph identities include component patches, so e.g. water and healing potions stay distinct.
 */
public sealed interface ValueKey {
    record ItemValue(Item id, DataComponentPatch components) implements ValueKey {
        public static final Codec<ItemValue> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("id").forGetter(ItemValue::id),
                DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(ItemValue::components)
        ).apply(i, ItemValue::new));

        public static ItemValue of(ItemStack stack) {
            if (stack.isEmpty()) throw new IllegalArgumentException("Cannot value an empty stack");
            return new ItemValue(stack.getItem(), stack.getComponentsPatch());
        }
    }

    /**
     * Internal alternative group, never exposed as a datapack target.
     */
    record GroupValue(String id) implements ValueKey {
    }
}
