package com.iafenvoy.evaluate_tests;

import com.iafenvoy.evaluate.api.ItemValues;
import com.iafenvoy.evaluate.api.ValueKey;
import com.iafenvoy.evaluate.data.WorldValuesData;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Real command permissions, graph invalidation, component keys, and disk-backed world isolation. */
final class CommandChecks {
    private CommandChecks() {
    }

    static void checkInitial(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
        expectRejected(helper, source.withPermission(LevelBasedPermissionSet.forLevel(PermissionLevel.ALL)),
                "evaluate set minecraft:iron_ingot 5");
        expectRejected(helper, source.withPermission(LevelBasedPermissionSet.MODERATOR),
                "evaluate reset minecraft:iron_ingot");
        source = source.withPermission(LevelBasedPermissionSet.GAMEMASTER);
        Map<ValueKey.ItemValue, Double> before = ItemValues.all(server);
        execute(helper, source, "evaluate set minecraft:iron_ingot 2.5");
        helper.assertTrue(ItemValues.get(server, Items.IRON_INGOT).orElse(-1) == 2.5, "Command value must override the datapack base");
        helper.assertTrue(ItemValues.get(server, Items.IRON_BLOCK).orElse(-1) == 22.5, "World values must feed recipe inference immediately");
        helper.assertTrue(before.get(ValueKey.ItemValue.of(new ItemStack(Items.IRON_INGOT))) == 256,
                "An old snapshot must remain unchanged after a command");
        Map<ValueKey.ItemValue, Double> after = ItemValues.all(server);
        execute(helper, source, "evaluate set minecraft:iron_ingot 2.5");
        helper.assertTrue(after == ItemValues.all(server), "An unchanged command value must retain the cached graph");
        execute(helper, source, "evaluate reset minecraft:iron_ingot");
        helper.assertTrue(ItemValues.get(server, Items.IRON_INGOT).orElse(-1) == 256, "Reset must restore datapack values");
        helper.assertTrue(ItemValues.get(server, Items.IRON_BLOCK).orElse(-1) == 2304, "Reset must recalculate downstream recipes");
        execute(helper, source, "evaluate set minecraft:popped_chorus_fruit 4");
        helper.assertTrue(ItemValues.get(server, Items.POPPED_CHORUS_FRUIT).orElse(-1) == 4, "Command value must beat a final override");
        helper.assertTrue(ItemValues.get(server, Items.PURPUR_SLAB).orElse(-1) == 2, "World value must propagate before final overrides");
        execute(helper, source, "evaluate reset minecraft:popped_chorus_fruit");
        helper.assertTrue(ItemValues.get(server, Items.POPPED_CHORUS_FRUIT).orElse(-1) == 9.5, "Reset must restore the final override");
        execute(helper, source, "evaluate set minecraft:purpur_block 0");
        helper.assertTrue(ItemValues.get(server, Items.PURPUR_SLAB).isEmpty(), "World zero must remain free in the graph");
        helper.assertTrue(!ItemValues.all(server).containsKey(ValueKey.ItemValue.of(new ItemStack(Items.PURPUR_BLOCK))),
                "World zero must have no positive snapshot result");
        execute(helper, source, "evaluate reset minecraft:purpur_block");
        Map<ValueKey.ItemValue, Double> unchanged = ItemValues.all(server);
        for (String command : new String[]{"evaluate set minecraft:iron_ingot -1", "evaluate set minecraft:iron_ingot NaN",
                "evaluate set minecraft:iron_ingot Infinity", "evaluate set minecraft:air 1", "evaluate reset minecraft:iron_ingot"})
            expectRejected(helper, source, command);
        helper.assertTrue(unchanged == ItemValues.all(server), "Rejected commands must not modify the graph");

        execute(helper, source, "evaluate set minecraft:barrier 123.5");
        execute(helper, source, "evaluate set minecraft:barrier[minecraft:custom_name='World value'] 321.5");
        ItemStack named = namedBarrier();
        helper.assertTrue(ItemValues.get(server, named).orElse(-1) == 321.5, "Command item components must be preserved");
        helper.assertTrue(ItemValues.get(server, Items.BARRIER).orElse(-1) == 123.5, "Component values must not affect the default stack");
        checkPersistence(helper);
    }

