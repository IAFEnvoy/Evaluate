package com.iafenvoy.evaluate_tests;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iafenvoy.evaluate.api.Evaluator;
import com.iafenvoy.evaluate.api.ItemValues;
import com.iafenvoy.evaluate.api.ValueKey;
import com.iafenvoy.evaluate.evaluator.FtbQuestsEvaluator;
import com.mojang.serialization.JsonOps;
import dev.ftb.mods.ftblibrary.json5.Json5Ops;
import dev.ftb.mods.ftblibrary.json5.Json5Util;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Real FTB quest files and lifecycle events, with custom items isolated from vanilla recipe values. */
final class FtbQuestsChecks {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("evaluate_tests");
    private static final Map<String, DeferredItem<Item>> VALUES = new LinkedHashMap<>();
    private static final List<String> SKIPPED = List.of("collection", "mixed_task", "random_bonus", "only_one", "choice",
            "mixed_reward", "crafting_only", "oversized", "overflow", "unknown_input", "all_optional", "missing_item");

    static {
        for (String name : List.of("token", "block", "chain", "pair_a", "pair_b", "optional", "once", "components", "no_components"))
            VALUES.put(name, ITEMS.registerSimpleItem("quest_" + name));
        for (String name : SKIPPED) VALUES.put(name, ITEMS.registerSimpleItem("quest_" + name));
    }

    static void register(IEventBus bus) { ITEMS.register(bus); }

    static void checkInitial(GameTestHelper helper) {
        checkCodec(helper);
        if (!ModList.get().isLoaded("ftbquests")) {
            for (String name : VALUES.keySet()) if (!name.equals("token")) checkUnknown(helper, name);
            checkValue(helper, "token", 999);
            return;
        }
        helper.assertTrue(ServerQuestFile.exists() && !ServerQuestFile.getInstance().isLoading(), "FTB must load before initial value calculation");
        checkValue(helper, "token", 999); // Final override does not feed back into recipe or quest costs.
        checkValue(helper, "block", 1156.5); // 9 * ((2 * 256 + 2) / 4)
        checkValue(helper, "chain", 258); // (4 * 128.5 + 2) / 2
        checkValue(helper, "pair_a", 1025);
        checkValue(helper, "pair_b", 512.5);
        checkValue(helper, "optional", 258);
        checkValue(helper, "components", 455.5); // (Named sword base value 909 + 2) / 2
        checkValue(helper, "no_components", ItemValues.get(helper.getLevel().getServer(), Items.DIAMOND_SWORD).orElseThrow() + 2);
        checkUnknown(helper, "once");
        checkSkipped(helper);
    }

    static void changeReloadFixtures(Map<Path, String> originals) throws Exception {
        Path path = Path.of(FtbQuestsChecks.class.getResource("/data/evaluate/evaluate/evaluator/ftb_quests.json").toURI());
        originals.put(path, Files.readString(path));
        Files.writeString(path, "{\"type\":\"evaluate:ftb_quests\",\"repeatable_only\":false,\"extra_value\":6}");
    }

    static void checkReloaded(GameTestHelper helper) {
        Evaluator evaluator = ItemValues.evaluators(helper.getLevel().getServer()).getValue(Identifier.fromNamespaceAndPath("evaluate", "ftb_quests"));
        helper.assertTrue(evaluator instanceof FtbQuestsEvaluator(
                        boolean repeatableOnly, double extraValue
                ) && !repeatableOnly && extraValue == 6,
                "Datapack reload must replace FTB evaluator settings even when FTB is absent");
        if (!ModList.get().isLoaded("ftbquests")) return;
        checkValue(helper, "token", 999);
        checkValue(helper, "block", 1165.5);
        checkValue(helper, "chain", 262);
        checkValue(helper, "pair_a", 1027);
        checkValue(helper, "pair_b", 513.5);
        checkValue(helper, "optional", 262);
        checkValue(helper, "once", 1027);
        checkValue(helper, "components", 457.5);
        checkSkipped(helper);
    }

