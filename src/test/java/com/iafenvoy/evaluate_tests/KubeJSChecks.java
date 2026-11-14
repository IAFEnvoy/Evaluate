package com.iafenvoy.evaluate_tests;

import com.iafenvoy.evaluate.api.Evaluator;
import com.iafenvoy.evaluate.api.ItemValues;
import com.iafenvoy.evaluate.evaluator.JsEvaluator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Runs in the existing integration test when -PtestKubeJS=true supplies actual server scripts. */
final class KubeJSChecks {
    static void checkInitial(GameTestHelper helper) {
        if (!ModList.get().isLoaded("kubejs")) return;
        checkValue(helper, Items.AMETHYST_SHARD, 18.25);
        checkValue(helper, Items.HEART_OF_THE_SEA, 21.5);
        checkValue(helper, Items.RECOVERY_COMPASS, 21.5);
        checkValue(helper, Items.PINK_PETALS, 111.25);
        checkValue(helper, Items.RABBIT_HIDE, 31.5);
        checkValue(helper, Items.DRAGON_EGG, 5000.25);
        checkValue(helper, Items.REINFORCED_DEEPSLATE, 77.25);
        checkValue(helper, Items.STRUCTURE_VOID, 7.75);
        checkValue(helper, Items.TRIAL_SPAWNER, 999.5);
        checkValue(helper, Items.DEBUG_STICK, 15); // (10.5 + 18.25 + 1.25) / 2, final override excluded.
        Evaluator evaluator = ItemValues.evaluators(helper.getLevel().getServer()).getValue(Identifier.fromNamespaceAndPath("evaluate_tests", "params"));
        helper.assertTrue(evaluator instanceof JsEvaluator js && js.params().get("value").getAsDouble() == 31.5,
                "JS callbacks must not mutate registry params");
    }

    static void changeReloadFixtures(Map<Path, String> originals) throws IOException {
        if (!ModList.get().isLoaded("kubejs")) return;
        Path root = FMLPaths.GAMEDIR.get().resolve("kubejs");
        Path script = root.resolve("server_scripts/evaluate.js");
        String source = Files.readString(script);
        originals.put(script, source);
        Files.writeString(script, source.replace("18.25", "19.25").replace("10.5", "12.5").replace("999.5", "1000.5")
                .replace("evaluate_tests:removed", "evaluate_tests:removed_now_unused")
                .replace("context.set('minecraft:reinforced_deepslate', 77.25)", "// Removed on reload"));
        Path data = root.resolve("data/evaluate_tests/evaluate/evaluator/params.json");
        String json = Files.readString(data);
        originals.put(data, json);
        Files.writeString(data, json.replace("31.5", "33.5"));
    }

    static void checkReloaded(GameTestHelper helper) {
        if (!ModList.get().isLoaded("kubejs")) return;
        checkValue(helper, Items.AMETHYST_SHARD, 19.25);
        checkValue(helper, Items.RABBIT_HIDE, 33.5);
        checkValue(helper, Items.TRIAL_SPAWNER, 1000.5);
        checkValue(helper, Items.DEBUG_STICK, 16.5);
        checkValue(helper, Items.DRAGON_EGG, 5000.25);
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(ItemValues.get(server, Items.REINFORCED_DEEPSLATE).isEmpty(), "Removed JS contributions must disappear on reload");
        helper.assertTrue(ItemValues.get(server, Items.STRUCTURE_VOID).isEmpty(), "Removed callbacks must not leak from previous scripts");
    }

    private static void checkValue(GameTestHelper helper, Item item, double expected) {
        double actual = ItemValues.get(helper.getLevel().getServer(), item).orElse(-1);
        helper.assertTrue(Math.abs(actual - expected) < 1e-10, "KubeJS " + item + ": expected " + expected + ", got " + actual);
    }
}
