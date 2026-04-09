package de.quest.network;

import de.quest.VillageQuest;
import net.minecraft.item.ItemStack;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class Payloads {
    private static boolean registered = false;

    private Payloads() {}

    public static void register() {
        if (registered) {
            return;
        }
        PayloadTypeRegistry.playS2C().register(JournalPayload.ID, JournalPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AdminJournalPayload.ID, AdminJournalPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(QuestTrackerPayload.ID, QuestTrackerPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PilgrimTradePayload.ID, PilgrimTradePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(QuestMasterPayload.ID, QuestMasterPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(JournalActionPayload.ID, JournalActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PilgrimTradeActionPayload.ID, PilgrimTradeActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PilgrimTradeSessionPayload.ID, PilgrimTradeSessionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(QuestMasterActionPayload.ID, QuestMasterActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(QuestMasterSessionPayload.ID, QuestMasterSessionPayload.CODEC);
        registered = true;
    }

    public record JournalPayload(
            int action,
            int total,
            int discovered,
            int completed,
            int active,
            long currencyBalance,
            int farmingReputation,
            int craftingReputation,
            int animalReputation,
            int tradeReputation,
            int monsterReputation,
            boolean hasStarreachRing,
            boolean hasMerchantSeal,
            boolean hasShepherdFlute,
            boolean hasApiaristSmoker,
            boolean hasSurveyorCompass,
            boolean dailyActive,
            Text dailyTitle,
            Text dailyProgress,
            boolean weeklyActive,
            Text weeklyTitle,
            Text weeklyProgress,
            boolean storyActive,
            Text storyTitle,
            Text storyProgress,
            boolean pilgrimActive,
            Text pilgrimTitle,
            Text pilgrimProgress,
            boolean specialActive,
            Text specialTitle,
            Text specialProgress,
            boolean hasVillageLedgerProject,
            boolean hasApiaryCharterProject,
            boolean hasForgeCharterProject,
            boolean hasMarketCharterProject,
            boolean hasPastureCharterProject,
            boolean hasWatchBellProject
    ) implements CustomPayload {
        public static final int ACTION_OPEN = 0;
        public static final int ACTION_UPDATE = 1;
        public static final int ACTION_CLOSE = 2;

        public static final CustomPayload.Id<JournalPayload> ID = new CustomPayload.Id<>(Identifier.of(VillageQuest.MOD_ID, "journal"));
        public static final PacketCodec<RegistryByteBuf, JournalPayload> CODEC = PacketCodec.ofStatic(JournalPayload::write, JournalPayload::read);

        private static JournalPayload read(RegistryByteBuf buf) {
            int action = buf.readVarInt();
            int total = buf.readVarInt();
            int discovered = buf.readVarInt();
            int completed = buf.readVarInt();
            int active = buf.readVarInt();
            long currencyBalance = buf.readLong();
            int farmingReputation = buf.readVarInt();
            int craftingReputation = buf.readVarInt();
            int animalReputation = buf.readVarInt();
            int tradeReputation = buf.readVarInt();
            int monsterReputation = buf.readVarInt();
            boolean hasStarreachRing = buf.readBoolean();
            boolean hasMerchantSeal = buf.readBoolean();
            boolean hasShepherdFlute = buf.readBoolean();
            boolean hasApiaristSmoker = buf.readBoolean();
            boolean hasSurveyorCompass = buf.readBoolean();
            boolean dailyActive = buf.readBoolean();
            Text dailyTitle = TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf);
            Text dailyProgress = TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf);
            boolean weeklyActive = buf.readBoolean();
            Text weeklyTitle = TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf);
            Text weeklyProgress = TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf);
            boolean storyActive = buf.readBoolean();
            Text storyTitle = TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf);
            Text storyProgress = TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf);
            boolean pilgrimActive = buf.readBoolean();
            Text pilgrimTitle = TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf);
            Text pilgrimProgress = TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf);
            boolean specialActive = buf.readBoolean();
            Text specialTitle = TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf);
            Text specialProgress = TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf);
            boolean hasVillageLedgerProject = buf.readBoolean();
            boolean hasApiaryCharterProject = buf.readBoolean();
            boolean hasForgeCharterProject = buf.readBoolean();
            boolean hasMarketCharterProject = buf.readBoolean();
            boolean hasPastureCharterProject = buf.readBoolean();
            boolean hasWatchBellProject = buf.readBoolean();
            return new JournalPayload(
                    action,
                    total,
                    discovered,
                    completed,
                    active,
                    currencyBalance,
                    farmingReputation,
                    craftingReputation,
                    animalReputation,
                    tradeReputation,
                    monsterReputation,
                    hasStarreachRing,
                    hasMerchantSeal,
                    hasShepherdFlute,
                    hasApiaristSmoker,
                    hasSurveyorCompass,
                    dailyActive,
                    dailyTitle,
                    dailyProgress,
                    weeklyActive,
                    weeklyTitle,
                    weeklyProgress,
                    storyActive,
                    storyTitle,
                    storyProgress,
                    pilgrimActive,
                    pilgrimTitle,
                    pilgrimProgress,
                    specialActive,
                    specialTitle,
                    specialProgress,
                    hasVillageLedgerProject,
                    hasApiaryCharterProject,
                    hasForgeCharterProject,
                    hasMarketCharterProject,
                    hasPastureCharterProject,
                    hasWatchBellProject
            );
        }

        private static void write(RegistryByteBuf buf, JournalPayload payload) {
            buf.writeVarInt(payload.action());
            buf.writeVarInt(payload.total());
            buf.writeVarInt(payload.discovered());
            buf.writeVarInt(payload.completed());
            buf.writeVarInt(payload.active());
            buf.writeLong(payload.currencyBalance());
            buf.writeVarInt(payload.farmingReputation());
            buf.writeVarInt(payload.craftingReputation());
            buf.writeVarInt(payload.animalReputation());
            buf.writeVarInt(payload.tradeReputation());
            buf.writeVarInt(payload.monsterReputation());
            buf.writeBoolean(payload.hasStarreachRing());
            buf.writeBoolean(payload.hasMerchantSeal());
            buf.writeBoolean(payload.hasShepherdFlute());
            buf.writeBoolean(payload.hasApiaristSmoker());
            buf.writeBoolean(payload.hasSurveyorCompass());
            buf.writeBoolean(payload.dailyActive());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, payload.dailyTitle());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, payload.dailyProgress());
            buf.writeBoolean(payload.weeklyActive());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, payload.weeklyTitle());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, payload.weeklyProgress());
            buf.writeBoolean(payload.storyActive());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, payload.storyTitle());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, payload.storyProgress());
            buf.writeBoolean(payload.pilgrimActive());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, payload.pilgrimTitle());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, payload.pilgrimProgress());
            buf.writeBoolean(payload.specialActive());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, payload.specialTitle());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, payload.specialProgress());
            buf.writeBoolean(payload.hasVillageLedgerProject());
            buf.writeBoolean(payload.hasApiaryCharterProject());
            buf.writeBoolean(payload.hasForgeCharterProject());
            buf.writeBoolean(payload.hasMarketCharterProject());
            buf.writeBoolean(payload.hasPastureCharterProject());
            buf.writeBoolean(payload.hasWatchBellProject());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record JournalActionPayload(int action) implements CustomPayload {
        public static final int ACTION_CANCEL_DAILY = 0;
        public static final int ACTION_CANCEL_WEEKLY = 1;

        public static final CustomPayload.Id<JournalActionPayload> ID =
                new CustomPayload.Id<>(Identifier.of(VillageQuest.MOD_ID, "journal_action"));
        public static final PacketCodec<RegistryByteBuf, JournalActionPayload> CODEC =
                PacketCodec.ofStatic(JournalActionPayload::write, JournalActionPayload::read);

        private static JournalActionPayload read(RegistryByteBuf buf) {
            return new JournalActionPayload(buf.readVarInt());
        }

        private static void write(RegistryByteBuf buf, JournalActionPayload payload) {
            buf.writeVarInt(payload.action());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record AdminJournalPayload(List<String> lines) implements CustomPayload {
        public static final CustomPayload.Id<AdminJournalPayload> ID =
                new CustomPayload.Id<>(Identifier.of(VillageQuest.MOD_ID, "admin_journal"));
        public static final PacketCodec<RegistryByteBuf, AdminJournalPayload> CODEC =
                PacketCodec.ofStatic(AdminJournalPayload::write, AdminJournalPayload::read);

        private static AdminJournalPayload read(RegistryByteBuf buf) {
            int count = buf.readVarInt();
            List<String> lines = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                lines.add(buf.readString());
            }
            return new AdminJournalPayload(lines);
        }

        private static void write(RegistryByteBuf buf, AdminJournalPayload payload) {
            List<String> lines = payload.lines();
            buf.writeVarInt(lines.size());
            for (String line : lines) {
                buf.writeString(line);
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record QuestTrackerPayload(
            boolean enabled,
            boolean dailyActive,
            Text dailyTitle,
            List<Text> dailyLines,
            boolean weeklyActive,
            Text weeklyTitle,
            List<Text> weeklyLines,
            boolean storyActive,
            Text storyTitle,
            List<Text> storyLines,
            boolean pilgrimActive,
            Text pilgrimTitle,
            List<Text> pilgrimLines,
            boolean specialActive,
            Text specialTitle,
            List<Text> specialLines
    ) implements CustomPayload {
        public static final CustomPayload.Id<QuestTrackerPayload> ID =
                new CustomPayload.Id<>(Identifier.of(VillageQuest.MOD_ID, "quest_tracker"));
        public static final PacketCodec<RegistryByteBuf, QuestTrackerPayload> CODEC =
                PacketCodec.ofStatic(QuestTrackerPayload::write, QuestTrackerPayload::read);

        private static QuestTrackerPayload read(RegistryByteBuf buf) {
            boolean enabled = buf.readBoolean();
            boolean dailyActive = buf.readBoolean();
            Text dailyTitle = TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf);
            List<Text> dailyLines = readTextList(buf);
            boolean weeklyActive = buf.readBoolean();
            Text weeklyTitle = TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf);
            List<Text> weeklyLines = readTextList(buf);
            boolean storyActive = buf.readBoolean();
            Text storyTitle = TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf);
            List<Text> storyLines = readTextList(buf);
            boolean pilgrimActive = buf.readBoolean();
            Text pilgrimTitle = TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf);
            List<Text> pilgrimLines = readTextList(buf);
            boolean specialActive = buf.readBoolean();
            Text specialTitle = TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf);
            List<Text> specialLines = readTextList(buf);
            return new QuestTrackerPayload(enabled, dailyActive, dailyTitle, dailyLines, weeklyActive, weeklyTitle, weeklyLines, storyActive, storyTitle, storyLines, pilgrimActive, pilgrimTitle, pilgrimLines, specialActive, specialTitle, specialLines);
        }

        private static void write(RegistryByteBuf buf, QuestTrackerPayload payload) {
            buf.writeBoolean(payload.enabled());
            buf.writeBoolean(payload.dailyActive());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, payload.dailyTitle());
            writeTextList(buf, payload.dailyLines());
            buf.writeBoolean(payload.weeklyActive());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, payload.weeklyTitle());
            writeTextList(buf, payload.weeklyLines());
            buf.writeBoolean(payload.storyActive());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, payload.storyTitle());
            writeTextList(buf, payload.storyLines());
            buf.writeBoolean(payload.pilgrimActive());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, payload.pilgrimTitle());
            writeTextList(buf, payload.pilgrimLines());
            buf.writeBoolean(payload.specialActive());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, payload.specialTitle());
            writeTextList(buf, payload.specialLines());
        }

        private static List<Text> readTextList(RegistryByteBuf buf) {
            int count = buf.readVarInt();
            List<Text> lines = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                lines.add(TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf));
            }
            return lines;
        }

        private static void writeTextList(RegistryByteBuf buf, List<Text> lines) {
            buf.writeVarInt(lines.size());
            for (Text line : lines) {
                TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, line);
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record PilgrimTradeOfferData(
            String offerId,
            Text title,
            Text description,
            long price,
            ItemStack previewStack
    ) {
        private static PilgrimTradeOfferData read(RegistryByteBuf buf) {
            String offerId = buf.readString();
            Text title = TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf);
            Text description = TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf);
            long price = buf.readLong();
            ItemStack previewStack = ItemStack.PACKET_CODEC.decode(buf);
            return new PilgrimTradeOfferData(offerId, title, description, price, previewStack);
        }

        private static void write(RegistryByteBuf buf, PilgrimTradeOfferData offer) {
            buf.writeString(offer.offerId());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, offer.title());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, offer.description());
            buf.writeLong(offer.price());
            ItemStack.PACKET_CODEC.encode(buf, offer.previewStack());
        }
    }

    public record PilgrimContractData(
            String contractId,
            Text title,
            Text status,
            List<Text> descriptionLines,
            List<Text> objectiveLines,
            List<Text> rewardLines,
            Text actionLabel,
            boolean actionEnabled,
            ItemStack previewStack
    ) {
        private static PilgrimContractData read(RegistryByteBuf buf) {
            String contractId = buf.readString();
            Text title = TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf);
            Text status = TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf);
            List<Text> descriptionLines = QuestTrackerPayload.readTextList(buf);
            List<Text> objectiveLines = QuestTrackerPayload.readTextList(buf);
            List<Text> rewardLines = QuestTrackerPayload.readTextList(buf);
            Text actionLabel = TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf);
            boolean actionEnabled = buf.readBoolean();
            ItemStack previewStack = ItemStack.PACKET_CODEC.decode(buf);
            return new PilgrimContractData(contractId, title, status, descriptionLines, objectiveLines, rewardLines, actionLabel, actionEnabled, previewStack);
        }

        private static void write(RegistryByteBuf buf, PilgrimContractData payload) {
            buf.writeString(payload.contractId());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, payload.title());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, payload.status());
            QuestTrackerPayload.writeTextList(buf, payload.descriptionLines());
            QuestTrackerPayload.writeTextList(buf, payload.objectiveLines());
            QuestTrackerPayload.writeTextList(buf, payload.rewardLines());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, payload.actionLabel());
            buf.writeBoolean(payload.actionEnabled());
            ItemStack.PACKET_CODEC.encode(buf, payload.previewStack());
        }
    }

    public record PilgrimTradePayload(
            int action,
            int entityId,
            Text merchantName,
            long balance,
            int despawnTicks,
            List<PilgrimTradeOfferData> offers,
            List<PilgrimContractData> contracts
    ) implements CustomPayload {
        public static final int ACTION_OPEN = 0;
        public static final int ACTION_UPDATE = 1;
        public static final int ACTION_CLOSE = 2;

        public static final CustomPayload.Id<PilgrimTradePayload> ID =
                new CustomPayload.Id<>(Identifier.of(VillageQuest.MOD_ID, "pilgrim_trade"));
        public static final PacketCodec<RegistryByteBuf, PilgrimTradePayload> CODEC =
                PacketCodec.ofStatic(PilgrimTradePayload::write, PilgrimTradePayload::read);

        private static PilgrimTradePayload read(RegistryByteBuf buf) {
            int action = buf.readVarInt();
            int entityId = buf.readVarInt();
            Text merchantName = TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf);
            long balance = buf.readLong();
            int despawnTicks = buf.readVarInt();
            int count = buf.readVarInt();
            List<PilgrimTradeOfferData> offers = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                offers.add(PilgrimTradeOfferData.read(buf));
            }
            int contractCount = buf.readVarInt();
            List<PilgrimContractData> contracts = new ArrayList<>(contractCount);
            for (int i = 0; i < contractCount; i++) {
                contracts.add(PilgrimContractData.read(buf));
            }
            return new PilgrimTradePayload(action, entityId, merchantName, balance, despawnTicks, offers, contracts);
        }

        private static void write(RegistryByteBuf buf, PilgrimTradePayload payload) {
            buf.writeVarInt(payload.action());
            buf.writeVarInt(payload.entityId());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, payload.merchantName());
            buf.writeLong(payload.balance());
            buf.writeVarInt(payload.despawnTicks());
            buf.writeVarInt(payload.offers().size());
            for (PilgrimTradeOfferData offer : payload.offers()) {
                PilgrimTradeOfferData.write(buf, offer);
            }
            buf.writeVarInt(payload.contracts().size());
            for (PilgrimContractData contract : payload.contracts()) {
                PilgrimContractData.write(buf, contract);
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record PilgrimTradeActionPayload(int entityId, String offerId) implements CustomPayload {
        public static final CustomPayload.Id<PilgrimTradeActionPayload> ID =
                new CustomPayload.Id<>(Identifier.of(VillageQuest.MOD_ID, "pilgrim_trade_action"));
        public static final PacketCodec<RegistryByteBuf, PilgrimTradeActionPayload> CODEC =
                PacketCodec.ofStatic(PilgrimTradeActionPayload::write, PilgrimTradeActionPayload::read);

        private static PilgrimTradeActionPayload read(RegistryByteBuf buf) {
            return new PilgrimTradeActionPayload(buf.readVarInt(), buf.readString());
        }

        private static void write(RegistryByteBuf buf, PilgrimTradeActionPayload payload) {
            buf.writeVarInt(payload.entityId());
            buf.writeString(payload.offerId());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record PilgrimTradeSessionPayload(int entityId, int action) implements CustomPayload {
        public static final int ACTION_CLOSE = 0;

        public static final CustomPayload.Id<PilgrimTradeSessionPayload> ID =
                new CustomPayload.Id<>(Identifier.of(VillageQuest.MOD_ID, "pilgrim_trade_session"));
        public static final PacketCodec<RegistryByteBuf, PilgrimTradeSessionPayload> CODEC =
                PacketCodec.ofStatic(PilgrimTradeSessionPayload::write, PilgrimTradeSessionPayload::read);

        private static PilgrimTradeSessionPayload read(RegistryByteBuf buf) {
            return new PilgrimTradeSessionPayload(buf.readVarInt(), buf.readVarInt());
        }

        private static void write(RegistryByteBuf buf, PilgrimTradeSessionPayload payload) {
            buf.writeVarInt(payload.entityId());
            buf.writeVarInt(payload.action());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record QuestMasterCategoryData(
            String categoryId,
            Text label,
            int entryCount
    ) {
        private static QuestMasterCategoryData read(RegistryByteBuf buf) {
            return new QuestMasterCategoryData(
                    buf.readString(),
                    TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf),
                    buf.readVarInt()
            );
        }

        private static void write(RegistryByteBuf buf, QuestMasterCategoryData category) {
            buf.writeString(category.categoryId());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, category.label());
            buf.writeVarInt(category.entryCount());
        }
    }

    public record QuestMasterEntryData(
            String entryId,
            String categoryId,
            Text title,
            Text subtitle,
            Text status,
            List<Text> descriptionLines,
            List<Text> objectiveLines,
            List<Text> rewardLines,
            int primaryAction,
            Text primaryLabel,
            boolean primaryEnabled,
            int secondaryAction,
            Text secondaryLabel,
            boolean secondaryEnabled,
            boolean locked
    ) {
        private static QuestMasterEntryData read(RegistryByteBuf buf) {
            return new QuestMasterEntryData(
                    buf.readString(),
                    buf.readString(),
                    TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf),
                    TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf),
                    TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf),
                    readTextList(buf),
                    readTextList(buf),
                    readTextList(buf),
                    buf.readVarInt(),
                    TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf),
                    buf.readBoolean(),
                    buf.readVarInt(),
                    TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf),
                    buf.readBoolean(),
                    buf.readBoolean()
            );
        }

        private static void write(RegistryByteBuf buf, QuestMasterEntryData entry) {
            buf.writeString(entry.entryId());
            buf.writeString(entry.categoryId());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, entry.title());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, entry.subtitle());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, entry.status());
            writeTextList(buf, entry.descriptionLines());
            writeTextList(buf, entry.objectiveLines());
            writeTextList(buf, entry.rewardLines());
            buf.writeVarInt(entry.primaryAction());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, entry.primaryLabel());
            buf.writeBoolean(entry.primaryEnabled());
            buf.writeVarInt(entry.secondaryAction());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, entry.secondaryLabel());
            buf.writeBoolean(entry.secondaryEnabled());
            buf.writeBoolean(entry.locked());
        }
    }

    public record QuestMasterPayload(
            int action,
            int entityId,
            Text questMasterName,
            List<QuestMasterCategoryData> categories,
            List<QuestMasterEntryData> entries,
            long storyCooldownUntil
    ) implements CustomPayload {
        public static final int ACTION_OPEN = 0;
        public static final int ACTION_UPDATE = 1;
        public static final int ACTION_CLOSE = 2;

        public static final CustomPayload.Id<QuestMasterPayload> ID =
                new CustomPayload.Id<>(Identifier.of(VillageQuest.MOD_ID, "questmaster"));
        public static final PacketCodec<RegistryByteBuf, QuestMasterPayload> CODEC =
                PacketCodec.ofStatic(QuestMasterPayload::write, QuestMasterPayload::read);

        private static QuestMasterPayload read(RegistryByteBuf buf) {
            int action = buf.readVarInt();
            int entityId = buf.readVarInt();
            Text questMasterName = TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf);
            int categoryCount = buf.readVarInt();
            List<QuestMasterCategoryData> categories = new ArrayList<>(categoryCount);
            for (int i = 0; i < categoryCount; i++) {
                categories.add(QuestMasterCategoryData.read(buf));
            }
            int entryCount = buf.readVarInt();
            List<QuestMasterEntryData> entries = new ArrayList<>(entryCount);
            for (int i = 0; i < entryCount; i++) {
                entries.add(QuestMasterEntryData.read(buf));
            }
            long storyCooldownUntil = buf.readLong();
            return new QuestMasterPayload(action, entityId, questMasterName, categories, entries, storyCooldownUntil);
        }

        private static void write(RegistryByteBuf buf, QuestMasterPayload payload) {
            buf.writeVarInt(payload.action());
            buf.writeVarInt(payload.entityId());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, payload.questMasterName());
            buf.writeVarInt(payload.categories().size());
            for (QuestMasterCategoryData category : payload.categories()) {
                QuestMasterCategoryData.write(buf, category);
            }
            buf.writeVarInt(payload.entries().size());
            for (QuestMasterEntryData entry : payload.entries()) {
                QuestMasterEntryData.write(buf, entry);
            }
            buf.writeLong(payload.storyCooldownUntil());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record QuestMasterActionPayload(
            int entityId,
            int action,
            String entryId
    ) implements CustomPayload {
        public static final int ACTION_NONE = 0;
        public static final int ACTION_ACCEPT = 1;
        public static final int ACTION_CLAIM = 3;
        public static final int ACTION_CANCEL = 4;

        public static final CustomPayload.Id<QuestMasterActionPayload> ID =
                new CustomPayload.Id<>(Identifier.of(VillageQuest.MOD_ID, "questmaster_action"));
        public static final PacketCodec<RegistryByteBuf, QuestMasterActionPayload> CODEC =
                PacketCodec.ofStatic(QuestMasterActionPayload::write, QuestMasterActionPayload::read);

        private static QuestMasterActionPayload read(RegistryByteBuf buf) {
            return new QuestMasterActionPayload(buf.readVarInt(), buf.readVarInt(), buf.readString());
        }

        private static void write(RegistryByteBuf buf, QuestMasterActionPayload payload) {
            buf.writeVarInt(payload.entityId());
            buf.writeVarInt(payload.action());
            buf.writeString(payload.entryId());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record QuestMasterSessionPayload(int entityId, int action) implements CustomPayload {
        public static final int ACTION_CLOSE = 0;

        public static final CustomPayload.Id<QuestMasterSessionPayload> ID =
                new CustomPayload.Id<>(Identifier.of(VillageQuest.MOD_ID, "questmaster_session"));
        public static final PacketCodec<RegistryByteBuf, QuestMasterSessionPayload> CODEC =
                PacketCodec.ofStatic(QuestMasterSessionPayload::write, QuestMasterSessionPayload::read);

        private static QuestMasterSessionPayload read(RegistryByteBuf buf) {
            return new QuestMasterSessionPayload(buf.readVarInt(), buf.readVarInt());
        }

        private static void write(RegistryByteBuf buf, QuestMasterSessionPayload payload) {
            buf.writeVarInt(payload.entityId());
            buf.writeVarInt(payload.action());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private static List<Text> readTextList(RegistryByteBuf buf) {
        int count = buf.readVarInt();
        List<Text> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            lines.add(TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf));
        }
        return lines;
    }

    private static void writeTextList(RegistryByteBuf buf, List<Text> lines) {
        buf.writeVarInt(lines.size());
        for (Text line : lines) {
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, line);
        }
    }
}
