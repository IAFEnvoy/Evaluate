package com.iafenvoy.evaluate.registry;

import com.iafenvoy.evaluate.api.Evaluator;
import com.iafenvoy.evaluate.api.ValueOverride;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

@EventBusSubscriber
public final class EvaluateDatapackRegistries {
    @SubscribeEvent
    public static void datapackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(EvaluateResourceKeys.EVALUATOR, Evaluator.CODEC);
        event.dataPackRegistry(EvaluateResourceKeys.VALUE_OVERRIDE, ValueOverride.CODEC);
    }
}
