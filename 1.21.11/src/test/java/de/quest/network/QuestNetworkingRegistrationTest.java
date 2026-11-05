package de.quest.network;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Registration only: no Minecraft client, server, level or world is started. */
final class QuestNetworkingRegistrationTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test void payloadTypesExistBeforeTheirServerReceiversAreRegistered() {
        assertDoesNotThrow(QuestNetworking::register);
    }
}
