package com.iafenvoy.evaluate_tests;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.iafenvoy.evaluate.api.ItemValues;
import com.iafenvoy.evaluate.api.ValueKey;
import com.iafenvoy.evaluate.compat.saturn.action.SetValueAction;
import com.iafenvoy.saturn.data.action.ItemAction;
import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.condition.ItemCondition;
import com.iafenvoy.saturn.data.context.action.ItemActionContext;
import com.iafenvoy.saturn.data.context.condition.ItemConditionContext;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.iafenvoy.saturn.util.formula.number.Constant;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.Map;

/** Uses Saturn's real dispatch codecs and nested contexts against a running server. */
final class SaturnChecks {
    private SaturnChecks() {
    }

    static void checkInitial(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Player holder = helper.makeMockPlayer(GameType.SURVIVAL);
        FormulaContext formula = FormulaContext.of(holder, Map.of("delta", 1.25));
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 3);
        ItemActionContext ctx = new ItemActionContext(holder, iron, formula);
        ItemAction reset = action(helper, "{\"type\":\"evaluate:reset_value\"}");
        ItemCondition hasValue = condition(helper, "{\"type\":\"evaluate:has_value\"}");
        ItemCondition hasWorldValue = condition(helper, "{\"type\":\"evaluate:has_world_value\"}");
        helper.assertTrue(hasValue.test(holder, iron, formula) && !hasWorldValue.test(holder, iron, formula),
                "Datapack values must exist without a world override");

        ItemAction set = action(helper, "{\"type\":\"evaluate:set_value\",\"value\":10.5}");
        set.execute(ctx);
        helper.assertTrue(iron.getCount() == 3 && ItemValues.get(server, iron).orElseThrow() == 10.5,
                "Item actions must change the per-item value without changing stack count");
        helper.assertTrue(ItemValues.get(server, Items.IRON_BLOCK).orElseThrow() == 94.5,
                "Item actions must immediately recalculate recipe descendants");
        Object2DoubleMap<ValueKey.ItemValue> snapshot = ItemValues.all(server);
        set.execute(ctx);
        helper.assertTrue(snapshot == ItemValues.all(server), "Setting an unchanged value must reuse the snapshot");
        checkComparisons(helper, holder, iron, formula);

        action(helper, "{\"type\":\"evaluate:add_value\",\"amount\":\"delta\"}").execute(ctx);
        helper.assertTrue(ItemValues.get(server, iron).orElseThrow() == 11.75, "Action amounts must use Saturn number providers");
        action(helper, """
                {"type":"saturn:if_else","condition":[
                  {"type":"evaluate:has_value"},
                  {"type":"evaluate:value","comparison":">=","compare_to":11}
                ],"if_action":{"type":"evaluate:add_value","amount":-1.25}}
                """).execute(ctx);
        helper.assertTrue(ItemValues.get(server, iron).orElseThrow() == 10.5,
                "Saturn composite actions and conditions must dispatch Evaluate types");

        snapshot = ItemValues.all(server);
        action(helper, "{\"type\":\"evaluate:add_value\",\"amount\":-100}").execute(ctx);
        action(helper, "{\"type\":\"evaluate:set_value\",\"value\":-1}").execute(ctx);
        new SetValueAction(new NumberProvider() {
            @Override
            public double evaluate(FormulaContext context) { return Double.NaN; }

            @Override
            public MapCodec<? extends NumberProvider> codec() { return MapCodec.unit(this); }
        }).execute(ctx);
        set.execute(new ItemActionContext(null, iron, formula));
        set.execute(new ItemActionContext(holder, ItemStack.EMPTY, formula));
        helper.assertTrue(snapshot == ItemValues.all(server), "Invalid action inputs must leave world values and snapshots unchanged");

        action(helper, "{\"type\":\"evaluate:set_value\",\"value\":0}").execute(ctx);
        helper.assertTrue(hasWorldValue.test(holder, iron, formula) && !hasValue.test(holder, iron, formula),
                "A zero world override must be detectable without becoming a positive public value");
        helper.assertTrue(!condition(helper, "{\"type\":\"evaluate:value\",\"comparison\":\"==\",\"compare_to\":0}")
                .test(holder, iron, formula), "Zero values must follow the public query semantics");
        reset.execute(ctx);
        helper.assertTrue(ItemValues.get(server, iron).orElseThrow() == 256
                        && ItemValues.get(server, Items.IRON_BLOCK).orElseThrow() == 2304,
                "Reset actions must restore base values and recipe descendants");

