package com.iafenvoy.evaluate.registry;

import com.iafenvoy.evaluate.Evaluate;
import com.iafenvoy.evaluate.compat.saturn.condition.InventoryValueCondition;
import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EvaluateEntityConditions {
    public static final DeferredRegister<MapCodec<? extends EntityCondition>> REGISTRY = DeferredRegister.create(SaturnRegistries.ENTITY_CONDITION_TYPE, Evaluate.MOD_ID);

    static {
        REGISTRY.register("inventory_value", () -> InventoryValueCondition.CODEC);
    }
}
