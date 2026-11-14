package com.iafenvoy.evaluate.registry;

import com.iafenvoy.evaluate.Evaluate;
import com.iafenvoy.evaluate.api.Evaluator;
import com.iafenvoy.evaluate.api.ValueOverride;
import com.iafenvoy.evaluate.evaluator.ConstantEvaluator;
import com.iafenvoy.evaluate.evaluator.FtbQuestsEvaluator;
import com.iafenvoy.evaluate.evaluator.JsEvaluator;
import com.iafenvoy.evaluate.evaluator.RecipeEvaluator;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

@EventBusSubscriber
public final class EvaluateRegistries {
    public static final Registry<MapCodec<? extends Evaluator>> EVALUATOR_TYPE = new RegistryBuilder<>(EvaluateResourceKeys.EVALUATOR_TYPE).create();

    private EvaluateRegistries() {
    }

    @SubscribeEvent
    public static void registries(NewRegistryEvent event) {
        event.register(EVALUATOR_TYPE);
    }

    @SubscribeEvent
    public static void evaluatorTypes(RegisterEvent event) {
        event.register(EvaluateResourceKeys.EVALUATOR_TYPE, helper -> {
            helper.register(Identifier.fromNamespaceAndPath(Evaluate.MOD_ID, "constant"), ConstantEvaluator.CODEC);
            helper.register(Identifier.fromNamespaceAndPath(Evaluate.MOD_ID, "recipe"), RecipeEvaluator.CODEC);
            helper.register(Identifier.fromNamespaceAndPath(Evaluate.MOD_ID, "js"), JsEvaluator.CODEC);
            helper.register(Identifier.fromNamespaceAndPath(Evaluate.MOD_ID, "ftb_quests"), FtbQuestsEvaluator.CODEC);
        });
    }

    @SubscribeEvent
    public static void datapackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(EvaluateResourceKeys.EVALUATOR, Evaluator.CODEC);
        event.dataPackRegistry(EvaluateResourceKeys.VALUE_OVERRIDE, ValueOverride.CODEC);
    }
}
