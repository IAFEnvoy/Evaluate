package com.iafenvoy.evaluate;

import com.iafenvoy.evaluate.api.ItemValues;
import com.iafenvoy.evaluate.compat.FtbQuestsCompat;
import com.iafenvoy.evaluate.data.ValueReloadListener;
import com.iafenvoy.evaluate.network.payload.InvalidateValuesPayload;
import com.iafenvoy.evaluate.registry.EvaluateEvaluators;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.resource.VanillaServerListeners;
import org.slf4j.Logger;

@Mod(Evaluate.MOD_ID)
@EventBusSubscriber
public class Evaluate {
    public static final String MOD_ID = "evaluate";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Evaluate(IEventBus bus) {
        EvaluateEvaluators.REGISTRY.register(bus);
        // FTB Quests loads its server quest file at normal priority in this same event.
        if (ModList.get().isLoaded("ftbquests")) FtbQuestsCompat.init();
    }

    @SubscribeEvent
    public static void addReloadListeners(AddServerReloadListenersEvent event) {
        event.addRetainedListener(ValueReloadListener.KEY, new ValueReloadListener(event.getServerResources()));
        event.addDependency(VanillaServerListeners.RECIPES, ValueReloadListener.KEY.getListenerId());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverStarted(ServerStartedEvent event) {
        ItemValues.all(event.getServer());
    }

    @SubscribeEvent
    public static void datapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) ItemValues.all(event.getPlayerList().getServer());
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, InvalidateValuesPayload.INSTANCE));
    }
}
