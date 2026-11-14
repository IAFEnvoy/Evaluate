package com.iafenvoy.evaluate_tests;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.iafenvoy.evaluate.api.*;
import com.iafenvoy.evaluate.data.ValueReloadListener;
import com.iafenvoy.evaluate.evaluator.ConstantEvaluator;
import com.iafenvoy.evaluate.evaluator.RecipeEvaluator;
import com.iafenvoy.evaluate.registry.EvaluateResourceKeys;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.mojang.serialization.MapCodec;
import com.iafenvoy.evaluate.value.Conversion;
import com.iafenvoy.evaluate.network.ValueRequestPayload;
import com.iafenvoy.evaluate.network.ValueResponsePayload;
import io.netty.buffer.Unpooled;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.Map;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod("evaluate_tests")
public class ValuesGameTestMod {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("evaluate_tests", "values_and_reload");
    private static final AtomicInteger EVALUATIONS = new AtomicInteger();

    public ValuesGameTestMod(IEventBus bus) {
        FtbQuestsChecks.register(bus);
        CycleRecipeChecks.register(bus);
        DeferredRegister<MapCodec<? extends Evaluator>> types = DeferredRegister.create(EvaluateResourceKeys.EVALUATOR_TYPE, "evaluate_tests");
        types.register("external", () -> ExternalEvaluator.CODEC);
        types.register("unused", () -> UnusedEvaluator.CODEC);
        types.register(bus);
        DeferredRegister<Consumer<GameTestHelper>> functions = DeferredRegister.create(Registries.TEST_FUNCTION, "evaluate_tests");
        functions.register(ID.getPath(), () -> ValuesGameTestMod::check);
        functions.register(bus);
        bus.addListener(ValuesGameTestMod::register);
    }

