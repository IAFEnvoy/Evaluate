package com.iafenvoy.evaluate.registry;

import com.iafenvoy.evaluate.Evaluate;
import com.iafenvoy.evaluate.api.Evaluator;
import com.iafenvoy.evaluate.api.ValueOverride;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

public final class EvaluateResourceKeys {
    public static final ResourceKey<Registry<MapCodec<? extends Evaluator>>> EVALUATOR_TYPE = key("evaluator_type");
    public static final ResourceKey<Registry<Evaluator>> EVALUATOR = key("evaluator");
    public static final ResourceKey<Registry<ValueOverride>> VALUE_OVERRIDE = key("value_override");

    private static <T> ResourceKey<Registry<T>> key(String path) {
        return ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(Evaluate.MOD_ID, path));
    }
}
