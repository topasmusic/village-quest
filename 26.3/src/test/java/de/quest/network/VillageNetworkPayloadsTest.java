package de.quest.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class VillageNetworkPayloadsTest {
    private static RegistryAccess registries;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Bootstrap.validate();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        Items.EMERALD.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        Items.BREAD.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
    }

    @Test
    void noticeBoardRoundTripsZeroOffers() {
        assertRoundTrip(0, 0);
    }
    @Test void introActionsPreserveAllAuthorityTokensAndLegacyActionsHaveNone() {
        var token=new VillageNetworkPayloads.IntroToken(7,java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),1);
        for (int action=3;action<=6;action++) {
            var original=new VillageNetworkPayloads.NoticeBoardActionPayload(13,64,-72,action,2,token);
            var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),registries);
            VillageNetworkPayloads.NoticeBoardActionPayload.CODEC.encode(buffer,original);
            assertEquals(original,VillageNetworkPayloads.NoticeBoardActionPayload.CODEC.decode(buffer));
            assertEquals(0,buffer.readableBytes()); buffer.release();
        }
        var original=new VillageNetworkPayloads.NoticeBoardActionPayload(13,64,-72,1,2);
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),registries);
        VillageNetworkPayloads.NoticeBoardActionPayload.CODEC.encode(buffer,original);
        assertEquals(original,VillageNetworkPayloads.NoticeBoardActionPayload.CODEC.decode(buffer)); buffer.release();
    }
    @Test void introBoardAndEmbeddedJourneyPreserveActiveEpisodeAndReplacement() {
        var p=payload(3);
        for (boolean replacement:java.util.List.of(false,true)) {
            var intro=new VillageNetworkPayloads.IntroBoardData(new VillageNetworkPayloads.IntroToken(12,
                    java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),1),replacement ? -1 : 2,replacement,Component.literal("No repeat payment"));
            var board=new VillageNetworkPayloads.NoticeBoardPayload(p.worldX(),p.worldY(),p.worldZ(),p.villageType(),p.bondLevel(),
                    p.villageCondition(),p.villageNeed(),p.villageSupport(),p.requestTitle(),p.requestStack(),p.requiredAmount(),
                    p.inventoryAmount(),p.reward(),p.balance(),p.completions(),p.bondTier(),p.nextThreshold(),p.nextLevel(),p.nextPerk(),
                    p.requestAvailable(),p.canDeliver(),p.offers(),p.adventureProfile(),p.nextRequest(),intro,-70,true,
                    new de.quest.reputation.VillageRelationship.Hints(true,true,2,Component.literal("Bond progress"),Component.literal("Attack repair")));
            var journey=new VillageNetworkPayloads.NoticeJourneyPayload(12,64,34,0,Component.empty(),Component.empty(),Component.empty(),
                    0,0,0,0,false,ItemStack.EMPTY,0,board,false);
            var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),registries);
            VillageNetworkPayloads.NoticeJourneyPayload.CODEC.encode(buffer,journey);
            var decodedBoard=VillageNetworkPayloads.NoticeJourneyPayload.CODEC.decode(buffer).requests();
            assertEquals(-70,decodedBoard.socialTrust());assertEquals(true,decodedBoard.socialCase());assertEquals(board.relationship(),decodedBoard.relationship());
            var decoded=decodedBoard.intro();
            assertEquals(intro.token(),decoded.token()); assertEquals(intro.activeId(),decoded.activeId());
            assertEquals(replacement,decoded.replacement()); assertEquals(intro.detail().getString(),decoded.detail().getString());
            assertEquals(0,buffer.readableBytes()); buffer.release();
        }
    }

    @Test
    void noticeBoardRoundTripsNormalThreeOffers() {
        assertRoundTrip(3, 3);
    }

    @Test
    void noticeBoardRoundTripsExactlyMaximumOffers() {
        int maximum = VillageNetworkPayloads.MAX_NOTICE_BOARD_OFFERS;
        assertRoundTrip(maximum, maximum);
    }

    @Test
    void noticeBoardClampsExcessOffersWithoutCorruptingFollowingField() {
        assertRoundTrip(VillageNetworkPayloads.MAX_NOTICE_BOARD_OFFERS + 4,
                VillageNetworkPayloads.MAX_NOTICE_BOARD_OFFERS);
    }

    @Test
    void noticeJourneyRoundTripsStoryAndEmbeddedRequests() {
        var original = new VillageNetworkPayloads.NoticeJourneyPayload(
                12, 64, -18, VillageNetworkPayloads.NoticeJourneyPayload.READY,
                Component.literal("Granary"), Component.literal("Shared Table"),
                Component.literal("Bring the bundle"), 24, 24, 1, 1,
                true, new ItemStack(Items.BREAD), 8, payload(3), false);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        VillageNetworkPayloads.NoticeJourneyPayload.CODEC.encode(buffer, original);
        var decoded = VillageNetworkPayloads.NoticeJourneyPayload.CODEC.decode(buffer);
        assertEquals(original.stage(), decoded.stage());
        assertEquals("Shared Table", decoded.title().getString());
        assertEquals(8, decoded.deliveryCount());
        assertEquals(3, decoded.requests().offers().size());
        assertEquals(false, decoded.preview());
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void noticeJourneyPreviewRoundTripsWithoutRequests() {
        var original = new VillageNetworkPayloads.NoticeJourneyPayload(
                0, 70, 0, VillageNetworkPayloads.NoticeJourneyPayload.INTRO,
                Component.literal("Village"), Component.literal("Welcome"), Component.literal("Meet three villagers"),
                2, 3, 0, 0, false, ItemStack.EMPTY, 0, null, true);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        VillageNetworkPayloads.NoticeJourneyPayload.CODEC.encode(buffer, original);
        var decoded = VillageNetworkPayloads.NoticeJourneyPayload.CODEC.decode(buffer);
        assertEquals(true, decoded.preview());
        assertEquals(null, decoded.requests());
        assertEquals(2, decoded.first());
        assertEquals(0, buffer.readableBytes());
    }

    private static void assertRoundTrip(int offered, int expected) {
        VillageNetworkPayloads.NoticeBoardPayload original = payload(offered);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);

        VillageNetworkPayloads.NoticeBoardPayload.CODEC.encode(buffer, original);
        VillageNetworkPayloads.NoticeBoardPayload decoded =
                VillageNetworkPayloads.NoticeBoardPayload.CODEC.decode(buffer);

        assertEquals(expected, decoded.offers().size());
        for (int i = 0; i < expected; i++) {
            assertEquals(i, decoded.offers().get(i).id());
            assertEquals("Offer " + i, decoded.offers().get(i).title().getString());
        }
        assertEquals("Standard profile follows offers", decoded.adventureProfile().getString());
        assertEquals("Next request in 0.25 Minecraft days", decoded.nextRequest().getString());
        assertEquals(0, buffer.readableBytes());
    }

    private static VillageNetworkPayloads.NoticeBoardPayload payload(int offerCount) {
        return payload(offerCount, 55);
    }

    @Test
    void localRequestsKeepAbsentNetworkDataThroughTheWire() {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        VillageNetworkPayloads.NoticeBoardPayload.CODEC.encode(buffer, payload(3, -1));
        var decoded = VillageNetworkPayloads.NoticeBoardPayload.CODEC.decode(buffer);
        org.junit.jupiter.api.Assertions.assertFalse(decoded.networkConnected());
        assertEquals(-1, decoded.villageSupport());
        assertEquals(3, decoded.offers().size());
        assertEquals(0, buffer.readableBytes());
    }

    private static VillageNetworkPayloads.NoticeBoardPayload payload(int offerCount, int supply) {
        List<VillageNetworkPayloads.NoticeBoardOfferData> offers = new ArrayList<>();
        for (int i = 0; i < offerCount; i++) {
            offers.add(new VillageNetworkPayloads.NoticeBoardOfferData(
                    i, Component.literal("Offer " + i), new ItemStack(Items.EMERALD),
                    i + 1, i, 20L + i, i * 2, i == 0, true));
        }
        return new VillageNetworkPayloads.NoticeBoardPayload(
                12, 64, -18,
                Component.literal("Village"), Component.literal("Known"),
                Component.literal("Stable"), Component.literal("Supplies"), supply,
                Component.literal("Request"), new ItemStack(Items.BREAD),
                12, 4, 30L, 99L, 2, 1, 8,
                Component.literal("Trusted"), Component.literal("Perk"),
                true, false, List.copyOf(offers),
                Component.literal("Standard profile follows offers"), Component.literal("Next request in 0.25 Minecraft days"));
    }
}
