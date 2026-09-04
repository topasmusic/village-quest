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
        assertEquals(0, buffer.readableBytes());
    }

    private static VillageNetworkPayloads.NoticeBoardPayload payload(int offerCount) {
        List<VillageNetworkPayloads.NoticeBoardOfferData> offers = new ArrayList<>();
        for (int i = 0; i < offerCount; i++) {
            offers.add(new VillageNetworkPayloads.NoticeBoardOfferData(
                    i, Component.literal("Offer " + i), new ItemStack(Items.EMERALD),
                    i + 1, i, 20L + i, i * 2, i == 0, true));
        }
        return new VillageNetworkPayloads.NoticeBoardPayload(
                12, 64, -18,
                Component.literal("Village"), Component.literal("Known"),
                Component.literal("Stable"), Component.literal("Supplies"), 55,
                Component.literal("Request"), new ItemStack(Items.BREAD),
                12, 4, 30L, 99L, 2, 1, 8,
                Component.literal("Trusted"), Component.literal("Perk"),
                true, false, List.copyOf(offers),
                Component.literal("Standard profile follows offers"));
    }
}
