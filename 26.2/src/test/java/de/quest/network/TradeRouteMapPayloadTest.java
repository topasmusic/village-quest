package de.quest.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class TradeRouteMapPayloadTest {
    private static RegistryAccess registries;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    @Test
    void suspendedRouteAndCrewNamesSurviveNetworkRoundTrip() {
        var route = new Payloads.TradeRouteLineData(0, 3, Component.literal("Stoneford Road"),
                1, Component.literal("Dangerous"), Component.literal("Destination abandoned"),
                43, 6750, false, false, true, false,
                Component.empty(), Component.empty(), 40L, Component.literal("Courier"),
                Component.literal("Careful"), Component.literal("Wheels"),
                "Mira Bellweather", "Elric Fenner", "Torren Ashford", "Cira Wayfarer",
                List.of(new Payloads.TradeRoutePointData(10, 20, false)),
                300L, 400L, 900L, 23L, 3, true);
        var original = new Payloads.TradeRouteMapPayload(Payloads.TradeRouteMapPayload.ACTION_OPEN,
                Component.literal("Routes"), Component.literal("One route"),
                List.of(new Payloads.TradeRouteNodeData(1, Component.literal("Stoneford"),
                        10, 20, false, false, 1)),
                List.of(route), List.of(),
                List.of(new Payloads.TradeRouteBondData(0, 10, 20,
                        Component.literal("Granary"), Component.literal("Trusted"),
                        Component.literal("Bread"), 3, Component.literal("Thriving"),
                        Component.literal("Food"), 40, 1, 1, false,
                        Component.literal("Village professions"))),
                List.of(), List.of());
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);

        Payloads.TradeRouteMapPayload.CODEC.encode(buffer, original);
        var restored = Payloads.TradeRouteMapPayload.CODEC.decode(buffer);

        assertTrue(restored.routes().getFirst().settlementSuspended());
        assertEquals("Destination abandoned", restored.routes().getFirst().availabilityLabel().getString());
        assertEquals("Mira Bellweather", restored.routes().getFirst().masterName());
        assertEquals("Cira Wayfarer", restored.routes().getFirst().courierName());
        assertEquals(900L, restored.routes().getFirst().estimatedBlocks());
        assertEquals(23L, restored.routes().getFirst().estimatedMinutes());
        assertEquals(3, restored.routes().getFirst().expectedLegs());
        assertTrue(restored.routes().getFirst().unusuallyLong());
        assertEquals(1, restored.nodes().getFirst().lifeStatus());
        assertEquals(1, restored.bonds().getFirst().lifeStatus());
        assertTrue(!restored.bonds().getFirst().connected());
        assertEquals("Village professions", restored.bonds().getFirst().identityOrigin().getString());
        assertEquals(0, buffer.readableBytes());
    }
}
