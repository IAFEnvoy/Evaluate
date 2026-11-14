package com.iafenvoy.evaluate.compat.kubejs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.latvian.mods.kubejs.event.KubeEvent;
import dev.latvian.mods.kubejs.generator.KubeDataGenerator;
import dev.latvian.mods.kubejs.script.KubeJSContext;
import dev.latvian.mods.kubejs.util.JsonUtils;
import dev.latvian.mods.rhino.Context;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Writes ordinary evaluator and override definitions into KubeJS's generated datapack.
 */
public final class EvaluateValuesEvent implements KubeEvent {
    public static final int DEFAULT_PRIORITY = 1000;
    private final KubeDataGenerator generator;

    public EvaluateValuesEvent(KubeDataGenerator generator) {
        this.generator = generator;
    }

    public void set(Context cx, Object item, double value) {
        this.set(cx, item, value, DEFAULT_PRIORITY);
    }

    public void set(Context cx, Object item, double value, int priority) {
        this.writeValue(cx, "evaluator", item, value, priority);
    }

    public void override(Context cx, Object item, double value) {
        this.override(cx, item, value, DEFAULT_PRIORITY);
    }

    public void override(Context cx, Object item, double value, int priority) {
        this.writeValue(cx, "value_override", item, value, priority);
    }

    private void writeValue(Context cx, String registry, Object item, double value, int priority) {
        if (!Double.isFinite(value) || value < 0)
            throw new IllegalArgumentException("Value must be finite and non-negative");
        JsonElement selection = JsonUtils.of(cx, item).deepCopy();
        JsonObject json = new JsonObject();
        if (registry.equals("evaluator")) json.addProperty("type", "evaluate:constant");
        json.add("item", selection);
        json.addProperty("value", value);
        json.addProperty("priority", priority);
        String name = UUID.nameUUIDFromBytes(selection.toString().getBytes(StandardCharsets.UTF_8)).toString();
        this.write(Identifier.fromNamespaceAndPath("kubejs", "set/" + name), registry, json);
    }

    /**
     * Register and instantiate one JS evaluator; other data files can reuse the callback id.
     */
    public void evaluator(Context cx, Identifier id, BiConsumer<ScriptEvaluationContext, Map<String, Object>> callback) {
        EvaluateKubeJSPlugin.bindings(((KubeJSContext) cx).kjsFactory.manager).registerEvaluator(id, callback);
        this.js(id, id, new JsonObject());
    }

    public void js(Identifier entryId, Identifier callbackId, JsonObject params) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "evaluate:js");
        json.addProperty("id", callbackId.toString());
        json.add("params", params.deepCopy());
        this.write(entryId, "evaluator", json);
    }

    /**
     * Add any existing evaluator type, or replace a same-id datapack definition.
     */
    public void addEvaluator(Identifier id, JsonObject definition) {
        this.write(id, "evaluator", definition.deepCopy());
    }

    private void write(Identifier id, String registry, JsonObject definition) {
        this.generator.json(id.withPath("evaluate/" + registry + "/" + id.getPath() + ".json"), definition);
    }
}
