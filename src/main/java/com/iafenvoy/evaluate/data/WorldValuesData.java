package com.iafenvoy.evaluate.data;

import com.iafenvoy.evaluate.Evaluate;
import com.iafenvoy.evaluate.api.ValueKey;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMaps;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.List;

/**
 * World-owned base values. Immutable map identities also invalidate the calculation snapshot after edits.
 */
public final class WorldValuesData extends SavedData {
    private static final Codec<WorldValuesData> CODEC = Entry.CODEC.listOf().fieldOf("values").xmap(WorldValuesData::new, data -> data.values.object2DoubleEntrySet().stream().map(Entry::new).toList()).codec();
    public static final SavedDataType<WorldValuesData> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(Evaluate.MOD_ID, "values"), WorldValuesData::new, CODEC);
    private Object2DoubleMap<ValueKey.ItemValue> values;

    public WorldValuesData() {
        this(List.of());
    }

    private WorldValuesData(List<Entry> entries) {
        Object2DoubleMap<ValueKey.ItemValue> values = new Object2DoubleOpenHashMap<>();
        entries.forEach(entry -> values.put(entry.item(), entry.value()));
        this.values = Object2DoubleMaps.unmodifiable(values);
    }

    public static WorldValuesData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public Object2DoubleMap<ValueKey.ItemValue> values() {
        return this.values;
    }

    public boolean set(ItemStack stack, double value) {
        if (!Double.isFinite(value) || value < 0)
            throw new IllegalArgumentException("Value must be finite and non-negative");
        ValueKey.ItemValue key = ValueKey.ItemValue.of(stack);
        if (this.values.containsKey(key) && value == this.values.getDouble(key)) return false;
        Object2DoubleMap<ValueKey.ItemValue> values = new Object2DoubleOpenHashMap<>(this.values);
        values.put(key, value);
        this.values = Object2DoubleMaps.unmodifiable(values);
        this.setDirty();
        return true;
    }

    public boolean remove(ItemStack stack) {
        ValueKey.ItemValue key = ValueKey.ItemValue.of(stack);
        if (!this.values.containsKey(key)) return false;
        Object2DoubleMap<ValueKey.ItemValue> values = new Object2DoubleOpenHashMap<>(this.values);
        values.removeDouble(key);
        this.values = Object2DoubleMaps.unmodifiable(values);
        this.setDirty();
        return true;
    }

    private record Entry(ValueKey.ItemValue item, double value) {
        private static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                ValueKey.ItemValue.CODEC.fieldOf("item").forGetter(Entry::item),
                MiscCodecs.NON_NEGATIVE_DOUBLE.fieldOf("value").forGetter(Entry::value)
        ).apply(i, Entry::new));

        public Entry(Object2DoubleMap.Entry<ValueKey.ItemValue> entry) {
            this(entry.getKey(), entry.getDoubleValue());
        }
    }
}
