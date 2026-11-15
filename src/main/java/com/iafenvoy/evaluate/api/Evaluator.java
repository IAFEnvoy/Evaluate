package com.iafenvoy.evaluate.api;

import com.iafenvoy.evaluate.registry.EvaluateRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.function.Function;

/**
 * Contributes candidate values and relationships to the shared graph on startup and reload.
 */
public interface Evaluator {
    Codec<Evaluator> CODEC = EvaluateRegistries.EVALUATOR_TYPE.byNameCodec().dispatch("type", Evaluator::codec, Function.identity());

    void evaluate(EvaluationContext context);

    MapCodec<? extends Evaluator> codec();
}
