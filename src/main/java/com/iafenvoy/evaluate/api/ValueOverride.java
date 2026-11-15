package com.iafenvoy.evaluate.api;

import com.iafenvoy.evaluate.data.ItemSelection;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.iafenvoy.saturn.util.matcher.ItemMatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * A separate datapack registry entry that only replaces final query results.
 */
public record ValueOverride(ItemSelection item, int priority, double value) implements ItemMatcher {
    public static final Codec<ValueOverride> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemSelection.CODEC.fieldOf("item").forGetter(ValueOverride::item),
            Codec.INT.optionalFieldOf("priority", DEFAULT_PRIORITY).forGetter(ValueOverride::priority),
            MiscCodecs.NON_NEGATIVE_DOUBLE.fieldOf("value").forGetter(ValueOverride::value)
    ).apply(i, ValueOverride::new));

    @Override
    public List<Entry> entries() {
        return this.item.entries();
    }
}
