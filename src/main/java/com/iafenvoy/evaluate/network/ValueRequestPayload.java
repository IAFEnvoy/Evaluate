package com.iafenvoy.evaluate.network;

import com.iafenvoy.evaluate.Evaluate;
import com.iafenvoy.evaluate.api.ItemValues;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

public record ValueRequestPayload(int requestId, ItemStack stack) implements CustomPacketPayload {
    public static final Type<ValueRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Evaluate.MOD_ID, "value_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ValueRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ValueRequestPayload::requestId,
            ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC, ValueRequestPayload::stack,
            ValueRequestPayload::new);

    public ValueRequestPayload {
        stack = stack.copyWithCount(1);
    }

    public ValueResponsePayload answer(MinecraftServer server) {
        return new ValueResponsePayload(this.requestId, ItemValues.get(server, this.stack).orElse(0));
    }

    @Override
    public @NonNull Type<ValueRequestPayload> type() {
        return TYPE;
    }
}
