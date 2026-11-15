package com.iafenvoy.evaluate.registry;

import com.iafenvoy.evaluate.Evaluate;
import com.iafenvoy.evaluate.api.Evaluator;
import com.iafenvoy.evaluate.evaluator.ConstantEvaluator;
import com.iafenvoy.evaluate.evaluator.FtbQuestsEvaluator;
import com.iafenvoy.evaluate.evaluator.JsEvaluator;
import com.iafenvoy.evaluate.evaluator.RecipeEvaluator;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class EvaluateEvaluators {
    public static final DeferredRegister<MapCodec<? extends Evaluator>> REGISTRY = DeferredRegister.create(EvaluateRegistries.EVALUATOR_TYPE, Evaluate.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends Evaluator>, MapCodec<ConstantEvaluator>> CONSTANT = REGISTRY.register("constant", () -> ConstantEvaluator.CODEC);
    public static final DeferredHolder<MapCodec<? extends Evaluator>, MapCodec<RecipeEvaluator>> RECIPE = REGISTRY.register("recipe", () -> RecipeEvaluator.CODEC);
    public static final DeferredHolder<MapCodec<? extends Evaluator>, MapCodec<JsEvaluator>> JS = REGISTRY.register("js", () -> JsEvaluator.CODEC);
    public static final DeferredHolder<MapCodec<? extends Evaluator>, MapCodec<FtbQuestsEvaluator>> FTB_QUESTS = REGISTRY.register("ftb_quests", () -> FtbQuestsEvaluator.CODEC);
}
