package com.iafenvoy.evaluate.network.payload;

import com.iafenvoy.evaluate.Evaluate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public enum InvalidateValuesPayload implements CustomPacketPayload {
    INSTANCE;
    public static final Type<InvalidateValuesPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Evaluate.MOD_ID, "invalidate_values"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InvalidateValuesPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public @NonNull Type<InvalidateValuesPayload> type() {
        return TYPE;
    }
}
