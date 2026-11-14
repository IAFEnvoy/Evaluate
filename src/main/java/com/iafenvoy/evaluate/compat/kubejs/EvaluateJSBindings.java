package com.iafenvoy.evaluate.compat.kubejs;

import com.google.gson.JsonObject;
import com.iafenvoy.evaluate.api.EvaluationContext;
import dev.latvian.mods.kubejs.util.JsonUtils;
import dev.latvian.mods.rhino.util.HideFromJS;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Named callbacks are registered from server_scripts, then referenced by evaluate:js data entries.
 */
public final class EvaluateJSBindings {
    private final Map<Identifier, BiConsumer<EvaluationContext, JsonObject>> callbacks = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    public void registerEvaluator(Identifier id, BiConsumer<ScriptEvaluationContext, Map<String, Object>> callback) {
        Objects.requireNonNull(callback);
        this.callbacks.put(id, (context, params) -> callback.accept(new ScriptEvaluationContext(context),
                (Map<String, Object>) JsonUtils.toObject(params)));
    }

    @HideFromJS
    public Map<Identifier, BiConsumer<EvaluationContext, JsonObject>> callbacks() {
        return Map.copyOf(this.callbacks);
    }
}
