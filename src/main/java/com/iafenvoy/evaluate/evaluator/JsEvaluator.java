package com.iafenvoy.evaluate.evaluator;

import com.google.gson.JsonObject;
import com.iafenvoy.evaluate.Evaluate;
import com.iafenvoy.evaluate.api.EvaluationContext;
import com.iafenvoy.evaluate.api.Evaluator;
import com.iafenvoy.evaluate.data.DefinitionCodecs;
import com.iafenvoy.evaluate.data.ValueReloadListener;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.function.BiConsumer;

/**
 * Data files reference a named callback; callbacks belong to the resource reload that loaded them.
 */
public record JsEvaluator(Identifier callbackId, JsonObject params) implements Evaluator {
    public static final MapCodec<JsEvaluator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("id").forGetter(JsEvaluator::callbackId),
            DefinitionCodecs.JSON_OBJECT.optionalFieldOf("params", new JsonObject()).forGetter(JsEvaluator::params)
    ).apply(i, JsEvaluator::new));

    public JsEvaluator {
        params = params.deepCopy();
    }

    @Override
    public void evaluate(EvaluationContext context) {
        BiConsumer<EvaluationContext, JsonObject> callback = context.resources().getListener(ValueReloadListener.KEY).jsCallbacks().get(this.callbackId);
        if (callback == null) {
            Evaluate.LOGGER.warn("Unknown JS evaluator callback '{}' (KubeJS server script registration required)", this.callbackId);
            return;
        }
        try {
            callback.accept(context, this.params.deepCopy());
        } catch (Exception error) {
            Evaluate.LOGGER.error("Error in JS evaluator callback '{}'", this.callbackId, error);
        }
    }

    @Override
    public MapCodec<JsEvaluator> codec() {
        return CODEC;
    }
}
