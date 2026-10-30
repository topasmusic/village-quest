package de.quest.network;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Check before any Overhaul payload; old codecs must never decode a new shape. */
public final class ProgressionProtocol {
    private static final Set<UUID> REJECTED=ConcurrentHashMap.newKeySet();
    private ProgressionProtocol() {}
    public static boolean require(ServerPlayer player) {
        if(player==null) return false;
        if(ServerPlayNetworking.canSend(player,ExactVersionPayload.ID) && compatible(ServerPlayNetworking.canSend(player,ReputationPayloads.ViewPayload.ID),
                ServerPlayNetworking.canSend(player,ReputationPayloads.InteractionPayload.ID),ServerPlayNetworking.canSend(player,Payloads.JournalPayload.ID),
                ServerPlayNetworking.canSend(player,Payloads.QuestTrackerPayload.ID),
                ServerPlayNetworking.canSend(player,VillageNetworkPayloads.NoticeBoardPayload.ID),
                ServerPlayNetworking.canSend(player,VillageNetworkPayloads.NoticeJourneyPayload.ID))) return true;
        if(REJECTED.add(player.getUUID()) && player.connection!=null)
            player.connection.disconnect(ExactVersionPayload.mismatch());
        return false;
    }
    public static boolean compatible(boolean reputation, boolean interaction, boolean journal, boolean tracker, boolean board, boolean journey) {
        return reputation && interaction && compatible(journal,tracker,board,journey);
    }
    public static boolean compatible(boolean journal,boolean tracker,boolean board,boolean journey) {
        return journal && tracker && board && journey;
    }
    public static void disconnected(UUID player) { REJECTED.remove(player); }
    public static void stopped() { REJECTED.clear(); }
}
