package de.quest.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Carries only a built-in quest or chapter event to the player who performed it. */
public record GameplayStatsEvent(String metric) implements CustomPacketPayload {
    public static final Type<GameplayStatsEvent> ID = new Type<>(Identifier.fromNamespaceAndPath("village-quest", "stats_event"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GameplayStatsEvent> CODEC = StreamCodec.of(
            (buf, event) -> buf.writeUtf(event.metric(), 110),
            buf -> new GameplayStatsEvent(buf.readUtf(110)));

    public static boolean valid(String metric) {
        return GameplayStatsMetrics.valid(metric);
    }

    public static void send(ServerPlayer player, String category, String action, String key) {
        if (player != null && player.connection != null && ServerPlayNetworking.canSend(player, ID)) {
            String metric = category + "." + action + "." + key.toLowerCase(java.util.Locale.ROOT);
            if (valid(metric) || (action.equals("paused") || action.equals("resumed")) && GameplayStatsMetrics.content(category+"."+key))
                ServerPlayNetworking.send(player, new GameplayStatsEvent(metric));
        }
    }

    @Override
    public Type<GameplayStatsEvent> type() {
        return ID;
    }
}
