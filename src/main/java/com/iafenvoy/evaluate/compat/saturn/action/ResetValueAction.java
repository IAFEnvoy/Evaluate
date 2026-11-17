package com.iafenvoy.evaluate.compat.saturn.action;

import com.iafenvoy.evaluate.api.ItemValues;
import com.iafenvoy.evaluate.compat.saturn.ValueContexts;
import com.iafenvoy.saturn.data.action.ItemAction;
import com.iafenvoy.saturn.data.context.action.ItemActionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.NonNull;

public record ResetValueAction() implements ItemAction {
    public static final MapCodec<ResetValueAction> CODEC = MapCodec.unit(new ResetValueAction());

    @Override
    public void execute(@NonNull ItemActionContext ctx) {
        MinecraftServer server = ValueContexts.server(ctx.holder());
        if (server != null && !ctx.stack().isEmpty()) ItemValues.reset(server, ctx.stack());
    }

    @Override
    public @NonNull MapCodec<ResetValueAction> codec() {
        return CODEC;
    }
}
