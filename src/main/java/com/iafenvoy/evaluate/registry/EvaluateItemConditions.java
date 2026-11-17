package com.iafenvoy.evaluate.registry;

import com.iafenvoy.evaluate.Evaluate;
import com.iafenvoy.evaluate.compat.saturn.condition.HasValueCondition;
import com.iafenvoy.evaluate.compat.saturn.condition.HasWorldValueCondition;
import com.iafenvoy.evaluate.compat.saturn.condition.ItemValueCondition;
import com.iafenvoy.saturn.data.condition.ItemCondition;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EvaluateItemConditions {
    public static final DeferredRegister<MapCodec<? extends ItemCondition>> REGISTRY = DeferredRegister.create(SaturnRegistries.ITEM_CONDITION_TYPE, Evaluate.MOD_ID);

    static {
        REGISTRY.register("has_value", () -> HasValueCondition.CODEC);
        REGISTRY.register("has_world_value", () -> HasWorldValueCondition.CODEC);
        REGISTRY.register("value", () -> ItemValueCondition.CODEC);
    }
}
