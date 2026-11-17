package com.iafenvoy.evaluate.compat.saturn.condition;

import com.iafenvoy.evaluate.api.ItemValues;
import com.iafenvoy.evaluate.compat.saturn.ValueContexts;
import com.iafenvoy.saturn.data.condition.ItemCondition;
import com.iafenvoy.saturn.data.context.condition.ItemConditionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.NonNull;

public record HasValueCondition() implements ItemCondition {
    public static final MapCodec<HasValueCondition> CODEC = MapCodec.unit(new HasValueCondition());

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        MinecraftServer server = ValueContexts.server(ctx.holder());
        return server != null && ItemValues.get(server, ctx.stack()).isPresent();
    }

    @Override
    public @NonNull MapCodec<HasValueCondition> codec() {
        return CODEC;
    }
}
