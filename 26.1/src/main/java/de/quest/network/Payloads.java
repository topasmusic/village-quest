package de.quest.network;

import de.quest.VillageQuest;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

public final class Payloads {
    private static boolean registered = false;

    private Payloads() {}

    public static void register() {
        if (registered) {
            return;
        }
        PayloadTypeRegistry.clientboundPlay().register(JournalPayload.ID, JournalPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AdminJournalPayload.ID, AdminJournalPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(QuestTrackerPayload.ID, QuestTrackerPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PilgrimTradePayload.ID, PilgrimTradePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(QuestMasterPayload.ID, QuestMasterPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(JournalActionPayload.ID, JournalActionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PilgrimTradeActionPayload.ID, PilgrimTradeActionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PilgrimTradeSessionPayload.ID, PilgrimTradeSessionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(QuestMasterActionPayload.ID, QuestMasterActionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(QuestMasterSessionPayload.ID, QuestMasterSessionPayload.CODEC);
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
            Component dailyTitle,
            Component dailyProgress,
            boolean weeklyActive,
            Component weeklyTitle,
            Component weeklyProgress,
            boolean storyActive,
            Component storyTitle,
            Component storyProgress,
            boolean pilgrimActive,
            Component pilgrimTitle,
            Component pilgrimProgress,
            boolean specialActive,
            Component specialTitle,
            Component specialProgress,
            boolean hasVillageLedgerProject,
            boolean hasApiaryCharterProject,
            boolean hasForgeCharterProject,
            boolean hasMarketCharterProject,
            boolean hasPastureCharterProject,
            boolean hasWatchBellProject
    ) implements CustomPacketPayload {
        public static final int ACTION_OPEN = 0;
        public static final int ACTION_UPDATE = 1;
        public static final int ACTION_CLOSE = 2;

        public static final CustomPacketPayload.Type<JournalPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "journal"));
        public static final StreamCodec<RegistryFriendlyByteBuf, JournalPayload> CODEC = StreamCodec.of(JournalPayload::write, JournalPayload::read);

        private static JournalPayload read(RegistryFriendlyByteBuf buf) {
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
            Component dailyTitle = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            Component dailyProgress = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            boolean weeklyActive = buf.readBoolean();
            Component weeklyTitle = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            Component weeklyProgress = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            boolean storyActive = buf.readBoolean();
            Component storyTitle = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            Component storyProgress = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            boolean pilgrimActive = buf.readBoolean();
            Component pilgrimTitle = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            Component pilgrimProgress = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            boolean specialActive = buf.readBoolean();
            Component specialTitle = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            Component specialProgress = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
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

        private static void write(RegistryFriendlyByteBuf buf, JournalPayload payload) {
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
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.dailyTitle());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.dailyProgress());
            buf.writeBoolean(payload.weeklyActive());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.weeklyTitle());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.weeklyProgress());
            buf.writeBoolean(payload.storyActive());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.storyTitle());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.storyProgress());
            buf.writeBoolean(payload.pilgrimActive());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.pilgrimTitle());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.pilgrimProgress());
            buf.writeBoolean(payload.specialActive());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.specialTitle());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.specialProgress());
            buf.writeBoolean(payload.hasVillageLedgerProject());
            buf.writeBoolean(payload.hasApiaryCharterProject());
            buf.writeBoolean(payload.hasForgeCharterProject());
            buf.writeBoolean(payload.hasMarketCharterProject());
            buf.writeBoolean(payload.hasPastureCharterProject());
            buf.writeBoolean(payload.hasWatchBellProject());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record JournalActionPayload(int action) implements CustomPacketPayload {
        public static final int ACTION_CANCEL_DAILY = 0;
        public static final int ACTION_CANCEL_WEEKLY = 1;

        public static final CustomPacketPayload.Type<JournalActionPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "journal_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, JournalActionPayload> CODEC =
                StreamCodec.of(JournalActionPayload::write, JournalActionPayload::read);

        private static JournalActionPayload read(RegistryFriendlyByteBuf buf) {
            return new JournalActionPayload(buf.readVarInt());
        }

        private static void write(RegistryFriendlyByteBuf buf, JournalActionPayload payload) {
            buf.writeVarInt(payload.action());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record AdminJournalPayload(List<String> lines) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<AdminJournalPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "admin_journal"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AdminJournalPayload> CODEC =
                StreamCodec.of(AdminJournalPayload::write, AdminJournalPayload::read);

        private static AdminJournalPayload read(RegistryFriendlyByteBuf buf) {
            int count = buf.readVarInt();
            List<String> lines = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                lines.add(buf.readUtf());
            }
            return new AdminJournalPayload(lines);
        }

        private static void write(RegistryFriendlyByteBuf buf, AdminJournalPayload payload) {
            List<String> lines = payload.lines();
            buf.writeVarInt(lines.size());
            for (String line : lines) {
                buf.writeUtf(line);
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record QuestTrackerPayload(
            boolean enabled,
            boolean dailyActive,
            Component dailyTitle,
            List<Component> dailyLines,
            boolean weeklyActive,
            Component weeklyTitle,
            List<Component> weeklyLines,
            boolean storyActive,
            Component storyTitle,
            List<Component> storyLines,
            boolean pilgrimActive,
            Component pilgrimTitle,
            List<Component> pilgrimLines,
            boolean specialActive,
            Component specialTitle,
            List<Component> specialLines
    ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<QuestTrackerPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "quest_tracker"));
        public static final StreamCodec<RegistryFriendlyByteBuf, QuestTrackerPayload> CODEC =
                StreamCodec.of(QuestTrackerPayload::write, QuestTrackerPayload::read);

        private static QuestTrackerPayload read(RegistryFriendlyByteBuf buf) {
            boolean enabled = buf.readBoolean();
            boolean dailyActive = buf.readBoolean();
            Component dailyTitle = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            List<Component> dailyLines = readTextList(buf);
            boolean weeklyActive = buf.readBoolean();
            Component weeklyTitle = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            List<Component> weeklyLines = readTextList(buf);
            boolean storyActive = buf.readBoolean();
            Component storyTitle = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            List<Component> storyLines = readTextList(buf);
            boolean pilgrimActive = buf.readBoolean();
            Component pilgrimTitle = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            List<Component> pilgrimLines = readTextList(buf);
            boolean specialActive = buf.readBoolean();
            Component specialTitle = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            List<Component> specialLines = readTextList(buf);
            return new QuestTrackerPayload(enabled, dailyActive, dailyTitle, dailyLines, weeklyActive, weeklyTitle, weeklyLines, storyActive, storyTitle, storyLines, pilgrimActive, pilgrimTitle, pilgrimLines, specialActive, specialTitle, specialLines);
        }

        private static void write(RegistryFriendlyByteBuf buf, QuestTrackerPayload payload) {
            buf.writeBoolean(payload.enabled());
            buf.writeBoolean(payload.dailyActive());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.dailyTitle());
            writeTextList(buf, payload.dailyLines());
            buf.writeBoolean(payload.weeklyActive());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.weeklyTitle());
            writeTextList(buf, payload.weeklyLines());
            buf.writeBoolean(payload.storyActive());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.storyTitle());
            writeTextList(buf, payload.storyLines());
            buf.writeBoolean(payload.pilgrimActive());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.pilgrimTitle());
            writeTextList(buf, payload.pilgrimLines());
            buf.writeBoolean(payload.specialActive());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.specialTitle());
            writeTextList(buf, payload.specialLines());
        }

        private static List<Component> readTextList(RegistryFriendlyByteBuf buf) {
            int count = buf.readVarInt();
            List<Component> lines = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                lines.add(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf));
            }
            return lines;
        }

        private static void writeTextList(RegistryFriendlyByteBuf buf, List<Component> lines) {
            buf.writeVarInt(lines.size());
            for (Component line : lines) {
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, line);
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record PilgrimTradeOfferData(
            String offerId,
            Component title,
            Component description,
            long price,
            ItemStack previewStack
    ) {
        private static PilgrimTradeOfferData read(RegistryFriendlyByteBuf buf) {
            String offerId = buf.readUtf();
            Component title = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            Component description = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            long price = buf.readLong();
            ItemStack previewStack = ItemStack.STREAM_CODEC.decode(buf);
            return new PilgrimTradeOfferData(offerId, title, description, price, previewStack);
        }

        private static void write(RegistryFriendlyByteBuf buf, PilgrimTradeOfferData offer) {
            buf.writeUtf(offer.offerId());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, offer.title());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, offer.description());
            buf.writeLong(offer.price());
            ItemStack.STREAM_CODEC.encode(buf, offer.previewStack());
        }
    }

    public record PilgrimContractData(
            String contractId,
            Component title,
            Component status,
            List<Component> descriptionLines,
            List<Component> objectiveLines,
            List<Component> rewardLines,
            Component actionLabel,
            boolean actionEnabled,
            ItemStack previewStack
    ) {
        private static PilgrimContractData read(RegistryFriendlyByteBuf buf) {
            String contractId = buf.readUtf();
            Component title = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            Component status = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            List<Component> descriptionLines = QuestTrackerPayload.readTextList(buf);
            List<Component> objectiveLines = QuestTrackerPayload.readTextList(buf);
            List<Component> rewardLines = QuestTrackerPayload.readTextList(buf);
            Component actionLabel = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            boolean actionEnabled = buf.readBoolean();
            ItemStack previewStack = ItemStack.STREAM_CODEC.decode(buf);
            return new PilgrimContractData(contractId, title, status, descriptionLines, objectiveLines, rewardLines, actionLabel, actionEnabled, previewStack);
        }

        private static void write(RegistryFriendlyByteBuf buf, PilgrimContractData payload) {
            buf.writeUtf(payload.contractId());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.title());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.status());
            QuestTrackerPayload.writeTextList(buf, payload.descriptionLines());
            QuestTrackerPayload.writeTextList(buf, payload.objectiveLines());
            QuestTrackerPayload.writeTextList(buf, payload.rewardLines());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.actionLabel());
            buf.writeBoolean(payload.actionEnabled());
            ItemStack.STREAM_CODEC.encode(buf, payload.previewStack());
        }
    }

    public record PilgrimTradePayload(
            int action,
            int entityId,
            Component merchantName,
            long balance,
            int despawnTicks,
            List<PilgrimTradeOfferData> offers,
            List<PilgrimContractData> contracts
    ) implements CustomPacketPayload {
        public static final int ACTION_OPEN = 0;
        public static final int ACTION_UPDATE = 1;
        public static final int ACTION_CLOSE = 2;

        public static final CustomPacketPayload.Type<PilgrimTradePayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "pilgrim_trade"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PilgrimTradePayload> CODEC =
                StreamCodec.of(PilgrimTradePayload::write, PilgrimTradePayload::read);

        private static PilgrimTradePayload read(RegistryFriendlyByteBuf buf) {
            int action = buf.readVarInt();
            int entityId = buf.readVarInt();
            Component merchantName = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
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

        private static void write(RegistryFriendlyByteBuf buf, PilgrimTradePayload payload) {
            buf.writeVarInt(payload.action());
            buf.writeVarInt(payload.entityId());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.merchantName());
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
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record PilgrimTradeActionPayload(int entityId, String offerId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<PilgrimTradeActionPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "pilgrim_trade_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PilgrimTradeActionPayload> CODEC =
                StreamCodec.of(PilgrimTradeActionPayload::write, PilgrimTradeActionPayload::read);

        private static PilgrimTradeActionPayload read(RegistryFriendlyByteBuf buf) {
            return new PilgrimTradeActionPayload(buf.readVarInt(), buf.readUtf());
        }

        private static void write(RegistryFriendlyByteBuf buf, PilgrimTradeActionPayload payload) {
            buf.writeVarInt(payload.entityId());
            buf.writeUtf(payload.offerId());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record PilgrimTradeSessionPayload(int entityId, int action) implements CustomPacketPayload {
        public static final int ACTION_CLOSE = 0;

        public static final CustomPacketPayload.Type<PilgrimTradeSessionPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "pilgrim_trade_session"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PilgrimTradeSessionPayload> CODEC =
                StreamCodec.of(PilgrimTradeSessionPayload::write, PilgrimTradeSessionPayload::read);

        private static PilgrimTradeSessionPayload read(RegistryFriendlyByteBuf buf) {
            return new PilgrimTradeSessionPayload(buf.readVarInt(), buf.readVarInt());
        }

        private static void write(RegistryFriendlyByteBuf buf, PilgrimTradeSessionPayload payload) {
            buf.writeVarInt(payload.entityId());
            buf.writeVarInt(payload.action());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record QuestMasterCategoryData(
            String categoryId,
            Component label,
            int entryCount
    ) {
        private static QuestMasterCategoryData read(RegistryFriendlyByteBuf buf) {
            return new QuestMasterCategoryData(
                    buf.readUtf(),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    buf.readVarInt()
            );
        }

        private static void write(RegistryFriendlyByteBuf buf, QuestMasterCategoryData category) {
            buf.writeUtf(category.categoryId());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, category.label());
            buf.writeVarInt(category.entryCount());
        }
    }

    public record QuestMasterEntryData(
            String entryId,
            String categoryId,
            Component title,
            Component subtitle,
            Component status,
            List<Component> descriptionLines,
            List<Component> objectiveLines,
            List<Component> rewardLines,
            int primaryAction,
            Component primaryLabel,
            boolean primaryEnabled,
            int secondaryAction,
            Component secondaryLabel,
            boolean secondaryEnabled,
            boolean locked
    ) {
        private static QuestMasterEntryData read(RegistryFriendlyByteBuf buf) {
            return new QuestMasterEntryData(
                    buf.readUtf(),
                    buf.readUtf(),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    readTextList(buf),
                    readTextList(buf),
                    readTextList(buf),
                    buf.readVarInt(),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    buf.readBoolean(),
                    buf.readVarInt(),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    buf.readBoolean(),
                    buf.readBoolean()
            );
        }

        private static void write(RegistryFriendlyByteBuf buf, QuestMasterEntryData entry) {
            buf.writeUtf(entry.entryId());
            buf.writeUtf(entry.categoryId());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, entry.title());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, entry.subtitle());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, entry.status());
            writeTextList(buf, entry.descriptionLines());
            writeTextList(buf, entry.objectiveLines());
            writeTextList(buf, entry.rewardLines());
            buf.writeVarInt(entry.primaryAction());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, entry.primaryLabel());
            buf.writeBoolean(entry.primaryEnabled());
            buf.writeVarInt(entry.secondaryAction());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, entry.secondaryLabel());
            buf.writeBoolean(entry.secondaryEnabled());
            buf.writeBoolean(entry.locked());
        }
    }

    public record QuestMasterPayload(
            int action,
            int entityId,
            Component questMasterName,
            List<QuestMasterCategoryData> categories,
            List<QuestMasterEntryData> entries
    ) implements CustomPacketPayload {
        public static final int ACTION_OPEN = 0;
        public static final int ACTION_UPDATE = 1;
        public static final int ACTION_CLOSE = 2;

        public static final CustomPacketPayload.Type<QuestMasterPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "questmaster"));
        public static final StreamCodec<RegistryFriendlyByteBuf, QuestMasterPayload> CODEC =
                StreamCodec.of(QuestMasterPayload::write, QuestMasterPayload::read);

        private static QuestMasterPayload read(RegistryFriendlyByteBuf buf) {
            int action = buf.readVarInt();
            int entityId = buf.readVarInt();
            Component questMasterName = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
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
            return new QuestMasterPayload(action, entityId, questMasterName, categories, entries);
        }

        private static void write(RegistryFriendlyByteBuf buf, QuestMasterPayload payload) {
            buf.writeVarInt(payload.action());
            buf.writeVarInt(payload.entityId());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.questMasterName());
            buf.writeVarInt(payload.categories().size());
            for (QuestMasterCategoryData category : payload.categories()) {
                QuestMasterCategoryData.write(buf, category);
            }
            buf.writeVarInt(payload.entries().size());
            for (QuestMasterEntryData entry : payload.entries()) {
                QuestMasterEntryData.write(buf, entry);
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record QuestMasterActionPayload(
            int entityId,
            int action,
            String entryId
    ) implements CustomPacketPayload {
        public static final int ACTION_NONE = 0;
        public static final int ACTION_ACCEPT = 1;
        public static final int ACTION_CLAIM = 3;
        public static final int ACTION_CANCEL = 4;

        public static final CustomPacketPayload.Type<QuestMasterActionPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "questmaster_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, QuestMasterActionPayload> CODEC =
                StreamCodec.of(QuestMasterActionPayload::write, QuestMasterActionPayload::read);

        private static QuestMasterActionPayload read(RegistryFriendlyByteBuf buf) {
            return new QuestMasterActionPayload(buf.readVarInt(), buf.readVarInt(), buf.readUtf());
        }

        private static void write(RegistryFriendlyByteBuf buf, QuestMasterActionPayload payload) {
            buf.writeVarInt(payload.entityId());
            buf.writeVarInt(payload.action());
            buf.writeUtf(payload.entryId());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record QuestMasterSessionPayload(int entityId, int action) implements CustomPacketPayload {
        public static final int ACTION_CLOSE = 0;

        public static final CustomPacketPayload.Type<QuestMasterSessionPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "questmaster_session"));
        public static final StreamCodec<RegistryFriendlyByteBuf, QuestMasterSessionPayload> CODEC =
                StreamCodec.of(QuestMasterSessionPayload::write, QuestMasterSessionPayload::read);

        private static QuestMasterSessionPayload read(RegistryFriendlyByteBuf buf) {
            return new QuestMasterSessionPayload(buf.readVarInt(), buf.readVarInt());
        }

        private static void write(RegistryFriendlyByteBuf buf, QuestMasterSessionPayload payload) {
            buf.writeVarInt(payload.entityId());
            buf.writeVarInt(payload.action());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    private static List<Component> readTextList(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Component> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            lines.add(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf));
        }
        return lines;
    }

    private static void writeTextList(RegistryFriendlyByteBuf buf, List<Component> lines) {
        buf.writeVarInt(lines.size());
        for (Component line : lines) {
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, line);
        }
    }
}
