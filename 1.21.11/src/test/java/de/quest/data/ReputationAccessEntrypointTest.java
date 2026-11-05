package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.caravan.GuildConvoyService;
import de.quest.reputation.ReputationAccessService;
import de.quest.reputation.SocialReputationRules.*;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ReputationAccessEntrypointTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void directConvoyStartAndJoinStopBeforeAnyGuildOrConvoyMutation() {
        MinecraftServer server = mock(MinecraftServer.class); ServerLevel world = mock(ServerLevel.class); ServerPlayer player = mock(ServerPlayer.class);
        when(world.getServer()).thenReturn(server); when(server.overworld()).thenReturn(world); when(player.level()).thenReturn(world);
        UUID id = UUID.randomUUID(); when(player.getUUID()).thenReturn(id);
        QuestState state = QuestState.fromNbt(new CompoundTag()); var data = state.getPlayerData(id).socialReputation();
        data.setGuildTrust(80); var village = VillageKey.overworld(100, 200);
        data.openCase(UUID.randomUUID(), Offence.ASSAULT, village, 1);
        try (var lookup = mockStatic(QuestState.class)) {
            lookup.when(() -> QuestState.get(server)).thenReturn(state);
            assertEquals(0, GuildConvoyService.start(world, player)); assertEquals(0, GuildConvoyService.join(world, player));
            assertFalse(ReputationAccessService.require(world, player, ServiceKind.NOTICE_ACCEPT, village));
            assertTrue(ReputationAccessService.require(world, player, ServiceKind.NOTICE_ACCEPT, VillageKey.overworld(999, 999)));
            verify(world, never()).getDataStorage(); assertEquals(80, data.guildTrust());
        }
    }
}
