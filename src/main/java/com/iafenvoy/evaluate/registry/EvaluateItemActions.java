package com.iafenvoy.evaluate.registry;

import com.iafenvoy.evaluate.Evaluate;
import com.iafenvoy.evaluate.compat.saturn.action.AddValueAction;
import com.iafenvoy.evaluate.compat.saturn.action.ResetValueAction;
import com.iafenvoy.evaluate.compat.saturn.action.SetValueAction;
import com.iafenvoy.saturn.data.action.ItemAction;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EvaluateItemActions {
    public static final DeferredRegister<MapCodec<? extends ItemAction>> REGISTRY = DeferredRegister.create(SaturnRegistries.ITEM_ACTION_TYPE, Evaluate.MOD_ID);

    static {
        REGISTRY.register("set_value", () -> SetValueAction.CODEC);
        REGISTRY.register("add_value", () -> AddValueAction.CODEC);
        REGISTRY.register("reset_value", () -> ResetValueAction.CODEC);
    }
}
