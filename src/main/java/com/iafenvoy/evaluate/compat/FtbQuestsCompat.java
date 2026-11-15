package com.iafenvoy.evaluate.compat;

import com.iafenvoy.evaluate.Evaluate;
import com.iafenvoy.evaluate.api.EvaluationContext;
import com.iafenvoy.evaluate.api.ItemValues;
import com.iafenvoy.evaluate.api.ValueKey;
import com.iafenvoy.evaluate.data.ValueReloadListener;
import com.iafenvoy.evaluate.evaluator.FtbQuestsEvaluator;
import com.iafenvoy.evaluate.network.payload.InvalidateValuesPayload;
import com.iafenvoy.evaluate.value.Conversion;
import de.marhali.json5.Json5Object;
import dev.ftb.mods.ftblibrary.json5.Json5Util;
import dev.ftb.mods.ftbquests.api.neoforge.FTBQuestsEvent;
import dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem;
import dev.ftb.mods.ftbquests.item.MissingItem;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.reward.ItemReward;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * All references to optional FTB classes stay behind the mod-presence check.
 */
public final class FtbQuestsCompat {
    private static boolean dirty;

    private FtbQuestsCompat() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(FtbQuestsCompat::fileChanged);
        NeoForge.EVENT_BUS.addListener(FtbQuestsCompat::tick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ServerStartedEvent _) -> dirty = false);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent _) -> dirty = false);
    }

    private static void fileChanged(FTBQuestsEvent.ClearFileCache event) {
        if (event.getEventData().file() instanceof ServerQuestFile) dirty = true;
    }

    private static void tick(ServerTickEvent.Post event) {
        if (!dirty || !ServerQuestFile.exists() || ServerQuestFile.getInstance().isLoading()) return;
        dirty = false;
        MinecraftServer server = event.getServer();
        ValueReloadListener listener = server.getServerResources().managers().getListener(ValueReloadListener.KEY);
        if (listener.evaluators().listElements().noneMatch(h -> h.value() instanceof FtbQuestsEvaluator)) return;
        listener.invalidate();
        ItemValues.all(server);
        server.getPlayerList().getPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, InvalidateValuesPayload.INSTANCE));
    }

    public static void evaluate(EvaluationContext context, FtbQuestsEvaluator settings) {
        if (!ServerQuestFile.exists() || ServerQuestFile.getInstance().isLoading()) return;
        List<Quest> quests = new ArrayList<>();
        ServerQuestFile.getInstance().forAllQuests(quests::add);
        quests.sort(Comparator.comparing(quest -> quest.getCodeString()));
        int mapped = 0;
        for (Quest quest : quests)
            try {
                mapped += mapQuest(context, settings, quest);
            } catch (RuntimeException error) {
                Evaluate.LOGGER.warn("Skipping FTB Quests exchange {}: {}", quest.getCodeString(), error.toString());
            }
        Evaluate.LOGGER.debug("Mapped {} FTB Quests reward relationships from {} quests (repeatable only: {})", mapped, quests.size(), settings.repeatableOnly());
    }

    private static int mapQuest(EvaluationContext context, FtbQuestsEvaluator settings, Quest quest) {
        if ((settings.repeatableOnly() && !quest.canBeRepeated()) || quest.getTasks().isEmpty() || quest.getRewards().isEmpty())
            return 0;
        // Validate the whole exchange before submitting anything to the shared graph.
        Map<ValueKey.ItemValue, StackAmount> inputs = new LinkedHashMap<>();
        for (Task task : quest.getTasks()) {
            Json5Object data = new Json5Object();
            task.writeData(data, context.resources().getRegistryLookup());
            if (Json5Util.getBoolean(data, "optional_task").orElse(false)) continue;
            if (task.getClass() != ItemTask.class) return 0;
            ItemTask itemTask = (ItemTask) task;
            if (!itemTask.consumesResources() || itemTask.isOnlyFromCrafting()) return 0;
            ItemStack stack = itemTask.getItemStack();
            if (!usable(stack) || ItemMatchingSystem.INSTANCE.isItemFilter(stack)) return 0;
            // With no component matching, the default stack is a valid representative input.
            if (Json5Util.getString(data, "match_components").orElse("none").equals("none"))
                stack = new ItemStack(stack.getItem());
            if (!itemTask.test(stack)) return 0;
            add(inputs, stack, Math.toIntExact(itemTask.getMaxProgress()));
        }
        if (inputs.isEmpty()) return 0;
        Map<ValueKey.ItemValue, StackAmount> outputs = new LinkedHashMap<>();
        for (Reward reward : quest.getRewards()) {
            if (reward.getClass() != ItemReward.class) return 0;
            ItemReward itemReward = (ItemReward) reward;
            Json5Object data = new Json5Object();
            reward.writeData(data, context.resources().getRegistryLookup());
            if (Json5Util.getInt(data, "random_bonus").orElse(0) != 0 || Json5Util.getBoolean(data, "only_one").orElse(false))
                return 0;
            if (!usable(itemReward.getItem())) return 0;
            // FTB's separate reward count replaces the count stored inside its item stack.
            add(outputs, itemReward.getItem(), itemReward.getCount());
        }
        Map<ValueKey, Integer> costs = new LinkedHashMap<>();
        inputs.values().forEach(input -> costs.put(context.item(input.stack()), input.amount()));
        // Charge the entire input cost for each distinct output: no arbitrary splitting or byproduct credits.
        outputs.values().forEach(output -> context.addConversion(new Conversion<>(output.amount(), context.item(output.stack()), costs, settings.extraValue())));
        return outputs.size();
    }

    private static boolean usable(ItemStack stack) {
        return !stack.isEmpty() && !(stack.getItem() instanceof MissingItem);
    }

    private static void add(Map<ValueKey.ItemValue, StackAmount> amounts, ItemStack stack, int amount) {
        if (amount <= 0) throw new IllegalArgumentException("Exchange amounts must be positive");
        amounts.merge(ValueKey.ItemValue.of(stack), new StackAmount(stack.copyWithCount(1), amount),
                (a, b) -> new StackAmount(a.stack(), Math.addExact(a.amount(), b.amount())));
    }

    private record StackAmount(ItemStack stack, int amount) {
    }
}
