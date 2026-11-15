package com.iafenvoy.evaluate.network.payload;

import com.iafenvoy.evaluate.Evaluate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Zero represents an unknown or free item, matching the public query API.
 */
public record ValueResponsePayload(int requestId, double value) implements CustomPacketPayload {
    public static final Type<ValueResponsePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Evaluate.MOD_ID, "value_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ValueResponsePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ValueResponsePayload::requestId,
            ByteBufCodecs.DOUBLE, ValueResponsePayload::value,
            ValueResponsePayload::new);

    public ValueResponsePayload {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid item value: " + value);
    }

    @Override
    public @NonNull Type<ValueResponsePayload> type() {
        return TYPE;
    }
}
