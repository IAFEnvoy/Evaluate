package com.iafenvoy.evaluate.evaluator;

import com.iafenvoy.evaluate.api.EvaluationContext;
import com.iafenvoy.evaluate.api.Evaluator;
import com.iafenvoy.evaluate.compat.FtbQuestsCompat;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.fml.ModList;

/**
 * Fixed item exchanges contribute acquisition costs, in the direction of the reward only.
 */
public record FtbQuestsEvaluator(boolean repeatableOnly, double extraValue) implements Evaluator {
    public static final MapCodec<FtbQuestsEvaluator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.optionalFieldOf("repeatable_only", true).forGetter(FtbQuestsEvaluator::repeatableOnly),
            MiscCodecs.NON_NEGATIVE_DOUBLE.optionalFieldOf("extra_value", 0.0).forGetter(FtbQuestsEvaluator::extraValue)
    ).apply(i, FtbQuestsEvaluator::new));

    @Override
    public void evaluate(EvaluationContext context) {
        if (ModList.get().isLoaded("ftbquests")) FtbQuestsCompat.evaluate(context, this);
    }

    @Override
    public MapCodec<FtbQuestsEvaluator> codec() {
        return CODEC;
    }
}
