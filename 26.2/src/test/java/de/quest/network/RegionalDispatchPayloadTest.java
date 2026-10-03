package de.quest.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
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

final class RegionalDispatchPayloadTest {
    private static RegistryAccess registries;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Bootstrap.validate();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        Items.BREAD.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
    }

    @Test
    void hubHeldFreightAndSameIdentityDestinationsSurviveRoundTrip() {
        var first = new VillageNetworkPayloads.RegionalDispatchOfferData(1,
                Component.literal("Granary B"), new ItemStack(Items.BREAD), 8, 12, 12);
        var second = new VillageNetworkPayloads.RegionalDispatchOfferData(2,
                Component.literal("Granary C"), new ItemStack(Items.BREAD), 8, 12, 12);
        var payload = new VillageNetworkPayloads.RegionalDispatchPayload(10, 64, 20,
                Component.literal("Granary A"), Component.literal("Held at hub"),
                Component.literal("A → Hub → B"), new ItemStack(Items.BREAD), 8,
                false, false, List.of(first, second));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        VillageNetworkPayloads.RegionalDispatchPayload.CODEC.encode(buffer, payload);
        var decoded = VillageNetworkPayloads.RegionalDispatchPayload.CODEC.decode(buffer);

        assertEquals(2, decoded.offers().size());
        assertEquals("Granary C", decoded.offers().get(1).destination().getString());
        assertEquals("Held at hub", decoded.status().getString());
        assertEquals(8, decoded.activeAmount());
        assertFalse(decoded.canStart());
        assertTrue(decoded.activeCargo().is(Items.BREAD));
        assertEquals(0, buffer.readableBytes());
    }
}
