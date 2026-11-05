package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.guild.VillageGuildState;
import de.quest.guildtown.*;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import org.junit.jupiter.api.*;

final class FinalAuditResetTest {
    @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
    @Test void unfinishedLastMemberProjectBlocksResetWithoutLosingContributionsAndEarnedClaims() throws Exception {
        var server=mock(MinecraftServer.class);var world=mock(ServerLevel.class);var player=mock(ServerPlayer.class);var actor=UUID.randomUUID();
        when(player.level()).thenReturn(world);when(world.getServer()).thenReturn(server);when(player.getUUID()).thenReturn(actor);
        var gc=VillageGuildState.class.getDeclaredConstructor();gc.setAccessible(true);var guilds=gc.newInstance();
        var sc=GuildTownSharedState.class.getDeclaredConstructor();sc.setAccessible(true);var shared=sc.newInstance();var guild=guilds.create(actor,"Test");
        shared.accept(guild.id(),GuildTownSharedProject.COMMON_STOREHOUSE);
        shared.contribute(guild.id(),actor,UUID.randomUUID(),96);
        shared.accept(guild.id(),GuildTownSharedProject.ROAD_WAYMARKERS);
        shared.contribute(guild.id(),actor,UUID.randomUUID(),12);
        try(var g=mockStatic(VillageGuildState.class,CALLS_REAL_METHODS);var s=mockStatic(GuildTownSharedState.class,CALLS_REAL_METHODS)){
            g.when(()->VillageGuildState.get(server)).thenReturn(guilds);s.when(()->GuildTownSharedState.get(server)).thenReturn(shared);
            assertTrue(PersonalResetService.unfinishedLastGuildProject(player));
            assertEquals(12,shared.project(guild.id()).orElseThrow().progress());assertEquals(1,shared.pendingClaims(actor));
            shared.contribute(guild.id(),actor,UUID.randomUUID(),36);
            assertFalse(PersonalResetService.unfinishedLastGuildProject(player));
            guilds.detachForPersonalRestart(actor);assertEquals(2,shared.pendingClaims(actor));
            assertNotNull(shared.claimForParticipant(actor,null));assertNotNull(shared.claimForParticipant(actor,null));assertNull(shared.claimForParticipant(actor,null));
        }
    }
    @Test void searchDeadlineSurvivesNativeSaveReloadAndLegacyDefaultIsZero(){
        var state=QuestState.fromNbt(new net.minecraft.nbt.CompoundTag());var actor=UUID.randomUUID();
        assertEquals(0,state.getPlayerData(actor).getSurveyorCompassSearchCooldownUntil());
        state.getPlayerData(actor).setSurveyorCompassSearchCooldownUntil(1450);
        var loaded=QuestState.fromNbt(QuestState.toNbt(state));assertEquals(1450,loaded.getPlayerData(actor).getSurveyorCompassSearchCooldownUntil());
    }
}
