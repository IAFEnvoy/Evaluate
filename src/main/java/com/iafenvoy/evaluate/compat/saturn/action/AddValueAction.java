package com.iafenvoy.evaluate.compat.saturn.action;

import com.iafenvoy.evaluate.api.ItemValues;
import com.iafenvoy.evaluate.compat.saturn.ValueContexts;
import com.iafenvoy.saturn.data.action.ItemAction;
import com.iafenvoy.saturn.data.context.action.ItemActionContext;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.NonNull;

/**
 * Apply a signed amount to the effective per-item value, then store it as a world override.
 */
public record AddValueAction(NumberProvider amount) implements ItemAction {
    public static final MapCodec<AddValueAction> CODEC = NumberProvider.CODEC.fieldOf("amount").xmap(AddValueAction::new, AddValueAction::amount);

    @Override
    public void execute(@NonNull ItemActionContext ctx) {
        MinecraftServer server = ValueContexts.server(ctx.holder());
        if (server == null || ctx.stack().isEmpty()) return;
        double amount = this.amount.evaluate(ctx.formula());
        if (!Double.isFinite(amount) || amount == 0) return;
        double value = ItemValues.get(server, ctx.stack()).orElse(0) + amount;
        if (Double.isFinite(value) && value >= 0) ItemValues.set(server, ctx.stack(), value);
    }

    @Override
    public @NonNull MapCodec<AddValueAction> codec() {
        return CODEC;
    }
}
