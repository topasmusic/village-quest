package de.quest.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class CaravanMasterPayloadTest {
    private static RegistryAccess registries;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    @Test
    void interruptedJourneyAndCargoStayAttachedToTheInteractedEntity() {
        UUID entity = UUID.fromString("14fd2136-8543-4275-9b4c-980be63b3478");
        var original = new VillageNetworkPayloads.CaravanMasterPayload(entity,
                Component.literal("Mira Bellweather"), Component.literal("Granary Road"),
                Component.literal("Granary → Hub"), 63, -1, 75,
                Component.literal("Secured"), Component.literal("Destination abandoned"),
                Component.literal("Broken wheel"), Component.literal("8 bread for Forge"),
                Component.literal("The road needs repair."), true);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        VillageNetworkPayloads.CaravanMasterPayload.CODEC.encode(buffer, original);
        var decoded = VillageNetworkPayloads.CaravanMasterPayload.CODEC.decode(buffer);

        assertEquals(entity, decoded.entityId());
        assertEquals(-1, decoded.etaSeconds());
        assertEquals("Broken wheel", decoded.incident().getString());
        assertEquals("8 bread for Forge", decoded.cargo().getString());
        assertEquals(0, buffer.readableBytes());
        assertFalse(decoded.availability().getString().isBlank());
    }
}
