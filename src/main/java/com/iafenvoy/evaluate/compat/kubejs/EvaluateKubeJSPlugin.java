package com.iafenvoy.evaluate.compat.kubejs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iafenvoy.evaluate.api.EvaluationContext;
import dev.latvian.mods.kubejs.core.ScriptManagerHolderKJS;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.generator.KubeDataGenerator;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;

public final class EvaluateKubeJSPlugin implements KubeJSPlugin {
    public static final EventGroup GROUP = EventGroup.of("EvaluateEvents");
    public static final EventHandler VALUES = GROUP.server("values", () -> EvaluateValuesEvent.class);
    private static final Map<ScriptManager, EvaluateJSBindings> BINDINGS = Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    public void init() {
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent _) -> BINDINGS.clear());
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(GROUP);
    }

    @Override
    public void beforeScriptsLoaded(ScriptManager manager) {
        if (manager.scriptType != ScriptType.SERVER) return;
        // Active reload listeners retain their own callback snapshots, including across a failed reload.
        BINDINGS.clear();
        BINDINGS.put(manager, new EvaluateJSBindings());
    }

    @Override
    public void registerBindings(BindingRegistry registry) {
        if (registry.type() == ScriptType.SERVER)
            registry.add("EvaluateJS", bindings(registry.context().kjsFactory.manager));
    }

    @Override
    public void generateData(@NonNull KubeDataGenerator generator) {
        VALUES.post(new EvaluateValuesEvent(generator));
    }

    static EvaluateJSBindings bindings(ScriptManager manager) {
        return BINDINGS.computeIfAbsent(manager, ignored -> new EvaluateJSBindings());
    }

    /**
     * Capture from this resource manager, never from the previous live MinecraftServer.
     */
    public static Map<Identifier, BiConsumer<EvaluationContext, JsonElement>> callbacks(ResourceManager resources) {
        if (resources instanceof ScriptManagerHolderKJS holder && holder.kjs$getScriptManager() != null)
            return bindings(holder.kjs$getScriptManager()).callbacks();
        return Map.of();
    }
}
