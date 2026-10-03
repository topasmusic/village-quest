package de.quest.network;

import de.quest.VillageQuest;
import de.quest.reputation.ReputationViewService.*;
import de.quest.reputation.ReputationViewService;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Numeric, versioned packets with hard limits checked before collection allocation. */
public final class ReputationPayloads {
    public static final UUID NONE = new UUID(0, 0);
    private ReputationPayloads() {}
    static int checkedCount(int count, int max) {
        if (count < 0 || count > max) throw new IllegalArgumentException("Invalid reputation collection size");
        return count;
    }
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> id(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, path));
    }
    private static void writeKey(RegistryFriendlyByteBuf buf, VillageKey key) {
        buf.writeUtf(key.dimension(), 96); buf.writeInt(key.anchorX()); buf.writeInt(key.anchorZ());
    }
    private static VillageKey readKey(RegistryFriendlyByteBuf buf) {
        return new VillageKey(buf.readUtf(96), buf.readInt(), buf.readInt());
    }
    private static void writeCase(RegistryFriendlyByteBuf buf, CaseView view) {
        buf.writeBoolean(view != null); if (view == null) return;
        buf.writeUUID(view.id()); buf.writeBoolean(view.major()); buf.writeVarInt(view.affectedCount());
        buf.writeVarInt(view.ticksRemaining()); buf.writeVarInt(view.selected()); buf.writeVarInt(view.offence());
        buf.writeVarInt(checkedCount(view.aid().size(), 3));
        for (var line : view.aid()) { buf.writeVarInt(line.material()); buf.writeVarInt(line.supplied()); buf.writeVarInt(line.required()); buf.writeVarInt(line.carried()); }
    }
    private static CaseView readCase(RegistryFriendlyByteBuf buf) {
        if (!buf.readBoolean()) return null;
        UUID id = buf.readUUID(); boolean major = buf.readBoolean(); int count = buf.readVarInt(), ticks = buf.readVarInt(), selected = buf.readVarInt(), offence = buf.readVarInt();
        if (count < 0 || ticks < 0 || ticks > 36_000 || selected < 0 || selected > 2 || offence < 0 || offence >= de.quest.reputation.SocialReputationRules.Offence.values().length)
            throw new IllegalArgumentException("Invalid reputation case");
        int size = checkedCount(buf.readVarInt(), 3); List<AidView> aid = new ArrayList<>(size);
        int seen = 0;
        for (int i = 0; i < size; i++) {
            int material = buf.readVarInt(), supplied = buf.readVarInt(), required = buf.readVarInt(), carried = buf.readVarInt();
            if (material < 0 || material > 2 || (seen & (1 << material)) != 0 || supplied < 0 || supplied > required || required < 0 || required > 64 || carried < 0)
                throw new IllegalArgumentException("Invalid reputation material");
            seen |= 1 << material; aid.add(new AidView(material, supplied, required, carried));
        }
        return new CaseView(id, major, count, ticks, selected, offence, aid);
    }
    private static void writeView(RegistryFriendlyByteBuf buf, View view) {
        buf.writeVarInt(view.schema()); buf.writeLong(view.revision()); buf.writeBoolean(view.enabled()); buf.writeBoolean(view.writable());
        buf.writeVarInt(view.guildTrust()); buf.writeVarInt(view.probation()); buf.writeVarInt(view.buyLimit());
        buf.writeBoolean(view.dispatchAllowed()); buf.writeBoolean(view.convoyAllowed()); buf.writeVarInt(view.rewardPercent());
        buf.writeVarInt(view.villagePage()); buf.writeVarInt(view.villageTotal());
        buf.writeVarInt(checkedCount(view.villages().size(), 8));
        for (var village : view.villages()) { writeKey(buf, village.key()); buf.writeVarInt(village.trust()); buf.writeBoolean(village.affected()); }
        buf.writeVarInt(checkedCount(view.history().size(), 32));
        for (var history : view.history()) {
            buf.writeUUID(history.id()); buf.writeUtf(history.event(), 64); buf.writeVarInt(history.guildDelta());
            buf.writeBoolean(history.village() != null); if (history.village() != null) writeKey(buf, history.village());
            buf.writeVarInt(history.localDelta()); buf.writeVarInt(history.affectedCount()); buf.writeLong(history.tick());
        }
        writeCase(buf, view.reparation());
    }
    private static View readView(RegistryFriendlyByteBuf buf) {
        int schema = buf.readVarInt(); if (schema != ReputationViewService.SCHEMA) throw new IllegalArgumentException("Unsupported reputation view schema");
        long revision = buf.readLong(); boolean enabled = buf.readBoolean(), writable = buf.readBoolean();
        int guild = buf.readVarInt(), probation = buf.readVarInt(), buys = buf.readVarInt();
        boolean dispatch = buf.readBoolean(), convoy = buf.readBoolean(); int bonus = buf.readVarInt(), page = buf.readVarInt(), total = buf.readVarInt();
        int count = checkedCount(buf.readVarInt(), 8); List<VillageView> villages = new ArrayList<>(count);
        for (int i = 0; i < count; i++) villages.add(new VillageView(readKey(buf), buf.readVarInt(), buf.readBoolean()));
        count = checkedCount(buf.readVarInt(), 32); List<HistoryView> history = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID id = buf.readUUID(); String event = buf.readUtf(64); int delta = buf.readVarInt();
            VillageKey village = buf.readBoolean() ? readKey(buf) : null;
            history.add(new HistoryView(id, event, delta, village, buf.readVarInt(), buf.readVarInt(), buf.readLong()));
        }
        return new View(schema, revision, enabled, writable, guild, probation, buys, dispatch, convoy, bonus, page, total, villages, history, readCase(buf));
    }
    public record ViewPayload(View view) implements CustomPacketPayload {
        public static final Type<ViewPayload> ID = id("reputation_view");
        public static final StreamCodec<RegistryFriendlyByteBuf, ViewPayload> CODEC = StreamCodec.of(
                (buf, value) -> writeView(buf, value.view()), buf -> new ViewPayload(readView(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public record PagePayload(int page) implements CustomPacketPayload {
        public static final Type<PagePayload> ID = id("reputation_page");
        public static final StreamCodec<RegistryFriendlyByteBuf, PagePayload> CODEC = StreamCodec.of(
                (buf, value) -> buf.writeVarInt(value.page()), buf -> new PagePayload(buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public record OpenPayload(int kind, BlockPos position, int entityId) implements CustomPacketPayload {
        public static final Type<OpenPayload> ID = id("reputation_open");
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenPayload> CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeVarInt(value.kind()); buf.writeBlockPos(value.position()); buf.writeVarInt(value.entityId()); },
                buf -> new OpenPayload(buf.readVarInt(), buf.readBlockPos(), buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public record ActionPayload(UUID session, UUID subject, long revision, int action, int material, int villagePage) implements CustomPacketPayload {
        public static final int CLOSE = 0, CHOOSE = 1, SUBMIT = 2, SUPPORT_ACCEPT = 3, SUPPORT_SUBMIT = 4, PAGE = 5;
        public static final Type<ActionPayload> ID = id("reputation_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeUUID(value.session()); buf.writeUUID(value.subject()); buf.writeLong(value.revision()); buf.writeVarInt(value.action()); buf.writeVarInt(value.material()); buf.writeVarInt(value.villagePage()); },
                buf -> new ActionPayload(buf.readUUID(), buf.readUUID(), buf.readLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public record SupportView(UUID id, VillageKey village, boolean accepted, boolean supplied, boolean barred) {}
    public record InteractionPayload(UUID session, boolean preview, boolean canReparate, View view, List<SupportView> support, String feedback) implements CustomPacketPayload {
        public InteractionPayload { support = List.copyOf(support); }
        public static final Type<InteractionPayload> ID = id("reputation_interaction");
        public static final StreamCodec<RegistryFriendlyByteBuf, InteractionPayload> CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.session()); buf.writeBoolean(value.preview()); buf.writeBoolean(value.canReparate()); writeView(buf, value.view());
                    buf.writeVarInt(checkedCount(value.support().size(), 8));
                    for (var offer : value.support()) { buf.writeUUID(offer.id()); writeKey(buf, offer.village()); buf.writeBoolean(offer.accepted()); buf.writeBoolean(offer.supplied()); buf.writeBoolean(offer.barred()); }
                    buf.writeUtf(value.feedback(), 128);
                }, buf -> {
                    UUID session = buf.readUUID(); boolean preview = buf.readBoolean(), canReparate = buf.readBoolean(); View view = readView(buf);
                    int count = checkedCount(buf.readVarInt(), 8); List<SupportView> support = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) support.add(new SupportView(buf.readUUID(), readKey(buf), buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));
                    return new InteractionPayload(session, preview, canReparate, view, support, buf.readUtf(128));
                });
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public record ClosePayload(UUID session) implements CustomPacketPayload {
        public static final Type<ClosePayload> ID = id("reputation_close");
        public static final StreamCodec<RegistryFriendlyByteBuf, ClosePayload> CODEC = StreamCodec.of(
                (buf, value) -> buf.writeUUID(value.session()), buf -> new ClosePayload(buf.readUUID()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
}