    static void addQuestReloadChecks(GameTestHelper helper) {
        if (!ModList.get().isLoaded("ftbquests")) {
            helper.succeed();
            return;
        }
        MinecraftServer server = helper.getLevel().getServer();
        Map<ValueKey.ItemValue, Double> beforeReload = ItemValues.all(server);
        Path path = ServerQuestFile.getInstance().getFolder().resolve("chapters/evaluate_tests.json5");
        try {
            String original = Files.readString(path);
            try {
                JsonObject chapter = Json5Ops.INSTANCE.convertTo(JsonOps.INSTANCE, Json5Util.load(path)).getAsJsonObject();
                JsonArray quests = chapter.getAsJsonArray("quests");
                // The repeatable exchange now needs 3 iron instead of 2; the paired-output exchange is removed.
                quests.get(0).getAsJsonObject().getAsJsonArray("tasks").get(0).getAsJsonObject().addProperty("count", 2);
                for (int i = quests.size() - 1; i >= 0; i--)
                    if (quests.get(i).getAsJsonObject().get("id").getAsString().equals("2000000000000030")) quests.remove(i);
                Files.writeString(path, chapter.toString());
                // Same load method as /ftbquests reload quests; its cache-clear event precedes the file mutation.
                ServerQuestFile.getInstance().load(true, false);
            } finally { Files.writeString(path, original); }
        } catch (IOException error) { throw new IllegalStateException("Cannot reload FTB quest fixture", error); }
        helper.assertTrue(ItemValues.all(server) == beforeReload, "FTB cache invalidation must wait until the next server tick");
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(ItemValues.all(server) != beforeReload, "Waiting for quest reload recalculation");
            checkValue(helper, "block", 1741.5); // 9 * ((3 * 256 + 6) / 4)
            checkValue(helper, "chain", 390);
            checkUnknown(helper, "pair_a");
            checkUnknown(helper, "pair_b");
            checkValue(helper, "token", 999);
            checkSkipped(helper);
            KubeJSChecks.checkReloaded(helper); // Recalculation must retain callbacks from the current reload.
        }).thenExecute(() -> {
            Map<ValueKey.ItemValue, Double> beforeEdit = ItemValues.all(server);
            // Simulate the cache clear before an editor update, then mutate the live task after the event.
            ServerQuestFile file = ServerQuestFile.getInstance();
            ItemTask task = (ItemTask) file.getQuest(0x2000000000000010L).getTasks().iterator().next();
            file.clearCachedData();
            task.setStackAndCount(new ItemStack(Items.IRON_INGOT), 4);
            helper.startSequence().thenWaitUntil(() -> {
                helper.assertTrue(ItemValues.all(server) != beforeEdit, "Waiting for editor recalculation");
                checkValue(helper, "block", 2893.5); // 9 * ((5 * 256 + 6) / 4)
                checkValue(helper, "chain", 646);
                checkUnknown(helper, "pair_a");
                checkValue(helper, "token", 999);
            }).thenSucceed();
        });
    }

    private static void checkCodec(GameTestHelper helper) {
        RegistryOps<JsonElement> ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        Evaluator defaults = Evaluator.CODEC.parse(ops, JsonParser.parseString("{\"type\":\"evaluate:ftb_quests\"}")).getOrThrow();
        helper.assertTrue(defaults instanceof FtbQuestsEvaluator(
                        boolean repeatableOnly, double extraValue
                ) && repeatableOnly && extraValue == 0,
                "FTB settings must default to repeatable only and no extra cost");
        helper.assertTrue(Evaluator.CODEC.parse(ops, Evaluator.CODEC.encodeStart(ops, defaults).getOrThrow()).getOrThrow().equals(defaults),
                "FTB evaluator must roundtrip without loading optional FTB classes");
        for (String invalid : List.of("{\"type\":\"evaluate:ftb_quests\",\"extra_value\":-1}",
                "{\"type\":\"evaluate:ftb_quests\",\"extra_value\":1e309}"))
            helper.assertTrue(Evaluator.CODEC.parse(ops, JsonParser.parseString(invalid)).error().isPresent(), "Invalid FTB settings must fail: " + invalid);
    }

    private static void checkSkipped(GameTestHelper helper) { SKIPPED.forEach(name -> checkUnknown(helper, name)); }

    private static void checkUnknown(GameTestHelper helper, String name) {
        helper.assertTrue(ItemValues.get(helper.getLevel().getServer(), VALUES.get(name).get()).isEmpty(), "FTB " + name + " must stay unknown");
    }

    private static void checkValue(GameTestHelper helper, String name, double expected) {
        double actual = ItemValues.get(helper.getLevel().getServer(), VALUES.get(name).get()).orElse(-1);
        helper.assertTrue(Math.abs(actual - expected) < 1e-10, "FTB " + name + ": expected " + expected + ", got " + actual);
    }
}
