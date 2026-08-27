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
        PayloadTypeRegistry.clientboundPlay().register(EconomyPayload.ID, EconomyPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(QuestMasterPayload.ID, QuestMasterPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TradeRouteMapPayload.ID, TradeRouteMapPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(WayshrinePayload.ID, WayshrinePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(NoticeBoardPayload.ID, NoticeBoardPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(GuildPathPayload.ID, GuildPathPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(QuestFeedbackPayload.ID, QuestFeedbackPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(JournalActionPayload.ID, JournalActionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PilgrimTradeActionPayload.ID, PilgrimTradeActionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PilgrimTradeSessionPayload.ID, PilgrimTradeSessionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(EconomyActionPayload.ID, EconomyActionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(QuestMasterActionPayload.ID, QuestMasterActionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(QuestMasterPartyActionPayload.ID, QuestMasterPartyActionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(QuestMasterSessionPayload.ID, QuestMasterSessionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TradeRouteActionPayload.ID, TradeRouteActionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(WayshrineTravelPayload.ID, WayshrineTravelPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(WayshrineRenamePayload.ID, WayshrineRenamePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(NoticeBoardActionPayload.ID, NoticeBoardActionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClientPreferencesPayload.ID, ClientPreferencesPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(QuestTrackerActionPayload.ID, QuestTrackerActionPayload.CODEC);
        registered = true;
    }

    public record ClientPreferencesPayload(
            boolean questTrackerEnabledByDefault,
            boolean questAvailableChatNotifications,
            boolean caravanEventNotifications,
            boolean questProgressSounds,
            float questProgressSoundVolume
    ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ClientPreferencesPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "client_preferences"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ClientPreferencesPayload> CODEC =
                StreamCodec.of(ClientPreferencesPayload::write, ClientPreferencesPayload::read);

        private static ClientPreferencesPayload read(RegistryFriendlyByteBuf buf) {
            return new ClientPreferencesPayload(
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readFloat());
        }

        private static void write(RegistryFriendlyByteBuf buf, ClientPreferencesPayload payload) {
            buf.writeBoolean(payload.questTrackerEnabledByDefault());
            buf.writeBoolean(payload.questAvailableChatNotifications());
            buf.writeBoolean(payload.caravanEventNotifications());
            buf.writeBoolean(payload.questProgressSounds());
            buf.writeFloat(payload.questProgressSoundVolume());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record QuestFeedbackPayload(int tier) implements CustomPacketPayload {
        public static final int PROGRESS = 0;
        public static final int OBJECTIVE = 1;
        public static final int ACCEPTED = 2;
        public static final int STAGE = 3;
        public static final int AVAILABILITY = 4;

        public static final CustomPacketPayload.Type<QuestFeedbackPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "quest_feedback"));
        public static final StreamCodec<RegistryFriendlyByteBuf, QuestFeedbackPayload> CODEC =
                StreamCodec.of(QuestFeedbackPayload::write, QuestFeedbackPayload::read);

        private static QuestFeedbackPayload read(RegistryFriendlyByteBuf buf) {
            return new QuestFeedbackPayload(buf.readVarInt());
        }

        private static void write(RegistryFriendlyByteBuf buf, QuestFeedbackPayload payload) {
            buf.writeVarInt(payload.tier());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /** A keybind-friendly request to toggle the player's authoritative tracker state. */
    public record QuestTrackerActionPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<QuestTrackerActionPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "quest_tracker_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, QuestTrackerActionPayload> CODEC =
                StreamCodec.of((buf, payload) -> { }, buf -> new QuestTrackerActionPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
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
            boolean hasCaravanLedger,
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
            boolean hasWatchBellProject,
            boolean hasCaravanYardProject,
            boolean hasWayshrineNetworkProject,
            List<GuildPathNodeData> guildPathNodes
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
            boolean hasCaravanLedger = buf.readBoolean();
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
            boolean hasCaravanYardProject = buf.readBoolean();
            boolean hasWayshrineNetworkProject = buf.readBoolean();
            int guildPathNodeCount = Math.max(0, Math.min(64, buf.readVarInt()));
            List<GuildPathNodeData> guildPathNodes = new ArrayList<>(guildPathNodeCount);
            for (int i = 0; i < guildPathNodeCount; i++) {
                guildPathNodes.add(GuildPathNodeData.read(buf));
            }
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
                    hasCaravanLedger,
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
                    hasWatchBellProject,
                    hasCaravanYardProject,
                    hasWayshrineNetworkProject,
                    List.copyOf(guildPathNodes)
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
            buf.writeBoolean(payload.hasCaravanLedger());
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
            buf.writeBoolean(payload.hasCaravanYardProject());
            buf.writeBoolean(payload.hasWayshrineNetworkProject());
            List<GuildPathNodeData> guildPathNodes = payload.guildPathNodes() == null
                    ? List.of() : payload.guildPathNodes();
            buf.writeVarInt(guildPathNodes.size());
            for (GuildPathNodeData node : guildPathNodes) {
                GuildPathNodeData.write(buf, node);
            }
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

    public record EconomyEntryData(
            String entryId,
            String iconName,
            Component title,
            Component subtitle,
            List<Component> descriptionLines,
            long price,
            Component actionLabel,
            boolean actionEnabled,
            boolean owned
    ) {
        private static EconomyEntryData read(RegistryFriendlyByteBuf buf) {
            return new EconomyEntryData(
                    buf.readUtf(),
                    buf.readUtf(),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    QuestTrackerPayload.readTextList(buf),
                    buf.readLong(),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    buf.readBoolean(),
                    buf.readBoolean()
            );
        }

        private static void write(RegistryFriendlyByteBuf buf, EconomyEntryData entry) {
            buf.writeUtf(entry.entryId());
            buf.writeUtf(entry.iconName());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, entry.title());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, entry.subtitle());
            QuestTrackerPayload.writeTextList(buf, entry.descriptionLines());
            buf.writeLong(entry.price());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, entry.actionLabel());
            buf.writeBoolean(entry.actionEnabled());
            buf.writeBoolean(entry.owned());
        }
    }

    public record EconomySectionData(
            String sectionId,
            Component label,
            String iconName,
            List<EconomyEntryData> entries
    ) {
        private static EconomySectionData read(RegistryFriendlyByteBuf buf) {
            String sectionId = buf.readUtf();
            Component label = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            String iconName = buf.readUtf();
            int count = buf.readVarInt();
            List<EconomyEntryData> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) entries.add(EconomyEntryData.read(buf));
            return new EconomySectionData(sectionId, label, iconName, List.copyOf(entries));
        }

        private static void write(RegistryFriendlyByteBuf buf, EconomySectionData section) {
            buf.writeUtf(section.sectionId());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, section.label());
            buf.writeUtf(section.iconName());
            buf.writeVarInt(section.entries().size());
            for (EconomyEntryData entry : section.entries()) EconomyEntryData.write(buf, entry);
        }
    }

    public record EconomyPayload(
            int action,
            long balance,
            List<Component> routeNames,
            List<EconomySectionData> sections
    ) implements CustomPacketPayload {
        public static final int ACTION_OPEN = 0;
        public static final int ACTION_UPDATE = 1;

        public static final CustomPacketPayload.Type<EconomyPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "economy"));
        public static final StreamCodec<RegistryFriendlyByteBuf, EconomyPayload> CODEC =
                StreamCodec.of(EconomyPayload::write, EconomyPayload::read);

        private static EconomyPayload read(RegistryFriendlyByteBuf buf) {
            int action = buf.readVarInt();
            long balance = buf.readLong();
            List<Component> routeNames = QuestTrackerPayload.readTextList(buf);
            int count = buf.readVarInt();
            List<EconomySectionData> sections = new ArrayList<>(count);
            for (int i = 0; i < count; i++) sections.add(EconomySectionData.read(buf));
            return new EconomyPayload(action, balance, List.copyOf(routeNames), List.copyOf(sections));
        }

        private static void write(RegistryFriendlyByteBuf buf, EconomyPayload payload) {
            buf.writeVarInt(payload.action());
            buf.writeLong(payload.balance());
            QuestTrackerPayload.writeTextList(buf, payload.routeNames());
            buf.writeVarInt(payload.sections().size());
            for (EconomySectionData section : payload.sections()) EconomySectionData.write(buf, section);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record EconomyActionPayload(String actionId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<EconomyActionPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "economy_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, EconomyActionPayload> CODEC =
                StreamCodec.of(EconomyActionPayload::write, EconomyActionPayload::read);

        private static EconomyActionPayload read(RegistryFriendlyByteBuf buf) {
            return new EconomyActionPayload(buf.readUtf());
        }

        private static void write(RegistryFriendlyByteBuf buf, EconomyActionPayload payload) {
            buf.writeUtf(payload.actionId());
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
            boolean partyShareable,
            Component partyStatus,
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
                    buf.readBoolean(),
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
            buf.writeBoolean(entry.partyShareable());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, entry.partyStatus());
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

    public record QuestMasterPartyMemberData(
            String playerId,
            Component name,
            boolean leader,
            boolean self
    ) {
        private static QuestMasterPartyMemberData read(RegistryFriendlyByteBuf buf) {
            return new QuestMasterPartyMemberData(
                    buf.readUtf(),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    buf.readBoolean(),
                    buf.readBoolean()
            );
        }

        private static void write(RegistryFriendlyByteBuf buf, QuestMasterPartyMemberData member) {
            buf.writeUtf(member.playerId());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, member.name());
            buf.writeBoolean(member.leader());
            buf.writeBoolean(member.self());
        }
    }

    public record QuestMasterPartyCandidateData(
            String playerId,
            Component name,
            Component status,
            boolean inviteable
    ) {
        private static QuestMasterPartyCandidateData read(RegistryFriendlyByteBuf buf) {
            return new QuestMasterPartyCandidateData(
                    buf.readUtf(),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    buf.readBoolean()
            );
        }

        private static void write(RegistryFriendlyByteBuf buf, QuestMasterPartyCandidateData candidate) {
            buf.writeUtf(candidate.playerId());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, candidate.name());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, candidate.status());
            buf.writeBoolean(candidate.inviteable());
        }
    }

    public record QuestMasterPartyData(
            boolean hasParty,
            boolean leader,
            Component summary,
            List<QuestMasterPartyMemberData> members,
            List<QuestMasterPartyCandidateData> candidates
    ) {
        private static QuestMasterPartyData read(RegistryFriendlyByteBuf buf) {
            boolean hasParty = buf.readBoolean();
            boolean leader = buf.readBoolean();
            Component summary = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            int memberCount = buf.readVarInt();
            List<QuestMasterPartyMemberData> members = new ArrayList<>(memberCount);
            for (int i = 0; i < memberCount; i++) {
                members.add(QuestMasterPartyMemberData.read(buf));
            }
            int candidateCount = buf.readVarInt();
            List<QuestMasterPartyCandidateData> candidates = new ArrayList<>(candidateCount);
            for (int i = 0; i < candidateCount; i++) {
                candidates.add(QuestMasterPartyCandidateData.read(buf));
            }
            return new QuestMasterPartyData(hasParty, leader, summary, members, candidates);
        }

        private static void write(RegistryFriendlyByteBuf buf, QuestMasterPartyData party) {
            buf.writeBoolean(party.hasParty());
            buf.writeBoolean(party.leader());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, party.summary());
            buf.writeVarInt(party.members().size());
            for (QuestMasterPartyMemberData member : party.members()) {
                QuestMasterPartyMemberData.write(buf, member);
            }
            buf.writeVarInt(party.candidates().size());
            for (QuestMasterPartyCandidateData candidate : party.candidates()) {
                QuestMasterPartyCandidateData.write(buf, candidate);
            }
        }
    }

    public record QuestMasterPayload(
            int action,
            int entityId,
            Component questMasterName,
            List<QuestMasterCategoryData> categories,
            List<QuestMasterEntryData> entries,
            QuestMasterPartyData party,
            long storyCooldownUntil
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
            QuestMasterPartyData party = QuestMasterPartyData.read(buf);
            long storyCooldownUntil = buf.readLong();
            return new QuestMasterPayload(action, entityId, questMasterName, categories, entries, party, storyCooldownUntil);
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
            QuestMasterPartyData.write(buf, payload.party());
            buf.writeLong(payload.storyCooldownUntil());
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
        public static final int ACTION_OPEN_GUILD_PATH = 5;

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

    public record QuestMasterPartyActionPayload(
            int entityId,
            int action,
            String playerId
    ) implements CustomPacketPayload {
        public static final int ACTION_INVITE = 1;
        public static final int ACTION_LEAVE = 2;
        public static final int ACTION_DISBAND = 3;

        public static final CustomPacketPayload.Type<QuestMasterPartyActionPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "questmaster_party_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, QuestMasterPartyActionPayload> CODEC =
                StreamCodec.of(QuestMasterPartyActionPayload::write, QuestMasterPartyActionPayload::read);

        private static QuestMasterPartyActionPayload read(RegistryFriendlyByteBuf buf) {
            return new QuestMasterPartyActionPayload(buf.readVarInt(), buf.readVarInt(), buf.readUtf());
        }

        private static void write(RegistryFriendlyByteBuf buf, QuestMasterPartyActionPayload payload) {
            buf.writeVarInt(payload.entityId());
            buf.writeVarInt(payload.action());
            buf.writeUtf(payload.playerId());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record GuildPathNodeData(String nodeId, ItemStack previewStack,
                                    Component title, Component ability,
                                    Component requirement, int status) {
        private static GuildPathNodeData read(RegistryFriendlyByteBuf buf) {
            return new GuildPathNodeData(buf.readUtf(48), ItemStack.STREAM_CODEC.decode(buf),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readVarInt());
        }
        private static void write(RegistryFriendlyByteBuf buf, GuildPathNodeData value) {
            buf.writeUtf(value.nodeId(), 48);
            ItemStack.STREAM_CODEC.encode(buf, value.previewStack());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, value.title());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, value.ability());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, value.requirement());
            buf.writeVarInt(value.status());
        }
    }

    public record GuildPathPayload(int questMasterEntityId, long balance,
                                   List<GuildPathNodeData> nodes) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<GuildPathPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "guild_path"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GuildPathPayload> CODEC =
                StreamCodec.of(GuildPathPayload::write, GuildPathPayload::read);
        private static GuildPathPayload read(RegistryFriendlyByteBuf buf) {
            int entityId = buf.readVarInt();
            long balance = buf.readLong();
            int count = buf.readVarInt();
            List<GuildPathNodeData> nodes = new ArrayList<>(count);
            for (int i = 0; i < count; i++) nodes.add(GuildPathNodeData.read(buf));
            return new GuildPathPayload(entityId, balance, List.copyOf(nodes));
        }
        private static void write(RegistryFriendlyByteBuf buf, GuildPathPayload payload) {
            buf.writeVarInt(payload.questMasterEntityId());
            buf.writeLong(payload.balance());
            buf.writeVarInt(payload.nodes().size());
            for (GuildPathNodeData node : payload.nodes()) GuildPathNodeData.write(buf, node);
        }
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
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

    public record TradeRouteNodeData(
            int nodeIndex,
            Component name,
            int worldX,
            int worldZ,
            boolean home,
            boolean playerYard
    ) {
        private static TradeRouteNodeData read(RegistryFriendlyByteBuf buf) {
            return new TradeRouteNodeData(
                    buf.readVarInt(),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readBoolean()
            );
        }

        private static void write(RegistryFriendlyByteBuf buf, TradeRouteNodeData node) {
            buf.writeVarInt(node.nodeIndex());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, node.name());
            buf.writeInt(node.worldX());
            buf.writeInt(node.worldZ());
            buf.writeBoolean(node.home());
            buf.writeBoolean(node.playerYard());
        }
    }

    public record TradeRoutePointData(int worldX, int worldZ, boolean ocean) {
        private static TradeRoutePointData read(RegistryFriendlyByteBuf buf) {
            return new TradeRoutePointData(buf.readInt(), buf.readInt(), buf.readBoolean());
        }

        private static void write(RegistryFriendlyByteBuf buf, TradeRoutePointData point) {
            buf.writeInt(point.worldX());
            buf.writeInt(point.worldZ());
            buf.writeBoolean(point.ocean());
        }
    }

    public record TradeRouteLineData(
            int routeIndex,
            int liveryIndex,
            Component name,
            int status,
            Component statusLabel,
            int roadQuality,
            int progress,
            boolean returning,
            boolean paused,
            boolean surveying,
            Component eventLabel,
            Component eventHelp,
            long lifetimeEarnings,
            Component specializationLabel,
            Component upgradeSummary,
            List<TradeRoutePointData> waypoints
    ) {
        private static TradeRouteLineData read(RegistryFriendlyByteBuf buf) {
            int routeIndex = buf.readVarInt();
            int liveryIndex = buf.readVarInt();
            Component name = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            int status = buf.readVarInt();
            Component statusLabel = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            int roadQuality = buf.readVarInt();
            int progress = buf.readVarInt();
            boolean returning = buf.readBoolean();
            boolean paused = buf.readBoolean();
            boolean surveying = buf.readBoolean();
            Component eventLabel = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            Component eventHelp = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            long lifetimeEarnings = buf.readLong();
            Component specializationLabel = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            Component upgradeSummary = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            int waypointCount = buf.readVarInt();
            List<TradeRoutePointData> waypoints = new ArrayList<>(waypointCount);
            for (int i = 0; i < waypointCount; i++) {
                waypoints.add(TradeRoutePointData.read(buf));
            }
            return new TradeRouteLineData(routeIndex, liveryIndex, name, status, statusLabel, roadQuality, progress,
                    returning, paused, surveying, eventLabel, eventHelp, lifetimeEarnings,
                    specializationLabel, upgradeSummary, List.copyOf(waypoints));
        }

        private static void write(RegistryFriendlyByteBuf buf, TradeRouteLineData route) {
            buf.writeVarInt(route.routeIndex());
            buf.writeVarInt(route.liveryIndex());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, route.name());
            buf.writeVarInt(route.status());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, route.statusLabel());
            buf.writeVarInt(route.roadQuality());
            buf.writeVarInt(route.progress());
            buf.writeBoolean(route.returning());
            buf.writeBoolean(route.paused());
            buf.writeBoolean(route.surveying());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, route.eventLabel());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, route.eventHelp());
            buf.writeLong(route.lifetimeEarnings());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, route.specializationLabel());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, route.upgradeSummary());
            buf.writeVarInt(route.waypoints().size());
            for (TradeRoutePointData point : route.waypoints()) {
                TradeRoutePointData.write(buf, point);
            }
        }
    }

    public record TradeRouteCaravanData(
            int routeIndex,
            int progress,
            boolean returning,
            boolean materialized,
            boolean boarding,
            boolean ferry,
            int ferrySecondsRemaining
    ) {
        private static TradeRouteCaravanData read(RegistryFriendlyByteBuf buf) {
            return new TradeRouteCaravanData(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt());
        }

        private static void write(RegistryFriendlyByteBuf buf, TradeRouteCaravanData caravan) {
            buf.writeVarInt(caravan.routeIndex());
            buf.writeVarInt(caravan.progress());
            buf.writeBoolean(caravan.returning());
            buf.writeBoolean(caravan.materialized());
            buf.writeBoolean(caravan.boarding());
            buf.writeBoolean(caravan.ferry());
            buf.writeVarInt(caravan.ferrySecondsRemaining());
        }
    }

    public record TradeRouteBondData(int index, int worldX, int worldZ, Component type,
                                     Component level, Component request, int completions) {
        private static TradeRouteBondData read(RegistryFriendlyByteBuf buf) {
            return new TradeRouteBondData(buf.readVarInt(), buf.readInt(), buf.readInt(),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readVarInt());
        }

        private static void write(RegistryFriendlyByteBuf buf, TradeRouteBondData value) {
            buf.writeVarInt(value.index());
            buf.writeInt(value.worldX());
            buf.writeInt(value.worldZ());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, value.type());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, value.level());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, value.request());
            buf.writeVarInt(value.completions());
        }
    }

    public record TradeRouteShrineData(int index, int worldX, int worldY, int worldZ,
                                       Component name, boolean current, int cost,
                                       int bondTier, int chargeCost, int cooldownSeconds) {
        private static TradeRouteShrineData read(RegistryFriendlyByteBuf buf) {
            return new TradeRouteShrineData(buf.readVarInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readBoolean(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }

        private static void write(RegistryFriendlyByteBuf buf, TradeRouteShrineData value) {
            buf.writeVarInt(value.index());
            buf.writeInt(value.worldX());
            buf.writeInt(value.worldY());
            buf.writeInt(value.worldZ());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, value.name());
            buf.writeBoolean(value.current());
            buf.writeVarInt(value.cost());
            buf.writeVarInt(value.bondTier());
            buf.writeVarInt(value.chargeCost());
            buf.writeVarInt(value.cooldownSeconds());
        }
    }

    public record TradeRouteDecorationData(int type, int worldX, int worldY, int worldZ) {
        private static TradeRouteDecorationData read(RegistryFriendlyByteBuf buf) {
            return new TradeRouteDecorationData(buf.readVarInt(), buf.readInt(), buf.readInt(), buf.readInt());
        }
        private static void write(RegistryFriendlyByteBuf buf, TradeRouteDecorationData value) {
            buf.writeVarInt(value.type()); buf.writeInt(value.worldX());
            buf.writeInt(value.worldY()); buf.writeInt(value.worldZ());
        }
    }

    public record TradeRouteMapPayload(
            int action,
            Component title,
            Component summary,
            List<TradeRouteNodeData> nodes,
            List<TradeRouteLineData> routes,
            List<TradeRouteCaravanData> caravans,
            List<TradeRouteBondData> bonds,
            List<TradeRouteShrineData> shrines,
            List<TradeRouteDecorationData> decorations
    ) implements CustomPacketPayload {
        public static final int ACTION_OPEN = 0;
        public static final int ACTION_UPDATE = 1;
        public static final int ACTION_CLOSE = 2;
        public static final int ACTION_MINIMAP_ENABLE = 3;
        public static final int ACTION_MINIMAP_UPDATE = 4;
        public static final int ACTION_MINIMAP_DISABLE = 5;

        public static final CustomPacketPayload.Type<TradeRouteMapPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "trade_route_map"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TradeRouteMapPayload> CODEC =
                StreamCodec.of(TradeRouteMapPayload::write, TradeRouteMapPayload::read);

        private static TradeRouteMapPayload read(RegistryFriendlyByteBuf buf) {
            int action = buf.readVarInt();
            Component title = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            Component summary = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            int nodeCount = buf.readVarInt();
            List<TradeRouteNodeData> nodes = new ArrayList<>(nodeCount);
            for (int i = 0; i < nodeCount; i++) {
                nodes.add(TradeRouteNodeData.read(buf));
            }
            int routeCount = buf.readVarInt();
            List<TradeRouteLineData> routes = new ArrayList<>(routeCount);
            for (int i = 0; i < routeCount; i++) {
                routes.add(TradeRouteLineData.read(buf));
            }
            int caravanCount = buf.readVarInt();
            List<TradeRouteCaravanData> caravans = new ArrayList<>(caravanCount);
            for (int i = 0; i < caravanCount; i++) {
                caravans.add(TradeRouteCaravanData.read(buf));
            }
            int bondCount = buf.readVarInt();
            List<TradeRouteBondData> bonds = new ArrayList<>(bondCount);
            for (int i = 0; i < bondCount; i++) bonds.add(TradeRouteBondData.read(buf));
            int shrineCount = buf.readVarInt();
            List<TradeRouteShrineData> shrines = new ArrayList<>(shrineCount);
            for (int i = 0; i < shrineCount; i++) shrines.add(TradeRouteShrineData.read(buf));
            int decorationCount = buf.readVarInt();
            List<TradeRouteDecorationData> decorations = new ArrayList<>(decorationCount);
            for (int i = 0; i < decorationCount; i++) decorations.add(TradeRouteDecorationData.read(buf));
            return new TradeRouteMapPayload(action, title, summary, nodes, routes, caravans, bonds, shrines, decorations);
        }

        private static void write(RegistryFriendlyByteBuf buf, TradeRouteMapPayload payload) {
            buf.writeVarInt(payload.action());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.title());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.summary());
            buf.writeVarInt(payload.nodes().size());
            for (TradeRouteNodeData node : payload.nodes()) {
                TradeRouteNodeData.write(buf, node);
            }
            buf.writeVarInt(payload.routes().size());
            for (TradeRouteLineData route : payload.routes()) {
                TradeRouteLineData.write(buf, route);
            }
            buf.writeVarInt(payload.caravans().size());
            for (TradeRouteCaravanData caravan : payload.caravans()) {
                TradeRouteCaravanData.write(buf, caravan);
            }
            buf.writeVarInt(payload.bonds().size());
            for (TradeRouteBondData bond : payload.bonds()) TradeRouteBondData.write(buf, bond);
            buf.writeVarInt(payload.shrines().size());
            for (TradeRouteShrineData shrine : payload.shrines()) TradeRouteShrineData.write(buf, shrine);
            buf.writeVarInt(payload.decorations().size());
            for (TradeRouteDecorationData decoration : payload.decorations()) TradeRouteDecorationData.write(buf, decoration);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record WayshrinePayload(int currentIndex, List<TradeRouteShrineData> destinations,
                                   String ownerName, boolean owner, int guestMultiplier,
                                   int cooldownSeconds, long balance, int charges,
                                   int chargesPerShard, int maxCharges)
            implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WayshrinePayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "wayshrine"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WayshrinePayload> CODEC =
                StreamCodec.of(WayshrinePayload::write, WayshrinePayload::read);

        private static WayshrinePayload read(RegistryFriendlyByteBuf buf) {
            int current = buf.readVarInt();
            int count = buf.readVarInt();
            List<TradeRouteShrineData> destinations = new ArrayList<>(count);
            for (int i = 0; i < count; i++) destinations.add(TradeRouteShrineData.read(buf));
            return new WayshrinePayload(current, destinations, buf.readUtf(32), buf.readBoolean(),
                    buf.readVarInt(), buf.readVarInt(), buf.readLong(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt());
        }

        private static void write(RegistryFriendlyByteBuf buf, WayshrinePayload payload) {
            buf.writeVarInt(payload.currentIndex());
            buf.writeVarInt(payload.destinations().size());
            for (TradeRouteShrineData destination : payload.destinations()) TradeRouteShrineData.write(buf, destination);
            buf.writeUtf(payload.ownerName(), 32);
            buf.writeBoolean(payload.owner());
            buf.writeVarInt(payload.guestMultiplier());
            buf.writeVarInt(payload.cooldownSeconds());
            buf.writeLong(payload.balance());
            buf.writeVarInt(payload.charges());
            buf.writeVarInt(payload.chargesPerShard());
            buf.writeVarInt(payload.maxCharges());
        }

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record NoticeBoardPayload(int worldX, int worldY, int worldZ,
                                     Component villageType, Component bondLevel,
                                     Component requestTitle, ItemStack requestStack,
                                     int requiredAmount, int inventoryAmount, long reward,
                                     long balance, int completions, int bondTier,
                                     int nextThreshold, Component nextLevel,
                                     Component nextPerk, boolean requestAvailable,
                                     boolean canDeliver) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<NoticeBoardPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "notice_board"));
        public static final StreamCodec<RegistryFriendlyByteBuf, NoticeBoardPayload> CODEC =
                StreamCodec.of(NoticeBoardPayload::write, NoticeBoardPayload::read);

        private static NoticeBoardPayload read(RegistryFriendlyByteBuf buf) {
            return new NoticeBoardPayload(buf.readInt(), buf.readInt(), buf.readInt(),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    ItemStack.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(),
                    buf.readLong(), buf.readLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readBoolean(), buf.readBoolean());
        }

        private static void write(RegistryFriendlyByteBuf buf, NoticeBoardPayload payload) {
            buf.writeInt(payload.worldX()); buf.writeInt(payload.worldY()); buf.writeInt(payload.worldZ());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.villageType());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.bondLevel());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.requestTitle());
            ItemStack.STREAM_CODEC.encode(buf, payload.requestStack());
            buf.writeVarInt(payload.requiredAmount()); buf.writeVarInt(payload.inventoryAmount());
            buf.writeLong(payload.reward()); buf.writeLong(payload.balance());
            buf.writeVarInt(payload.completions()); buf.writeVarInt(payload.bondTier());
            buf.writeVarInt(payload.nextThreshold());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.nextLevel());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.nextPerk());
            buf.writeBoolean(payload.requestAvailable());
            buf.writeBoolean(payload.canDeliver());
        }

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record NoticeBoardActionPayload(int worldX, int worldY, int worldZ, int action)
            implements CustomPacketPayload {
        public static final int ACTION_DELIVER = 1;
        public static final CustomPacketPayload.Type<NoticeBoardActionPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "notice_board_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, NoticeBoardActionPayload> CODEC =
                StreamCodec.of((buf, value) -> {
                    buf.writeInt(value.worldX()); buf.writeInt(value.worldY()); buf.writeInt(value.worldZ());
                    buf.writeVarInt(value.action());
                }, buf -> new NoticeBoardActionPayload(buf.readInt(), buf.readInt(), buf.readInt(), buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record WayshrineTravelPayload(int currentIndex, int targetIndex, boolean useCharge) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WayshrineTravelPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "wayshrine_travel"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WayshrineTravelPayload> CODEC =
                StreamCodec.of(WayshrineTravelPayload::write, WayshrineTravelPayload::read);
        private static WayshrineTravelPayload read(RegistryFriendlyByteBuf buf) {
            return new WayshrineTravelPayload(buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
        }
        private static void write(RegistryFriendlyByteBuf buf, WayshrineTravelPayload payload) {
            buf.writeVarInt(payload.currentIndex());
            buf.writeVarInt(payload.targetIndex());
            buf.writeBoolean(payload.useCharge());
        }
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record WayshrineRenamePayload(int currentIndex, String name) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WayshrineRenamePayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "wayshrine_rename"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WayshrineRenamePayload> CODEC =
                StreamCodec.of(WayshrineRenamePayload::write, WayshrineRenamePayload::read);
        private static WayshrineRenamePayload read(RegistryFriendlyByteBuf buf) {
            return new WayshrineRenamePayload(buf.readVarInt(), buf.readUtf(32));
        }
        private static void write(RegistryFriendlyByteBuf buf, WayshrineRenamePayload payload) {
            buf.writeVarInt(payload.currentIndex());
            buf.writeUtf(payload.name(), 32);
        }
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record TradeRouteActionPayload(int action, int routeIndex) implements CustomPacketPayload {
        public static final int ACTION_CLOSE = 0;
        public static final int ACTION_TOGGLE = 1;
        public static final int ACTION_SURVEY_START = 2;
        public static final int ACTION_SURVEY_FINISH = 3;
        public static final int ACTION_SURVEY_CANCEL = 4;
        public static final int ACTION_REMOVE = 5;
        public static final int ACTION_MINIMAP_TOGGLE = 6;

        public static final CustomPacketPayload.Type<TradeRouteActionPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "trade_route_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TradeRouteActionPayload> CODEC =
                StreamCodec.of(TradeRouteActionPayload::write, TradeRouteActionPayload::read);

        private static TradeRouteActionPayload read(RegistryFriendlyByteBuf buf) {
            return new TradeRouteActionPayload(buf.readVarInt(), buf.readVarInt());
        }

        private static void write(RegistryFriendlyByteBuf buf, TradeRouteActionPayload payload) {
            buf.writeVarInt(payload.action());
            buf.writeVarInt(payload.routeIndex());
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
