package com.iafenvoy.evaluate.registry;

import com.iafenvoy.evaluate.api.Evaluator;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

@EventBusSubscriber
public final class EvaluateRegistries {
    public static final Registry<MapCodec<? extends Evaluator>> EVALUATOR_TYPE = new RegistryBuilder<>(EvaluateResourceKeys.EVALUATOR_TYPE).create();

    @SubscribeEvent
    public static void registries(NewRegistryEvent event) {
        event.register(EVALUATOR_TYPE);
    }
}
