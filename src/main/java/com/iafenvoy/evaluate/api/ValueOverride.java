package com.iafenvoy.evaluate.api;

import com.iafenvoy.evaluate.data.DefinitionCodecs;
import com.iafenvoy.evaluate.data.ItemSelection;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.iafenvoy.saturn.util.matcher.ItemMatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * A separate datapack registry entry that only replaces final query results.
 */
public record ValueOverride(ItemSelection item, int priority, double value) implements ItemMatcher {
    public static final MapCodec<ValueOverride> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemSelection.CODEC.fieldOf("item").forGetter(ValueOverride::item),
            DefinitionCodecs.SIGNED_INTEGER.optionalFieldOf("priority", DEFAULT_PRIORITY).forGetter(ValueOverride::priority),
            MiscCodecs.NON_NEGATIVE_DOUBLE.fieldOf("value").forGetter(ValueOverride::value)
    ).apply(i, ValueOverride::new));
    public static final Codec<ValueOverride> CODEC = DefinitionCodecs.strict(MAP_CODEC);

    @Override
    public List<Entry> entries() {
        return this.item.entries();
    }
}
