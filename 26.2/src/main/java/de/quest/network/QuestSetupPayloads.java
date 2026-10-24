package de.quest.network;

import de.quest.VillageQuest;
import de.quest.config.VillageQuestServerConfig;
import de.quest.config.WorldQuestSettings;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class QuestSetupPayloads {
    private QuestSetupPayloads() {}
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> id(String name) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, name));
    }
    private static WorldQuestSettings readSettings(RegistryFriendlyByteBuf buf) {
        return new WorldQuestSettings(buf.readBoolean(), buf.readEnum(VillageQuestServerConfig.AdventureProfile.class),
                buf.readBoolean(), buf.readEnum(VillageQuestServerConfig.CaravanVisualMode.class));
    }
    private static void writeSettings(RegistryFriendlyByteBuf buf, WorldQuestSettings settings) {
        buf.writeBoolean(settings.minecraftCalendar()); buf.writeEnum(settings.profile());
        buf.writeBoolean(settings.storyCooldown()); buf.writeEnum(settings.caravans());
    }
    public record Open() implements CustomPacketPayload {
        public static final Type<Open> ID = id("setup_open");
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.of((buf, value) -> {}, buf -> new Open());
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public record View(UUID session, boolean firstRun, boolean editable, WorldQuestSettings settings,
                       boolean tracker, boolean minimap, int status) implements CustomPacketPayload {
        public static final Type<View> ID = id("setup_view");
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeUUID(value.session); buf.writeBoolean(value.firstRun); buf.writeBoolean(value.editable);
            writeSettings(buf, value.settings); buf.writeBoolean(value.tracker); buf.writeBoolean(value.minimap); buf.writeVarInt(value.status);
        }, buf -> new View(buf.readUUID(), buf.readBoolean(), buf.readBoolean(), readSettings(buf),
                buf.readBoolean(), buf.readBoolean(), buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public record Save(UUID session, boolean save, boolean changeWorld, WorldQuestSettings settings,
                       boolean tracker, boolean minimap) implements CustomPacketPayload {
        public static final Type<Save> ID = id("setup_save");
        public static final StreamCodec<RegistryFriendlyByteBuf, Save> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeUUID(value.session); buf.writeBoolean(value.save); buf.writeBoolean(value.changeWorld);
            writeSettings(buf, value.settings); buf.writeBoolean(value.tracker); buf.writeBoolean(value.minimap);
        }, buf -> new Save(buf.readUUID(), buf.readBoolean(), buf.readBoolean(), readSettings(buf), buf.readBoolean(), buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
}
