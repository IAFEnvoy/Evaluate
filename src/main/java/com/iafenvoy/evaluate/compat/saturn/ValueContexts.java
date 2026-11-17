package com.iafenvoy.evaluate.compat.saturn;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public final class ValueContexts {
    private ValueContexts() {
    }

    public static @Nullable MinecraftServer server(@Nullable Entity holder) {
        return holder != null && holder.level() instanceof ServerLevel level ? level.getServer() : null;
    }
}