    private static void register(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Identifier.fromNamespaceAndPath("evaluate_tests", "empty"));
        event.registerTest(ID, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, ID),
                new TestData<>(environment, Identifier.withDefaultNamespace("empty"), 1200, 0, true)));
    }

    private static void check(GameTestHelper helper) {
        assertValue(helper, Items.IRON_INGOT, 256);
        assertValue(helper, Items.GOLD_INGOT, 2048);
        assertValue(helper, Items.DIAMOND, 10000); // Higher pack replaces the same single-value resource.
        assertValue(helper, Items.OAK_PLANKS, 8);
        assertValue(helper, Items.CRAFTING_TABLE, 32);
        assertValue(helper, Items.BUCKET, 768);
        helper.assertTrue(ItemValues.get(helper.getLevel().getServer(), Items.WATER_BUCKET).isEmpty(), "No custom bucket conversions are loaded");
        helper.assertTrue(ItemValues.get(helper.getLevel().getServer(), Items.LAVA_BUCKET).isEmpty(), "Lava bucket requires its own value definition");
        helper.assertTrue(ItemValues.get(helper.getLevel().getServer(), Items.MILK_BUCKET).isEmpty(), "Milk bucket requires its own value definition");
        assertValue(helper, Items.DIAMOND_BLOCK, 90000);
        assertValue(helper, Items.RESIN_CLUMP, 12.5);
        assertValue(helper, Items.RESIN_BRICK, 6.25); // Test recipe has two outputs.
        assertValue(helper, Items.BIRCH_BUTTON, 77); // Tag from the current reload, not old global tags.
        assertValue(helper, Items.ACACIA_BUTTON, 33);
        assertValue(helper, Items.OAK_BUTTON, 44);
        assertValue(helper, Items.SPRUCE_BUTTON, 44);
        assertValue(helper, Items.APPLE, 77); // Mixed list entries use OR semantics.
        assertValue(helper, Items.COPPER_NUGGET, 999);
        assertValue(helper, Items.COPPER_BLOCK, 1152);
        assertValue(helper, Items.RESIN_BRICKS, 25); // Four bricks, using the automatic recipe graph.
        assertValue(helper, Items.RESIN_BRICK_SLAB, 12.5);
        assertValue(helper, Items.POPPED_CHORUS_FRUIT, 9.5); // The override registry only replaces final results.
        helper.assertTrue(ItemValues.get(helper.getLevel().getServer(), Items.PURPUR_BLOCK).isEmpty(), "A zero override hides the final value");
        assertValue(helper, Items.PURPUR_SLAB, 3.625); // The zero override must not propagate.
        assertValue(helper, Items.EXPERIENCE_BOTTLE, 3.625);
        assertValue(helper, Items.IRON_NUGGET, 256.0 / 9);
        assertValue(helper, Items.TRIAL_KEY, 7.5); // (12.5 + smelting extra 2.5) / 2
        assertValue(helper, Items.OMINOUS_TRIAL_KEY, 6.25); // Blasting keeps its independent default extra value.
        helper.assertTrue(ItemValues.get(helper.getLevel().getServer(), Items.POISONOUS_POTATO).isEmpty(), "Free items have no public positive value");
        checkComponentMatcher(helper);
        checkValuePayloads(helper);
        checkDefinitions(helper);
        KubeJSChecks.checkInitial(helper);
        FtbQuestsChecks.checkInitial(helper);
        CycleRecipeChecks.check(helper);
        MinecraftServer server = helper.getLevel().getServer();
        Map<ValueKey.Item, Double> snapshot = ItemValues.all(server);
        Registry<Evaluator> evaluatorRegistry = ItemValues.evaluators(server);
        Registry<ValueOverride> overrideRegistry = ItemValues.overrides(server);
        int previousEvaluations = EVALUATIONS.get();
        Map<Path, String> reloadFiles = changeReloadFixtures();
        AtomicBoolean complete = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((ignored, error) -> {
            try { restoreReloadFixtures(reloadFiles); }
            catch (Exception restoreError) {
                if (error == null) error = restoreError;
                else error.addSuppressed(restoreError);
            }
            failure.set(error);
            complete.set(true);
        });
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(complete.get(), "Waiting for datapack reload");
            helper.assertTrue(failure.get() == null, "Reload failed: " + failure.get());
            helper.assertTrue(snapshot != ItemValues.all(server), "Reload must publish a new snapshot");
            helper.assertTrue(!snapshot.equals(ItemValues.all(server)), "Reload must apply edited evaluator and override data");
            helper.assertTrue(evaluatorRegistry != ItemValues.evaluators(server) && overrideRegistry != ItemValues.overrides(server),
                    "Reload must replace both registry snapshots");
            assertValue(helper, Items.DIAMOND_BLOCK, 90000);
            assertValue(helper, Items.BIRCH_BUTTON, 77);
            assertValue(helper, Items.RESIN_BRICK, 6.25);
            assertValue(helper, Items.POPPED_CHORUS_FRUIT, 10.5);
            assertValue(helper, Items.PURPUR_SLAB, 4.25);
            assertValue(helper, Items.EXPERIENCE_BOTTLE, 4.25);
            assertValue(helper, Items.TRIAL_KEY, 8.75);
            helper.assertTrue(((RecipeEvaluator) ItemValues.evaluators(server).getValue(Identifier.fromNamespaceAndPath("evaluate", "recipes/smelting"))).extraValue() == 5,
                    "Reload must replace recipe evaluator parameters");
            helper.assertTrue(EVALUATIONS.get() == previousEvaluations + 2, "Reload must instantiate and rerun both evaluator data entries");
            checkComponentMatcher(helper);
            KubeJSChecks.checkReloaded(helper);
            FtbQuestsChecks.checkReloaded(helper);
            CycleRecipeChecks.check(helper);
        }).thenExecute(() -> FtbQuestsChecks.addQuestReloadChecks(helper));
    }

    private static Map<Path, String> changeReloadFixtures() {
        Map<Path, String> originals = new LinkedHashMap<>();
        Map<String, String> changes = Map.of(
                "evaluator/test/external_low.json", "{\"type\":\"evaluate_tests:external\",\"value\":10.25}",
                "evaluator/recipes/smelting.json", "{\"type\":\"evaluate:recipe\",\"recipe_type\":\"minecraft:smelting\",\"extra_value\":5}",
                "value_override/test/popped_chorus_fruit.json", "{\"item\":\"minecraft:popped_chorus_fruit\",\"value\":10.5}");
        try {
            for (Map.Entry<String, String> change : changes.entrySet()) {
                Path path = Path.of(ValuesGameTestMod.class.getResource("/data/evaluate/evaluate/" + change.getKey()).toURI());
                originals.put(path, Files.readString(path));
                Files.writeString(path, change.getValue());
            }
            KubeJSChecks.changeReloadFixtures(originals);
            FtbQuestsChecks.changeReloadFixtures(originals);
        } catch (Exception error) {
            restoreReloadFixtures(originals);
            throw new IllegalStateException("Cannot edit test reload fixtures", error);
        }
        return originals;
    }

    private static void restoreReloadFixtures(Map<Path, String> originals) {
        originals.forEach((path, text) -> {
            try { Files.writeString(path, text); }
            catch (IOException error) { throw new UncheckedIOException(error); }
        });
    }

    private static void checkComponentMatcher(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("VALUED"));
        named.set(DataComponents.DAMAGE, 7); // Not the sampled component patch: match the actual stack.
        helper.assertTrue(ItemValues.get(server, named).orElse(-1) == 909, "Stack-sensitive ingredient must match damaged named sword");
        ItemStack other = named.copy();
        other.set(DataComponents.CUSTOM_NAME, Component.literal("OTHER"));
        helper.assertTrue(ItemValues.get(server, other).orElse(-1) != 909, "Component matcher must not leak through an item cache");
    }

    private static void checkDefinitions(GameTestHelper helper) {
        RegistryOps<JsonElement> ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        JsonArray invalid = JsonParser.parseString("""
                [
                  {"item":[],"value":1},
                  {"item":"minecraft:diamond","value":-1},
                  {"item":"minecraft:diamond","value":-0.25},
                  {"item":"minecraft:diamond","value":1e309},
                  {"item":"minecraft:diamond","value":"NaN"},
                  {"item":"minecraft:diamond","value":"Infinity"},
                  {"item":{"type":"saturn:no_such_matcher"},"value":1},
                  {"item":"minecraft:diamond","value":1,"phase":"before"},
                  {"item":"minecraft:diamond","value":1,"priority":1.5},
                  {"item":"minecraft:diamond","value":1,"conversions":[]},
                  {"values":[{"item":"minecraft:diamond","value":7}]}
                ]
                """).getAsJsonArray();
        for (JsonElement json : invalid) {
            json.getAsJsonObject().addProperty("type", "evaluate:constant");
            boolean rejected = false;
            try { Evaluator.CODEC.parse(ops, json).getOrThrow(); }
            catch (RuntimeException expected) { rejected = true; }
            helper.assertTrue(rejected, "Invalid single-value file must be rejected: " + json);
        }
        Evaluator value = Evaluator.CODEC.parse(ops, JsonParser.parseString("""
                {"type":"evaluate:constant","item":["minecraft:diamond"],"value":0.125,"priority":2,"comment":"one file, one value"}
                """)).getOrThrow();
        helper.assertTrue(value instanceof ConstantEvaluator constant && constant.value() == 0.125 && constant.priority() == 2,
                "Constant evaluator must decode array matchers and optional fields");
        helper.assertTrue(Evaluator.CODEC.parse(ops, Evaluator.CODEC.encodeStart(ops, value).getOrThrow()).getOrThrow().equals(value),
                "Evaluator type dispatch must support encoding and decoding");
        Evaluator large = Evaluator.CODEC.parse(ops, JsonParser.parseString("""
                {"type":"evaluate:constant","item":"minecraft:diamond","value":1e100}
                """)).getOrThrow();
        helper.assertTrue(large instanceof ConstantEvaluator constant && constant.value() == 1.0E100, "Double definitions may exceed the long range");
        for (String json : List.of(
                "{\"type\":\"evaluate:no_such_type\"}",
                "{\"type\":\"evaluate:recipe\",\"recipe_type\":\"minecraft:smelting\",\"extra_value\":-1}",
                "{\"type\":\"evaluate:recipe\",\"recipe_type\":\"minecraft:missing\"}",
                "{\"type\":\"evaluate:recipe\",\"recipe_type\":\"minecraft:smelting\",\"phase\":\"after\"}"))
            helper.assertTrue(Evaluator.CODEC.parse(ops, JsonParser.parseString(json)).error().isPresent(), "Invalid evaluator must be rejected: " + json);
        helper.assertTrue(ValueOverride.CODEC.parse(ops, JsonParser.parseString("""
                {"item":"minecraft:diamond","value":7,"phase":"after"}
                """)).error().isPresent(), "Override entries must not accept a phase field");
        Evaluator js = Evaluator.CODEC.parse(ops, JsonParser.parseString("""
                {"type":"evaluate:js","id":"evaluate_tests:params","params":{"value":1.25,"nested":[1,2]}}
                """)).getOrThrow();
        helper.assertTrue(Evaluator.CODEC.parse(ops, Evaluator.CODEC.encodeStart(ops, js).getOrThrow()).getOrThrow().equals(js),
                "JS evaluator callback id and params must roundtrip without requiring KubeJS");
        for (String invalidJs : List.of(
                "{\"type\":\"evaluate:js\",\"id\":\"evaluate_tests:params\",\"params\":[]}",
                "{\"type\":\"evaluate:js\",\"id\":\"evaluate_tests:params\",\"params\":1}",
                "{\"type\":\"evaluate:js\",\"id\":\"Invalid ID\"}",
                "{\"type\":\"evaluate:js\",\"id\":\"evaluate_tests:params\",\"phase\":\"after\"}"))
            helper.assertTrue(Evaluator.CODEC.parse(ops, JsonParser.parseString(invalidJs)).error().isPresent(), "Invalid JS evaluator must be rejected: " + invalidJs);
        ValueReloadListener listener = helper.getLevel().getServer().getServerResources().managers().getListener(ValueReloadListener.KEY);
        helper.assertTrue(listener.evaluators().getValue(Identifier.fromNamespaceAndPath("evaluate", "recipes/smelting"))
                instanceof RecipeEvaluator recipe && recipe.extraValue() == 2.5, "Same-id evaluator data must replace the lower pack");
        helper.assertTrue(listener.overrides().getValue(Identifier.fromNamespaceAndPath("evaluate", "test/popped_chorus_fruit")).value() == 9.5,
                "Final overrides must have their own registry");
        helper.assertTrue(helper.getLevel().registryAccess().lookupOrThrow(EvaluateResourceKeys.VALUE_OVERRIDE).listElements().findAny().isPresent(),
                "value_override must be a registered native datapack registry");
    }

    private static void checkValuePayloads(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD, 3);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("VALUED"));
        stack.set(DataComponents.DAMAGE, 7);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        try {
            ValueRequestPayload.STREAM_CODEC.encode(buffer, new ValueRequestPayload(42, stack));
            ValueRequestPayload decoded = ValueRequestPayload.STREAM_CODEC.decode(buffer);
            helper.assertTrue(decoded.requestId() == 42 && decoded.stack().getCount() == 1
                    && ItemStack.isSameItemSameComponents(stack, decoded.stack()), "Value query must preserve components and normalize count");
            helper.assertTrue(decoded.answer(server).value() == 909, "Value query must use server component matchers");
            buffer.clear();
            ValueResponsePayload response = new ValueRequestPayload(43, new ItemStack(Items.RESIN_BRICK)).answer(server);
            ValueResponsePayload.STREAM_CODEC.encode(buffer, response);
            helper.assertTrue(ValueResponsePayload.STREAM_CODEC.decode(buffer).equals(new ValueResponsePayload(43, 6.25)),
                    "Value response must preserve fractional doubles and request id");
            helper.assertTrue(new ValueRequestPayload(44, new ItemStack(Items.WATER_BUCKET)).answer(server).value() == 0,
                    "Unknown items use an empty tooltip result");
            helper.assertTrue(new ValueRequestPayload(45, ItemStack.EMPTY).answer(server).value() == 0,
                    "Empty stacks must not produce a value");
        } finally { buffer.release(); }
    }

    private static void assertValue(GameTestHelper helper, Item item, double expected) {
        OptionalDouble actual = ItemValues.get(helper.getLevel().getServer(), item);
        helper.assertTrue(actual.isPresent() && Math.abs(actual.getAsDouble() - expected) <= Math.max(1, Math.abs(expected)) * 1.0E-12,
                item + ": expected " + expected + ", got " + actual);
    }

    private record ExternalEvaluator(double value) implements Evaluator {
        private static final MapCodec<ExternalEvaluator> CODEC = MiscCodecs.NON_NEGATIVE_DOUBLE.fieldOf("value")
                .xmap(ExternalEvaluator::new, ExternalEvaluator::value);

        @Override
        public MapCodec<ExternalEvaluator> codec() { return CODEC; }

        @Override
        public void evaluate(EvaluationContext context) {
            EVALUATIONS.incrementAndGet();
            context.addValue(new ItemStack(Items.POPPED_CHORUS_FRUIT), this.value);
            context.addValue(new ItemStack(Items.RESIN_CLUMP), 0.5); // The constant evaluator remains locked.
            context.addConversion(new Conversion<>(2, context.item(new ItemStack(Items.EXPERIENCE_BOTTLE)),
                    Map.of(context.item(new ItemStack(Items.POPPED_CHORUS_FRUIT)), 1)));
        }
    }

    private record UnusedEvaluator() implements Evaluator {
        private static final MapCodec<UnusedEvaluator> CODEC = MapCodec.unit(new UnusedEvaluator());

        @Override
        public MapCodec<UnusedEvaluator> codec() { return CODEC; }

        @Override
        public void evaluate(EvaluationContext context) { throw new AssertionError("A registered type without datapack entries must not execute"); }
    }
}
