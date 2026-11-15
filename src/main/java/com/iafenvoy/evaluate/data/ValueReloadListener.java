package com.iafenvoy.evaluate.data;

import com.google.gson.JsonElement;
import com.iafenvoy.evaluate.Evaluate;
import com.iafenvoy.evaluate.api.EvaluationContext;
import com.iafenvoy.evaluate.api.Evaluator;
import com.iafenvoy.evaluate.api.ValueKey;
import com.iafenvoy.evaluate.api.ValueOverride;
import com.iafenvoy.evaluate.compat.kubejs.EvaluateKubeJSPlugin;
import com.iafenvoy.evaluate.registry.EvaluateResourceKeys;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMaps;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.resource.ListenerKey;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

/**
 * Native datapack registries provide initial lookup and validation. Reload-scoped frozen registries
 * keep evaluator and override file edits live on /reload without mutating the worldgen registry layer.
 * Matching and graph evaluation wait until tags and default item components have bound.
 */
public final class ValueReloadListener extends SimplePreparableReloadListener<ValueReloadListener.Definitions> {
    public static final ListenerKey<ValueReloadListener> KEY = ListenerKey.create(Identifier.fromNamespaceAndPath(Evaluate.MOD_ID, "values"));
    private final ReloadableServerResources resources;
    private Definitions definitions;
    private boolean pending;
    private ValueSnapshot snapshot = ValueSnapshot.EMPTY;
    private Object2DoubleMap<ValueKey.ItemValue> worldValues = Object2DoubleMaps.emptyMap();

    public ValueReloadListener(ReloadableServerResources resources) {
        this.resources = resources;
    }

    public Object2DoubleMap<ValueKey.ItemValue> values() {
        return this.snapshot().values();
    }

    public Registry<Evaluator> evaluators() {
        return this.definitions.evaluators();
    }

    public Registry<ValueOverride> overrides() {
        return this.definitions.overrides();
    }

    public Map<Identifier, BiConsumer<EvaluationContext, JsonElement>> jsCallbacks() {
        return this.definitions.jsCallbacks();
    }

    public synchronized ValueSnapshot snapshot(MinecraftServer server) {
        Object2DoubleMap<ValueKey.ItemValue> current = WorldValuesData.get(server).values();
        if (this.worldValues != current) {
            this.worldValues = current;
            this.pending = true;
        }
        return this.snapshot();
    }

    public synchronized ValueSnapshot snapshot() {
        if (this.pending) {
            Map<Identifier, Evaluator> evaluators = new LinkedHashMap<>();
            this.definitions.evaluators().listElements().forEach(holder -> evaluators.put(holder.key().identifier(), holder.value()));
            List<ValueOverride> overrides = this.definitions.overrides().listElements().map(Holder.Reference::value).toList();
            this.snapshot = DefinitionEvaluator.evaluate(this.resources, evaluators, overrides, this.worldValues);
            this.pending = false;
        }
        return this.snapshot;
    }

    /**
     * Invalidate derived values when an external calculation source changes without a datapack reload.
     */
    public synchronized void invalidate() {
        this.pending = true;
    }

    @Override
    protected Definitions prepare(@NonNull ResourceManager manager, @NonNull ProfilerFiller profiler) {
        return new Definitions(this.load(manager, EvaluateResourceKeys.EVALUATOR, Evaluator.CODEC),
                this.load(manager, EvaluateResourceKeys.VALUE_OVERRIDE, ValueOverride.CODEC),
                ModList.get().isLoaded("kubejs") ? EvaluateKubeJSPlugin.callbacks(manager) : Map.of());
    }

    private <T> Registry<T> load(ResourceManager manager, ResourceKey<Registry<T>> key, Codec<T> codec) {
        Map<Identifier, T> values = new TreeMap<>();
        SimpleJsonResourceReloadListener.scanDirectory(manager, key, this.getRegistryLookup().createSerializationContext(JsonOps.INSTANCE), codec, values);
        MappedRegistry<T> registry = new MappedRegistry<>(key, Lifecycle.stable());
        values.forEach((id, value) -> registry.register(ResourceKey.create(key, id), value, RegistrationInfo.BUILT_IN));
        return registry.freeze();
    }

    @Override
    protected synchronized void apply(Definitions definitions, @NonNull ResourceManager manager, @NonNull ProfilerFiller profiler) {
        this.definitions = definitions;
        this.pending = true;
    }

    public record Definitions(Registry<Evaluator> evaluators, Registry<ValueOverride> overrides,
                              Map<Identifier, BiConsumer<EvaluationContext, JsonElement>> jsCallbacks) {
    }
}
