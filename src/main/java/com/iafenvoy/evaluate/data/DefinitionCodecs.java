package com.iafenvoy.evaluate.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class DefinitionCodecs {
    private DefinitionCodecs() {
    }

    public static final Codec<JsonObject> JSON_OBJECT = Codec.PASSTHROUGH.comapFlatMap(dynamic -> {
        JsonElement json = dynamic.convert(JsonOps.INSTANCE).getValue();
        return json.isJsonObject() ? DataResult.success(json.getAsJsonObject().deepCopy())
                : DataResult.error(() -> "params must be a JSON object");
    }, value -> new Dynamic<>(JsonOps.INSTANCE, value.deepCopy()));
    // Codec.INT would truncate fractional JSON numbers. Priorities must remain integers.
    public static final Codec<Integer> SIGNED_INTEGER = Codec.PASSTHROUGH.comapFlatMap(dynamic -> {
        try {
            return DataResult.success(new BigDecimal(dynamic.asNumber().getOrThrow().toString()).intValueExact());
        } catch (RuntimeException e) {
            return DataResult.error(() -> "Expected an integer");
        }
    }, value -> new Dynamic<>(JsonOps.INSTANCE, new JsonPrimitive(value)));

    /**
     * Preserve codec-defined extension fields while rejecting misspellings and removed fields.
     */
    public static <T> DataResult<T> validateFields(DynamicOps<T> ops, T input, MapCodec<?> codec, String... extraFields) {
        Set<String> fields = new HashSet<>(Set.of(extraFields));
        codec.keys(ops).forEach(key -> fields.add(ops.getStringValue(key).getOrThrow()));
        return ops.getMap(input).flatMap(map -> {
            Optional<String> unknown = map.entries().map(entry -> ops.getStringValue(entry.getFirst()).getOrThrow())
                    .filter(field -> !fields.contains(field)).findFirst();
            return unknown.<DataResult<T>>map(field -> DataResult.error(() -> "Unknown field: " + field)).orElseGet(() -> DataResult.success(input));
        });
    }

    public static <A> Codec<A> strict(MapCodec<A> codec) {
        return strict(codec.codec(), value -> codec, "comment");
    }

    public static <A> Codec<A> strict(Codec<A> codec, Function<A, MapCodec<? extends A>> fields, String... extraFields) {
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                return codec.decode(ops, input).flatMap(pair -> validateFields(ops, input, fields.apply(pair.getFirst()), extraFields).map(value -> pair));
            }

            @Override
            public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
                return codec.encode(input, ops, prefix);
            }
        };
    }
}
