package com.iafenvoy.evaluate.evaluator;

import com.iafenvoy.evaluate.api.EvaluationContext;
import com.iafenvoy.evaluate.api.Evaluator;
import com.iafenvoy.evaluate.data.DefinitionCodecs;
import com.iafenvoy.evaluate.data.ItemSelection;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.iafenvoy.saturn.util.matcher.ItemMatcher;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * One datapack file assigns one fixed value to all stacks selected by its Saturn matcher.
 */
public record ConstantEvaluator(ItemSelection item, int priority, double value) implements Evaluator, ItemMatcher {
    public static final MapCodec<ConstantEvaluator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemSelection.CODEC.fieldOf("item").forGetter(ConstantEvaluator::item),
            DefinitionCodecs.SIGNED_INTEGER.optionalFieldOf("priority", DEFAULT_PRIORITY).forGetter(ConstantEvaluator::priority),
            MiscCodecs.NON_NEGATIVE_DOUBLE.fieldOf("value").forGetter(ConstantEvaluator::value)
    ).apply(i, ConstantEvaluator::new));

    @Override
    public void evaluate(EvaluationContext context) {
        this.item.collectSamples(context::item);
        context.setValue(this, this.value);
    }

    @Override
    public MapCodec<ConstantEvaluator> codec() {
        return CODEC;
    }

    @Override
    public List<Entry> entries() {
        return this.item.entries();
    }
}
