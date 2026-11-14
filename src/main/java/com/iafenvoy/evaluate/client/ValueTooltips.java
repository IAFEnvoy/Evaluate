package com.iafenvoy.evaluate.client;

import com.iafenvoy.evaluate.api.ValueKey;
import com.iafenvoy.evaluate.network.InvalidateValuesPayload;
import com.iafenvoy.evaluate.network.ValueRequestPayload;
import com.iafenvoy.evaluate.network.ValueResponsePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(Dist.CLIENT)
public final class ValueTooltips {
    private static final ValueQueryCache<ValueKey.Item> VALUES = new ValueQueryCache<>();

    private ValueTooltips() {
    }

    @SubscribeEvent
    public static void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(ValueResponsePayload.TYPE, (payload, context) -> VALUES.receive(payload.requestId(), payload.value()));
        event.register(InvalidateValuesPayload.TYPE, (payload, context) -> VALUES.clear());
    }

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        if (!event.getFlags().isAdvanced() || event.getEntity() == null || !event.getEntity().level().isClientSide()
                || event.getItemStack().isEmpty()) return;
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        ItemStack stack = event.getItemStack();
        VALUES.get(ValueKey.Item.of(stack), requestId -> ClientPacketDistributor.sendToServer(new ValueRequestPayload(requestId, stack)))
                .ifPresent(value -> event.getToolTip().add(Component.translatable("tooltip.evaluate.value", String.format("%.2f", value))
                        .withStyle(ChatFormatting.DARK_GRAY)));
    }

    @SubscribeEvent
    public static void login(ClientPlayerNetworkEvent.LoggingIn event) {
        VALUES.clear();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        VALUES.clear();
    }
}
