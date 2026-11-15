package com.iafenvoy.evaluate.command;

import com.iafenvoy.evaluate.Evaluate;
import com.iafenvoy.evaluate.api.ItemValues;
import com.iafenvoy.evaluate.data.WorldValuesData;
import com.iafenvoy.evaluate.network.payload.InvalidateValuesPayload;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber
public final class EvaluateCommand {
    private static final SimpleCommandExceptionType INVALID_VALUE = new SimpleCommandExceptionType(Component.translatable("commands.evaluate.invalid_value"));
    private static final SimpleCommandExceptionType EMPTY_ITEM = new SimpleCommandExceptionType(Component.translatable("commands.evaluate.empty_item"));
    private static final SimpleCommandExceptionType NOT_SET = new SimpleCommandExceptionType(Component.translatable("commands.evaluate.not_set"));

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Evaluate.MOD_ID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("set")
                        .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0))
                                        .executes(context -> set(context.getSource(),
                                                ItemArgument.getItem(context, "item").createItemStack(1),
                                                DoubleArgumentType.getDouble(context, "value"))))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                                .executes(context -> reset(context.getSource(),
                                        ItemArgument.getItem(context, "item").createItemStack(1))))));
    }

    private static int set(CommandSourceStack source, ItemStack stack, double value) throws CommandSyntaxException {
        if (stack.isEmpty()) throw EMPTY_ITEM.create();
        if (!Double.isFinite(value) || value < 0) throw INVALID_VALUE.create();
        if (WorldValuesData.get(source.getServer()).set(stack, value)) refresh(source.getServer());
        source.sendSuccess(() -> Component.translatable("commands.evaluate.set", stack.getDisplayName(), Double.toString(value)), true);
        return 1;
    }

    private static int reset(CommandSourceStack source, ItemStack stack) throws CommandSyntaxException {
        if (stack.isEmpty()) throw EMPTY_ITEM.create();
        if (!WorldValuesData.get(source.getServer()).remove(stack)) throw NOT_SET.create();
        refresh(source.getServer());
        source.sendSuccess(() -> Component.translatable("commands.evaluate.reset", stack.getDisplayName()), true);
        return 1;
    }

    private static void refresh(MinecraftServer server) {
        ItemValues.all(server);
        server.getPlayerList().getPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, InvalidateValuesPayload.INSTANCE));
    }
}
