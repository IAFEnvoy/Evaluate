package com.iafenvoy.evaluate.compat.saturn.condition;

import com.iafenvoy.evaluate.api.ItemValues;
import com.iafenvoy.evaluate.compat.saturn.ValueContexts;
import com.iafenvoy.saturn.data.condition.ItemCondition;
import com.iafenvoy.saturn.data.context.condition.ItemConditionContext;
import com.iafenvoy.saturn.util.math.Comparison;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.NonNull;

import java.util.OptionalDouble;

public record ItemValueCondition(Comparison comparison, boolean stackValue) implements ItemCondition {
    public static final MapCodec<ItemValueCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Comparison.CODEC.forGetter(ItemValueCondition::comparison),
            Codec.BOOL.optionalFieldOf("stack_value", false).forGetter(ItemValueCondition::stackValue)
    ).apply(i, ItemValueCondition::new));

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        MinecraftServer server = ValueContexts.server(ctx.holder());
        if (server == null) return false;
        OptionalDouble value = ItemValues.get(server, ctx.stack());
        if (value.isEmpty()) return false;
        double amount = value.getAsDouble() * (this.stackValue ? ctx.stack().getCount() : 1);
        return Double.isFinite(amount) && this.comparison.compare(amount);
    }

    @Override
    public @NonNull MapCodec<ItemValueCondition> codec() {
        return CODEC;
    }
}