    static void checkReloaded(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(ItemValues.get(server, Items.BARRIER).orElse(-1) == 123.5, "World value must survive /reload");
        helper.assertTrue(ItemValues.get(server, namedBarrier()).orElse(-1) == 321.5, "Component world value must survive /reload");
        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
        execute(helper, source, "evaluate reset minecraft:barrier");
        helper.assertTrue(ItemValues.get(server, namedBarrier()).orElse(-1) == 321.5, "Reset must remove only the selected component key");
        execute(helper, source, "evaluate reset minecraft:barrier[minecraft:custom_name='World value']");
        helper.assertTrue(WorldValuesData.get(server).values().isEmpty(), "All test command values must be cleaned up");
        server.getDataStorage().saveAndJoin();
        try (SavedDataStorage disk = storage(server, server.getWorldPath(LevelResource.DATA))) {
            helper.assertTrue(disk.computeIfAbsent(WorldValuesData.TYPE).values().isEmpty(), "Reset must also persist to disk");
        }
    }

    private static void checkPersistence(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        server.getDataStorage().saveAndJoin();
        helper.assertTrue(Files.isRegularFile(server.getWorldPath(LevelResource.DATA).resolve("evaluate/values.dat")),
                "Values must be saved inside this world's data/evaluate/values.dat");
        try (SavedDataStorage disk = storage(server, server.getWorldPath(LevelResource.DATA))) {
            WorldValuesData loaded = disk.computeIfAbsent(WorldValuesData.TYPE);
            helper.assertTrue(loaded != WorldValuesData.get(server), "Persistence check must read a fresh instance from disk");
            helper.assertTrue(loaded.values().equals(WorldValuesData.get(server).values()), "Disk load must preserve double values and component patches");
            Path otherWorld = Files.createTempDirectory(server.getWorldPath(LevelResource.ROOT), "evaluate-other-world-");
            try (SavedDataStorage other = storage(server, otherWorld)) {
                WorldValuesData isolated = other.computeIfAbsent(WorldValuesData.TYPE);
                helper.assertTrue(isolated.values().isEmpty(), "A separate world must not inherit command values");
                isolated.set(new ItemStack(Items.BARRIER), 7);
                helper.assertTrue(WorldValuesData.get(server).values().get(ValueKey.ItemValue.of(new ItemStack(Items.BARRIER))) == 123.5,
                        "Editing a different world must not affect the running world's values");
            }
        } catch (IOException error) {
            throw new IllegalStateException("Cannot verify saved world values", error);
        }
    }

    private static SavedDataStorage storage(MinecraftServer server, Path folder) {
        return new SavedDataStorage(null, folder, DataFixers.getDataFixer(), server.registryAccess());
    }

    private static ItemStack namedBarrier() {
        ItemStack stack = new ItemStack(Items.BARRIER);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("World value"));
        return stack;
    }

    private static void execute(GameTestHelper helper, CommandSourceStack source, String command) {
        try {
            helper.assertTrue(source.getServer().getCommands().getDispatcher().execute(command, source) == 1, "Command must succeed: " + command);
        } catch (CommandSyntaxException error) {
            throw new IllegalStateException("Command failed: " + command, error);
        }
    }

    private static void expectRejected(GameTestHelper helper, CommandSourceStack source, String command) {
        boolean rejected = false;
        try { source.getServer().getCommands().getDispatcher().execute(command, source); }
        catch (CommandSyntaxException expected) { rejected = true; }
        helper.assertTrue(rejected, "Command must be rejected: " + command);
    }
}
