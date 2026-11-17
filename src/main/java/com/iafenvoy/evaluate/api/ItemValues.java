package com.iafenvoy.evaluate.api;

import com.iafenvoy.evaluate.data.ValueReloadListener;
import com.iafenvoy.evaluate.data.WorldValuesData;
import com.iafenvoy.evaluate.network.payload.InvalidateValuesPayload;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.core.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.OptionalDouble;

/**
 * Server-side API. Returned values are per item, never multiplied by stack size.
 */
public final class ItemValues {
    private ItemValues() {
    }

    public static Object2DoubleMap<ValueKey.ItemValue> all(MinecraftServer server) {
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

    /**
     * Set a world-owned value per item for this exact component patch. Zero hides its public value.
     * Call on the server thread; changed values recalculate the graph and invalidate client caches.
     */
    public static boolean set(MinecraftServer server, ItemStack stack, double value) {
        if (!WorldValuesData.get(server).set(stack, value)) return false;
        refresh(server);
        return true;
    }

    /**
     * Remove only this stack variant's world value, restoring datapack definitions and inference.
     */
    public static boolean reset(MinecraftServer server, ItemStack stack) {
        if (!WorldValuesData.get(server).remove(stack)) return false;
        refresh(server);
        return true;
    }

    private static void refresh(MinecraftServer server) {
        all(server);
        server.getPlayerList().getPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, InvalidateValuesPayload.INSTANCE));
    }
}
