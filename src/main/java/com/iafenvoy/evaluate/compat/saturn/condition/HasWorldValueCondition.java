package com.iafenvoy.evaluate.compat.saturn.condition;

import com.iafenvoy.evaluate.api.ValueKey;
import com.iafenvoy.evaluate.compat.saturn.ValueContexts;
import com.iafenvoy.evaluate.data.WorldValuesData;
import com.iafenvoy.saturn.data.condition.ItemCondition;
import com.iafenvoy.saturn.data.context.condition.ItemConditionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.NonNull;

public record HasWorldValueCondition() implements ItemCondition {
    public static final MapCodec<HasWorldValueCondition> CODEC = MapCodec.unit(new HasWorldValueCondition());

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        MinecraftServer server = ValueContexts.server(ctx.holder());
        return server != null && !ctx.stack().isEmpty()
                && WorldValuesData.get(server).values().containsKey(ValueKey.ItemValue.of(ctx.stack()));
    }

    @Override
    public @NonNull MapCodec<HasWorldValueCondition> codec() {
        return CODEC;
    }
}
