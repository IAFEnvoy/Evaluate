package com.iafenvoy.evaluate.compat.saturn.action;

import com.iafenvoy.evaluate.api.ItemValues;
import com.iafenvoy.evaluate.compat.saturn.ValueContexts;
import com.iafenvoy.saturn.data.action.ItemAction;
import com.iafenvoy.saturn.data.context.action.ItemActionContext;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.NonNull;

public record SetValueAction(NumberProvider value) implements ItemAction {
    public static final MapCodec<SetValueAction> CODEC = NumberProvider.CODEC.fieldOf("value").xmap(SetValueAction::new, SetValueAction::value);

    @Override
    public void execute(@NonNull ItemActionContext ctx) {
        MinecraftServer server = ValueContexts.server(ctx.holder());
        if (server == null || ctx.stack().isEmpty()) return;
        double value = this.value.evaluate(ctx.formula());
        if (Double.isFinite(value) && value >= 0) ItemValues.set(server, ctx.stack(), value);
    }

    @Override
    public @NonNull MapCodec<SetValueAction> codec() {
        return CODEC;
    }
}
