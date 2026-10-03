package de.quest.network;

import de.quest.VillageQuest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Packets owned by the village-board and Wayshrine domain. */
public final class VillageNetworkPayloads {
    public static final int MAX_NOTICE_BOARD_OFFERS = 8;

    private VillageNetworkPayloads() {}

    public record WayshrinePayload(int currentIndex, List<Payloads.TradeRouteShrineData> destinations,
                                   String ownerName, boolean owner, int guestMultiplier,
                                   int cooldownSeconds, long balance, int charges,
                                   int chargesPerShard, int maxCharges) implements CustomPacketPayload {
        public static final Type<WayshrinePayload> ID = new Type<>(
                Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "wayshrine"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WayshrinePayload> CODEC =
                StreamCodec.of(WayshrinePayload::write, WayshrinePayload::read);

        private static WayshrinePayload read(RegistryFriendlyByteBuf buf) {
            int current = buf.readVarInt();
            int count = buf.readVarInt();
            List<Payloads.TradeRouteShrineData> destinations = new ArrayList<>(count);
            for (int i = 0; i < count; i++) destinations.add(Payloads.TradeRouteShrineData.read(buf));
            return new WayshrinePayload(current, destinations, buf.readUtf(32), buf.readBoolean(),
                    buf.readVarInt(), buf.readVarInt(), buf.readLong(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt());
        }

        private static void write(RegistryFriendlyByteBuf buf, WayshrinePayload payload) {
            buf.writeVarInt(payload.currentIndex());
            buf.writeVarInt(payload.destinations().size());
            for (Payloads.TradeRouteShrineData destination : payload.destinations()) {
                Payloads.TradeRouteShrineData.write(buf, destination);
            }
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

    public record NoticeBoardOfferData(int id, Component title, ItemStack stack,
                                       int requiredAmount, int inventoryAmount, long reward,
                                       int support, boolean primaryNeed, boolean canDeliver) {
        private static NoticeBoardOfferData read(RegistryFriendlyByteBuf buf) {
            return new NoticeBoardOfferData(buf.readVarInt(),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    ItemStack.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(),
                    buf.readLong(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
        }

        private static void write(RegistryFriendlyByteBuf buf, NoticeBoardOfferData value) {
            buf.writeVarInt(value.id());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, value.title());
            ItemStack.STREAM_CODEC.encode(buf, value.stack());
            buf.writeVarInt(value.requiredAmount());
            buf.writeVarInt(value.inventoryAmount());
            buf.writeLong(value.reward());
            buf.writeVarInt(value.support());
            buf.writeBoolean(value.primaryNeed());
            buf.writeBoolean(value.canDeliver());
        }
    }

    public record NoticeBoardPayload(int worldX, int worldY, int worldZ,
                                     Component villageType, Component bondLevel,
                                     Component villageCondition, Component villageNeed, int villageSupport,
                                     Component requestTitle, ItemStack requestStack,
                                     int requiredAmount, int inventoryAmount, long reward,
                                     long balance, int completions, int bondTier,
                                     int nextThreshold, Component nextLevel, Component nextPerk,
                                     boolean requestAvailable, boolean canDeliver,
                                     List<NoticeBoardOfferData> offers,
                                     Component adventureProfile) implements CustomPacketPayload {
        public static final Type<NoticeBoardPayload> ID = new Type<>(
                Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "notice_board"));
        public static final StreamCodec<RegistryFriendlyByteBuf, NoticeBoardPayload> CODEC =
                StreamCodec.of(NoticeBoardPayload::write, NoticeBoardPayload::read);

        private static NoticeBoardPayload read(RegistryFriendlyByteBuf buf) {
            int worldX = buf.readInt();
            int worldY = buf.readInt();
            int worldZ = buf.readInt();
            Component villageType = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            Component bondLevel = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            Component condition = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            Component need = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            int support = buf.readVarInt();
            Component requestTitle = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            ItemStack requestStack = ItemStack.STREAM_CODEC.decode(buf);
            int required = buf.readVarInt();
            int inventory = buf.readVarInt();
            long reward = buf.readLong();
            long balance = buf.readLong();
            int completions = buf.readVarInt();
            int bondTier = buf.readVarInt();
            int nextThreshold = buf.readVarInt();
            Component nextLevel = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            Component nextPerk = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            boolean available = buf.readBoolean();
            boolean canDeliver = buf.readBoolean();
            int offerCount = Math.max(0, Math.min(MAX_NOTICE_BOARD_OFFERS, buf.readVarInt()));
            List<NoticeBoardOfferData> offers = new ArrayList<>(offerCount);
            for (int i = 0; i < offerCount; i++) offers.add(NoticeBoardOfferData.read(buf));
            Component profile = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            return new NoticeBoardPayload(worldX, worldY, worldZ, villageType, bondLevel, condition, need,
                    support, requestTitle, requestStack, required, inventory, reward, balance, completions,
                    bondTier, nextThreshold, nextLevel, nextPerk, available, canDeliver,
                    List.copyOf(offers), profile);
        }

        private static void write(RegistryFriendlyByteBuf buf, NoticeBoardPayload payload) {
            buf.writeInt(payload.worldX());
            buf.writeInt(payload.worldY());
            buf.writeInt(payload.worldZ());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.villageType());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.bondLevel());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.villageCondition());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.villageNeed());
            buf.writeVarInt(payload.villageSupport());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.requestTitle());
            ItemStack.STREAM_CODEC.encode(buf, payload.requestStack());
            buf.writeVarInt(payload.requiredAmount());
            buf.writeVarInt(payload.inventoryAmount());
            buf.writeLong(payload.reward());
            buf.writeLong(payload.balance());
            buf.writeVarInt(payload.completions());
            buf.writeVarInt(payload.bondTier());
            buf.writeVarInt(payload.nextThreshold());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.nextLevel());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.nextPerk());
            buf.writeBoolean(payload.requestAvailable());
            buf.writeBoolean(payload.canDeliver());
            List<NoticeBoardOfferData> offers = payload.offers() == null ? List.of() : payload.offers();
            int offerCount = Math.min(MAX_NOTICE_BOARD_OFFERS, offers.size());
            buf.writeVarInt(offerCount);
            for (int i = 0; i < offerCount; i++) NoticeBoardOfferData.write(buf, offers.get(i));
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf,
                    payload.adventureProfile() == null ? Component.empty() : payload.adventureProfile());
        }

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record NoticeBoardActionPayload(int worldX, int worldY, int worldZ, int action, int requestId)
            implements CustomPacketPayload {
        public static final int ACTION_DELIVER = 1;
        public static final Type<NoticeBoardActionPayload> ID = new Type<>(
                Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "notice_board_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, NoticeBoardActionPayload> CODEC =
                StreamCodec.of((buf, value) -> {
                    buf.writeInt(value.worldX());
                    buf.writeInt(value.worldY());
                    buf.writeInt(value.worldZ());
                    buf.writeVarInt(value.action());
                    buf.writeVarInt(value.requestId());
                }, buf -> new NoticeBoardActionPayload(buf.readInt(), buf.readInt(), buf.readInt(),
                        buf.readVarInt(), buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /** One board view for welcome, local stories and the later route request page. */
    public record NoticeJourneyPayload(int worldX, int worldY, int worldZ, int stage,
                                       Component village, Component title, Component detail,
                                       int first, int firstTarget, int second, int secondTarget,
                                       boolean sharedTable, ItemStack delivery, int deliveryCount,
                                       NoticeBoardPayload requests, boolean preview) implements CustomPacketPayload {
        public static final int INTRO = 0, AVAILABLE = 1, ACTIVE = 2, CHOOSE = 3,
                READY = 4, REMEMBERED = 5, PAUSED = 6, AWAY = 7, QUESTMASTER = 8,
                RESETTLEMENT = 9, RECOVERING = 10,
                AFTERSTORY_AVAILABLE = 11, AFTERSTORY_ACTIVE = 12,
                AFTERSTORY_READY = 13, AFTERSTORY_COMPLETE = 14;
        public static final Type<NoticeJourneyPayload> ID = new Type<>(
                Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "notice_journey"));
        public static final StreamCodec<RegistryFriendlyByteBuf, NoticeJourneyPayload> CODEC =
                StreamCodec.of((buf, value) -> {
                    buf.writeInt(value.worldX()); buf.writeInt(value.worldY()); buf.writeInt(value.worldZ());
                    buf.writeVarInt(value.stage());
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, value.village());
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, value.title());
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, value.detail());
                    buf.writeVarInt(value.first()); buf.writeVarInt(value.firstTarget());
                    buf.writeVarInt(value.second()); buf.writeVarInt(value.secondTarget());
                    buf.writeBoolean(value.sharedTable());
                    buf.writeBoolean(!value.delivery().isEmpty());
                    if (!value.delivery().isEmpty()) ItemStack.STREAM_CODEC.encode(buf, value.delivery());
                    buf.writeVarInt(value.deliveryCount());
                    buf.writeBoolean(value.requests() != null);
                    if (value.requests() != null) NoticeBoardPayload.CODEC.encode(buf, value.requests());
                    buf.writeBoolean(value.preview());
                }, buf -> new NoticeJourneyPayload(buf.readInt(), buf.readInt(), buf.readInt(), buf.readVarInt(),
                        ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                        ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                        ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readBoolean(), buf.readBoolean() ? ItemStack.STREAM_CODEC.decode(buf) : ItemStack.EMPTY,
                        buf.readVarInt(),
                        buf.readBoolean() ? NoticeBoardPayload.CODEC.decode(buf) : null,
                        buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record NoticeJourneyActionPayload(int worldX, int worldY, int worldZ, int action)
            implements CustomPacketPayload {
        public static final int ACCEPT = 1, RESERVE = 2, SHARE = 3, DELIVER = 4, REFRESH = 5, RESUME = 6,
                DISPATCH = 7, ACCEPT_AFTERSTORY = 8, DELIVER_AFTERSTORY = 9;
        public static final Type<NoticeJourneyActionPayload> ID = new Type<>(
                Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "notice_journey_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, NoticeJourneyActionPayload> CODEC =
                StreamCodec.of((buf, value) -> {
                    buf.writeInt(value.worldX()); buf.writeInt(value.worldY()); buf.writeInt(value.worldZ());
                    buf.writeVarInt(value.action());
                }, buf -> new NoticeJourneyActionPayload(buf.readInt(), buf.readInt(), buf.readInt(), buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record RegionalDispatchOfferData(int routeIndex, Component destination,
                                            ItemStack cargo, int amount, int inventory, int reward) {
        private static RegionalDispatchOfferData read(RegistryFriendlyByteBuf buf) {
            return new RegionalDispatchOfferData(buf.readVarInt(),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    ItemStack.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }

        private static void write(RegistryFriendlyByteBuf buf, RegionalDispatchOfferData value) {
            buf.writeVarInt(value.routeIndex());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, value.destination());
            ItemStack.STREAM_CODEC.encode(buf, value.cargo());
            buf.writeVarInt(value.amount()); buf.writeVarInt(value.inventory()); buf.writeVarInt(value.reward());
        }
    }

    public record RegionalDispatchPayload(int worldX, int worldY, int worldZ,
                                          Component source, Component status, Component path,
                                          ItemStack activeCargo, int activeAmount, boolean canClaim, boolean canStart,
                                          List<RegionalDispatchOfferData> offers) implements CustomPacketPayload {
        public static final Type<RegionalDispatchPayload> ID = new Type<>(
                Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "regional_dispatch"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RegionalDispatchPayload> CODEC =
                StreamCodec.of((buf, value) -> {
                    buf.writeInt(value.worldX()); buf.writeInt(value.worldY()); buf.writeInt(value.worldZ());
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, value.source());
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, value.status());
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, value.path());
                    buf.writeBoolean(!value.activeCargo().isEmpty());
                    if (!value.activeCargo().isEmpty()) ItemStack.STREAM_CODEC.encode(buf, value.activeCargo());
                    buf.writeVarInt(value.activeAmount()); buf.writeBoolean(value.canClaim());
                    buf.writeBoolean(value.canStart());
                    int count = Math.min(4, value.offers().size());
                    buf.writeVarInt(count);
                    for (int i = 0; i < count; i++) RegionalDispatchOfferData.write(buf, value.offers().get(i));
                }, buf -> {
                    int x = buf.readInt(), y = buf.readInt(), z = buf.readInt();
                    Component source = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
                    Component status = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
                    Component path = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
                    ItemStack cargo = buf.readBoolean() ? ItemStack.STREAM_CODEC.decode(buf) : ItemStack.EMPTY;
                    int amount = buf.readVarInt();
                    boolean claim = buf.readBoolean();
                    boolean canStart = buf.readBoolean();
                    int count = Math.max(0, Math.min(4, buf.readVarInt()));
                    List<RegionalDispatchOfferData> offers = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) offers.add(RegionalDispatchOfferData.read(buf));
                    return new RegionalDispatchPayload(x, y, z, source, status, path, cargo, amount,
                            claim, canStart, List.copyOf(offers));
                });
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record RegionalDispatchActionPayload(int worldX, int worldY, int worldZ, int action, int routeIndex)
            implements CustomPacketPayload {
        public static final int START = 1, CLAIM = 2, REFRESH = 3;
        public static final Type<RegionalDispatchActionPayload> ID = new Type<>(
                Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "regional_dispatch_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RegionalDispatchActionPayload> CODEC =
                StreamCodec.of((buf, value) -> {
                    buf.writeInt(value.worldX()); buf.writeInt(value.worldY()); buf.writeInt(value.worldZ());
                    buf.writeVarInt(value.action()); buf.writeVarInt(value.routeIndex());
                }, buf -> new RegionalDispatchActionPayload(buf.readInt(), buf.readInt(), buf.readInt(),
                        buf.readVarInt(), buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record CaravanMasterPayload(UUID entityId, Component masterName, Component routeName,
                                       Component journey, int progressPercent, int etaSeconds,
                                       int roadQuality, Component roadStatus, Component availability,
                                       Component incident, Component cargo, Component quote,
                                       boolean canViewRoute) implements CustomPacketPayload {
        public static final Type<CaravanMasterPayload> ID = new Type<>(
                Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "caravan_master"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CaravanMasterPayload> CODEC =
                StreamCodec.of((buf, value) -> {
                    buf.writeUUID(value.entityId());
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, value.masterName());
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, value.routeName());
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, value.journey());
                    buf.writeVarInt(value.progressPercent()); buf.writeVarInt(value.etaSeconds());
                    buf.writeVarInt(value.roadQuality());
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, value.roadStatus());
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, value.availability());
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, value.incident());
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, value.cargo());
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, value.quote());
                    buf.writeBoolean(value.canViewRoute());
                }, buf -> new CaravanMasterPayload(buf.readUUID(),
                        ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                        ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                        ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                        ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                        ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                        ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                        ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record CaravanMasterActionPayload(UUID entityId, int action) implements CustomPacketPayload {
        public static final int VIEW_ROUTE = 1, REFRESH = 2;
        public static final Type<CaravanMasterActionPayload> ID = new Type<>(
                Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "caravan_master_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CaravanMasterActionPayload> CODEC =
                StreamCodec.of((buf, value) -> {
                    buf.writeUUID(value.entityId()); buf.writeVarInt(value.action());
                }, buf -> new CaravanMasterActionPayload(buf.readUUID(), buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record WayshrineTravelPayload(int currentIndex, int targetIndex, boolean useCharge)
            implements CustomPacketPayload {
        public static final Type<WayshrineTravelPayload> ID = new Type<>(
                Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "wayshrine_travel"));
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
        public static final Type<WayshrineRenamePayload> ID = new Type<>(
                Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "wayshrine_rename"));
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
}
