package com.iafenvoy.evaluate.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber
public final class ValueNetworking {
    private ValueNetworking() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional()
                .playToServer(ValueRequestPayload.TYPE, ValueRequestPayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player)
                        context.reply(payload.answer(player.level().getServer()));
                })
                .playToClient(ValueResponsePayload.TYPE, ValueResponsePayload.STREAM_CODEC)
                .playToClient(InvalidateValuesPayload.TYPE, InvalidateValuesPayload.STREAM_CODEC);
    }
}
