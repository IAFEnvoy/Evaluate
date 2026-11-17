package com.iafenvoy.evaluate.compat.saturn.condition;

import com.iafenvoy.evaluate.api.ItemValues;
import com.iafenvoy.evaluate.compat.saturn.ValueContexts;
import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.condition.ItemCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.iafenvoy.saturn.util.EntitySlots;
import com.iafenvoy.saturn.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.List;

public record InventoryValueCondition(ItemCondition itemCondition, List<Integer> slot,
                                      Comparison comparison) implements EntityCondition {
    public static final MapCodec<InventoryValueCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemCondition.optionalCodec("item_condition").forGetter(InventoryValueCondition::itemCondition),
            EntitySlots.CODEC.optionalFieldOf("slot", List.of()).forGetter(InventoryValueCondition::slot),
            Comparison.CODEC.forGetter(InventoryValueCondition::comparison)
    ).apply(i, InventoryValueCondition::new));

    public InventoryValueCondition {
        slot = List.copyOf(slot);
    }

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        MinecraftServer server = ValueContexts.server(ctx.entity());
        if (server == null) return false;
        double total = 0;
        for (SlotAccess access : EntitySlots.select(ctx.entity(), this.slot)) {
            ItemStack stack = access.get();
            if (!stack.isEmpty() && this.itemCondition.test(ctx.entity(), stack, ctx))
                total = Math.fma(ItemValues.get(server, stack).orElse(0), stack.getCount(), total);
        }
        return Double.isFinite(total) && this.comparison.compare(total);
    }

    @Override
    public @NonNull MapCodec<InventoryValueCondition> codec() {
        return CODEC;
    }
}