        ItemStack unknown = new ItemStack(Items.WATER_BUCKET);
        helper.assertTrue(!hasValue.test(holder, unknown, formula), "Unknown items must fail has_value");
        helper.assertTrue(!condition(helper, "{\"type\":\"evaluate:value\",\"comparison\":\"!=\",\"compare_to\":0}")
                .test(holder, unknown, formula), "Unknown items must fail comparisons, including !=");
        helper.assertTrue(!hasValue.test(new ItemConditionContext(null, iron, formula)), "Conditions without a server holder must return false");
        action(helper, "{\"type\":\"evaluate:add_value\",\"amount\":2.5}").execute(holder, unknown, formula);
        helper.assertTrue(ItemValues.get(server, unknown).orElseThrow() == 2.5, "Adding to an unknown item must start from zero");
        reset.execute(holder, unknown, formula);

        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("VALUED"));
        named.set(DataComponents.DAMAGE, 7);
        set.execute(holder, named, formula);
        helper.assertTrue(ItemValues.get(server, named).orElseThrow() == 10.5
                        && ItemValues.get(server, new ItemStack(Items.DIAMOND_SWORD)).orElseThrow() != 10.5,
                "Actions must preserve exact component keys");
        reset.execute(holder, named, formula);
        helper.assertTrue(ItemValues.get(server, named).orElseThrow() == 909, "Reset must restore component-sensitive matchers");

        ItemStack large = new ItemStack(Items.BARRIER, 2);
        new SetValueAction(new Constant(Double.MAX_VALUE)).execute(holder, large, formula);
        helper.assertTrue(!condition(helper, "{\"type\":\"evaluate:value\",\"stack_value\":true,\"comparison\":\">\",\"compare_to\":0}")
                .test(holder, large, formula), "Overflowing stack totals must not satisfy comparisons");
        snapshot = ItemValues.all(server);
        action(helper, "{\"type\":\"evaluate:add_value\",\"amount\":1e308}").execute(holder, large, formula);
        helper.assertTrue(snapshot == ItemValues.all(server), "An overflowing addition must not modify the override");
        reset.execute(holder, large, formula);
        checkInventory(helper, holder, formula);

        action(helper, "{\"type\":\"evaluate:set_value\",\"value\":47.125}").execute(holder, savedStack(), formula);
    }

    private static void checkComparisons(GameTestHelper helper, Player holder, ItemStack stack, FormulaContext formula) {
        for (Map.Entry<String, Double> comparison : Map.of("==", 10.5, "!=", 10.0, "<", 11.0, "<=", 10.5, ">", 10.0, ">=", 10.5).entrySet()) {
            String json = "{\"type\":\"evaluate:value\",\"comparison\":\"" + comparison.getKey() + "\",\"compare_to\":" + comparison.getValue() + "}";
            helper.assertTrue(condition(helper, json).test(holder, stack, formula), "Comparison must work: " + json);
        }
        helper.assertTrue(condition(helper, "{\"type\":\"evaluate:value\",\"stack_value\":true,\"comparison\":\"==\",\"compare_to\":31.5}")
                .test(holder, stack, formula), "Stack value must multiply the count");
        helper.assertTrue(!condition(helper, "{\"type\":\"evaluate:value\",\"comparison\":\">\",\"compare_to\":10.5}")
                .test(holder, stack, formula), "A strict comparison must fail at equality");
    }

    private static void checkInventory(GameTestHelper helper, Player holder, FormulaContext formula) {
        holder.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 2));
        holder.getInventory().setItem(1, new ItemStack(Items.RESIN_BRICK, 4));
        holder.getInventory().setItem(2, new ItemStack(Items.WATER_BUCKET));
        for (String json : new String[]{
                "{\"type\":\"evaluate:inventory_value\",\"comparison\":\"==\",\"compare_to\":537}",
                "{\"type\":\"evaluate:inventory_value\",\"slot\":[\"hotbar.*\",\"weapon.mainhand\",0],\"comparison\":\"==\",\"compare_to\":537}",
                "{\"type\":\"evaluate:inventory_value\",\"slot\":\"weapon.mainhand\",\"comparison\":\"==\",\"compare_to\":512}",
                "{\"type\":\"evaluate:inventory_value\",\"item_condition\":{\"type\":\"saturn:item_id\",\"item\":\"minecraft:iron_ingot\"},\"comparison\":\"==\",\"compare_to\":512}"
        }) {
            EntityCondition condition = EntityCondition.CODEC.parse(ops(helper), JsonParser.parseString(json)).getOrThrow();
            helper.assertTrue(condition.test(holder, formula), "Inventory values must filter, count stacks, and deduplicate main hand: " + json);
            helper.assertTrue(EntityCondition.CODEC.parse(ops(helper), EntityCondition.CODEC.encodeStart(ops(helper), condition).getOrThrow()).getOrThrow().equals(condition),
                    "Inventory condition must roundtrip");
        }
        holder.getInventory().clearContent();
        EntityCondition empty = EntityCondition.CODEC.parse(ops(helper), JsonParser.parseString(
                "{\"type\":\"evaluate:inventory_value\",\"comparison\":\"==\",\"compare_to\":0}")).getOrThrow();
        helper.assertTrue(empty.test(holder, formula), "An empty inventory has a total value of zero");
    }

    static void checkReloaded(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(ItemValues.get(server, savedStack()).orElseThrow() == 47.125, "Action-created component values must survive /reload");
        Player holder = helper.makeMockPlayer(GameType.SURVIVAL);
        action(helper, "{\"type\":\"evaluate:reset_value\"}").execute(holder, savedStack(), FormulaContext.of(holder));
    }

    private static ItemStack savedStack() {
        ItemStack stack = new ItemStack(Items.BARRIER);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Action value"));
        return stack;
    }

    private static RegistryOps<JsonElement> ops(GameTestHelper helper) {
        return helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
    }

    private static ItemAction action(GameTestHelper helper, String json) {
        ItemAction action = ItemAction.CODEC.parse(ops(helper), JsonParser.parseString(json)).getOrThrow();
        JsonElement encoded = ItemAction.CODEC.encodeStart(ops(helper), action).getOrThrow();
        ItemAction decoded = ItemAction.CODEC.parse(ops(helper), encoded).getOrThrow();
        helper.assertTrue(ItemAction.CODEC.encodeStart(ops(helper), decoded).getOrThrow().equals(encoded),
                "Item action must roundtrip: " + json);
        return action;
    }

    private static ItemCondition condition(GameTestHelper helper, String json) {
        ItemCondition condition = ItemCondition.CODEC.parse(ops(helper), JsonParser.parseString(json)).getOrThrow();
        helper.assertTrue(ItemCondition.CODEC.parse(ops(helper), ItemCondition.CODEC.encodeStart(ops(helper), condition).getOrThrow()).getOrThrow().equals(condition),
                "Item condition must roundtrip: " + json);
        return condition;
    }
}
