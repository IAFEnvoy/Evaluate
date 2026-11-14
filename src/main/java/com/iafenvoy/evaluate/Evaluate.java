package com.iafenvoy.evaluate;

import com.iafenvoy.evaluate.api.ItemValues;
import com.iafenvoy.evaluate.compat.FtbQuestsCompat;
import com.iafenvoy.evaluate.data.ValueReloadListener;
import com.iafenvoy.evaluate.network.InvalidateValuesPayload;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.resource.VanillaServerListeners;
import org.slf4j.Logger;

@Mod(Evaluate.MOD_ID)
public class Evaluate {
    public static final String MOD_ID = "evaluate";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Evaluate() {
        NeoForge.EVENT_BUS.addListener(Evaluate::addReloadListeners);
        // FTB Quests loads its server quest file at normal priority in this same event.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, Evaluate::serverStarted);
        NeoForge.EVENT_BUS.addListener(Evaluate::datapackSync);
        if (ModList.get().isLoaded("ftbquests")) FtbQuestsCompat.init();
    }

    private static void addReloadListeners(AddServerReloadListenersEvent event) {
        event.addRetainedListener(ValueReloadListener.KEY, new ValueReloadListener(event.getServerResources()));
        event.addDependency(VanillaServerListeners.RECIPES, ValueReloadListener.KEY.getListenerId());
    }

    private static void serverStarted(ServerStartedEvent event) {
        ItemValues.all(event.getServer());
    }

    private static void datapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) ItemValues.all(event.getPlayerList().getServer());
        event.getRelevantPlayers().filter(player -> player.connection.hasChannel(InvalidateValuesPayload.TYPE))
                .forEach(player -> PacketDistributor.sendToPlayer(player, InvalidateValuesPayload.INSTANCE));
    }
}
