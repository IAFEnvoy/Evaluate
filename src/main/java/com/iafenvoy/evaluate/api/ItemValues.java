package com.iafenvoy.evaluate.api;

import com.iafenvoy.evaluate.data.ValueReloadListener;
import net.minecraft.core.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.OptionalDouble;

/**
 * Server-side API. Returned values are per item, never multiplied by stack size.
 */
public final class ItemValues {
    private ItemValues() {
    }

    public static Map<ValueKey.ItemValue, Double> all(MinecraftServer server) {
        return server.getServerResources().managers().getListener(ValueReloadListener.KEY).snapshot(server).values();
    }

    /**
     * The current reload's evaluator registry. Reacquire after a datapack reload.
     */
    public static Registry<Evaluator> evaluators(MinecraftServer server) {
        return server.getServerResources().managers().getListener(ValueReloadListener.KEY).evaluators();
    }

    /**
     * The current reload's final override registry. Reacquire after a datapack reload.
     */
    public static Registry<ValueOverride> overrides(MinecraftServer server) {
        return server.getServerResources().managers().getListener(ValueReloadListener.KEY).overrides();
    }

    public static OptionalDouble get(MinecraftServer server, Item item) {
        return get(server, new ItemStack(item));
    }

    /**
     * World values take precedence. Explicit Saturn matchers use the actual stack; inferred values use its exact component patch.
     */
    public static OptionalDouble get(MinecraftServer server, ItemStack stack) {
        return server.getServerResources().managers().getListener(ValueReloadListener.KEY).snapshot(server).get(stack);
    }
}
