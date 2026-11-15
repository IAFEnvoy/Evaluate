package com.iafenvoy.evaluate_tests;

import com.iafenvoy.evaluate.api.ItemValues;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Exercise cycle isolation through real recipe mapping, including an independent downstream recipe. */
final class CycleRecipeChecks {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("evaluate_tests");
    private static final DeferredItem<Item> A = ITEMS.registerSimpleItem("cycle_a");
    private static final DeferredItem<Item> B = ITEMS.registerSimpleItem("cycle_b");
    private static final DeferredItem<Item> CHILD = ITEMS.registerSimpleItem("cycle_child");

    static void register(IEventBus bus) { ITEMS.register(bus); }

    static void check(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(ItemValues.get(server, A.get()).isEmpty(), "Gain cycle A must be rejected even in the full recipe graph");
        helper.assertTrue(ItemValues.get(server, B.get()).isEmpty(), "Gain cycle B must be rejected even in the full recipe graph");
        helper.assertTrue(ItemValues.get(server, CHILD.get()).orElse(-1) == 512,
                "Downstream item must use its independent two-iron recipe after rejecting the gain cycle");
    }
}
