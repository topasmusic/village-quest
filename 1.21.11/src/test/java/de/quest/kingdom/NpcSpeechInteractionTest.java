package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.entity.KingdomNpcEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.*;

/** Mocked message boundary only; no world, entity tick or in-game simulation is started. */
final class NpcSpeechInteractionTest {
    @BeforeAll static void registries() {net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
    @Test void sleepingStaffNeverReplyOrReachTheStoryAndMarketServices() {
        var npc=mock(KingdomNpcEntity.class);var actor=mock(ServerPlayer.class);var world=mock(ServerLevel.class);
        when(npc.isAlive()).thenReturn(true);when(npc.isSleeping()).thenReturn(true);
        try(var state=mockStatic(LivingKingdomsState.class);var speech=mockStatic(NpcSpeechService.class)) {
            KingdomNpcService.interact(world,actor,npc);
            state.verifyNoInteractions();speech.verifyNoInteractions();verifyNoInteractions(actor);
        }
    }
    @Test void speechSightAndCertifiedLocalBuildingSeparatelyGateFamiliarityAndStoryEvidence() {
        for(int mode=0;mode<8;mode++) {
            boolean spoken=(mode&1)!=0,sight=(mode&2)!=0,certified=(mode&4)!=0;
            var world=mock(ServerLevel.class);var server=mock(MinecraftServer.class);
            var actor=mock(ServerPlayer.class);var npc=mock(KingdomNpcEntity.class);
            var state=KingdomMarketPlacesTest.state(0);var town=KingdomMarketPlacesTest.TOWN;
            var person=state.people().ensure(town,KingdomPersonRegistry.Role.LEADER,0).orElseThrow();
            if(certified) {
                var pos=KingdomMarketPlacesTest.POS.east(8);
                var report=new BuildingScanner.Report(BuildingScanner.Role.COUNCIL,BuildingScanner.Status.VALID,java.util.Map.of(),java.util.Map.of(),java.util.Set.of(pos));
                assertEquals(KingdomAnchorRegistry.Result.APPLIED,state.anchors().bind(KingdomMarketPlacesTest.OWNER,town,state.village(town).orElseThrow().revision(),java.util.UUID.randomUUID(),-1,
                        pos,net.minecraft.core.Direction.NORTH,BuildingScanner.Range.BASIC,report,0,0));
            }
            var quests=de.quest.data.QuestState.TYPE.constructor().get();var id=java.util.UUID.randomUUID();
            var data=quests.getPlayerData(id);KingdomStoryProgress.bind(data,town,false);
            when(world.getServer()).thenReturn(server);when(server.overworld()).thenReturn(world);
            when(actor.getUUID()).thenReturn(id);when(actor.hasLineOfSight(npc)).thenReturn(sight);
            when(npc.isAlive()).thenReturn(true);when(npc.getLookControl()).thenReturn(mock(net.minecraft.world.entity.ai.control.LookControl.class));
            try(var service=mockStatic(KingdomNpcService.class,CALLS_REAL_METHODS);
                var states=mockStatic(LivingKingdomsState.class);var quest=mockStatic(de.quest.data.QuestState.class);
                var lives=mockStatic(de.quest.village.VillageLifeState.class);var artifacts=mockStatic(ArtifactJourneyService.class);
                var speech=mockStatic(NpcSpeechService.class);
                var crises=mockStatic(KingdomCrisisService.class);var autonomy=mockStatic(KingdomAutonomyService.class);var attacks=mockStatic(KingdomAttackService.class);
                var story=mockStatic(KingdomStoryService.class)) {
                service.when(() -> KingdomNpcService.available(world,npc)).thenReturn(java.util.Optional.of(person));
                service.when(() -> KingdomNpcService.refuses(world,actor,npc)).thenReturn(false);
                states.when(() -> LivingKingdomsState.get(server)).thenReturn(state);
                quest.when(() -> de.quest.data.QuestState.get(server)).thenReturn(quests);
                var life=mock(de.quest.village.VillageLifeState.class);
                lives.when(() -> de.quest.village.VillageLifeState.get(server)).thenReturn(life);
                when(life.status(any())).thenReturn(de.quest.village.VillageLifeState.Status.ACTIVE);
                speech.when(() -> NpcSpeechService.privateLine(eq(actor),eq(npc),any(Component.class),any(Component.class))).thenReturn(spoken);
                KingdomNpcService.interact(world,actor,npc);
                assertEquals(spoken&&sight,KingdomStaffDialogue.seen(data,person.id()));
                if(spoken&&sight&&certified) story.verify(() -> KingdomStoryService.recordConfirmed(world,actor,town,KingdomStoryProgress.Deed.MEET_LEADER,"dialogue:"+person.id()));
                else story.verifyNoInteractions();

            }
        }
    }
    @Test void chatFallbackAndBubbleTransportShareOneCooldown() {
        var npc=mock(KingdomNpcEntity.class);var actor=mock(ServerPlayer.class);var world=mock(ServerLevel.class);var server=mock(MinecraftServer.class);
        when(npc.level()).thenReturn(world);when(actor.level()).thenReturn(world);when(world.getServer()).thenReturn(server);
        when(server.isSameThread()).thenReturn(true);when(server.overworld()).thenReturn(world);when(world.getGameTime()).thenReturn(100L);
        when(npc.getDisplayName()).thenReturn(Component.literal("Mira"));
        when(npc.isAlive()).thenReturn(true);when(npc.getUUID()).thenReturn(java.util.UUID.randomUUID());when(actor.getUUID()).thenReturn(java.util.UUID.randomUUID());
        var line=Component.literal("Welcome");
        try(var network=mockStatic(ServerPlayNetworking.class)) {
            assertTrue(NpcSpeechService.privateLine(actor,npc,line,line));
            assertFalse(NpcSpeechService.privateLine(actor,npc,line,line));
            verify(actor,times(1)).sendSystemMessage(line);
            when(npc.isSleeping()).thenReturn(true);when(world.getGameTime()).thenReturn(900L);
            assertFalse(NpcSpeechService.privateLine(actor,npc,line,line));
            verify(actor,times(1)).sendSystemMessage(line);
        }
    }
    @Test void firstRefusalAfterGreetingHasIndependentPacingAndStaysPrivate() {
        var npc=mock(KingdomNpcEntity.class);var actor=mock(ServerPlayer.class);var world=mock(ServerLevel.class);var server=mock(MinecraftServer.class);
        when(npc.level()).thenReturn(world);when(actor.level()).thenReturn(world);when(world.getServer()).thenReturn(server);
        when(server.isSameThread()).thenReturn(true);when(server.overworld()).thenReturn(world);when(world.getGameTime()).thenReturn(100L);
        when(npc.getDisplayName()).thenReturn(Component.literal("Mira"));when(npc.isAlive()).thenReturn(true);
        when(npc.getUUID()).thenReturn(java.util.UUID.randomUUID());when(actor.getUUID()).thenReturn(java.util.UUID.randomUUID());
        var person=java.util.UUID.randomUUID();
        try(var network=mockStatic(ServerPlayNetworking.class)) {
            assertTrue(NpcSpeechService.privateLine(actor,npc,Component.literal("Welcome"),Component.literal("Welcome")));
            NpcSpeechService.privateRefusal(actor,npc,person);
            NpcSpeechService.privateRefusal(actor,npc,person);
            verify(actor,times(2)).sendSystemMessage(any(Component.class));
            when(actor.distanceToSqr(npc)).thenReturn(99999d);when(world.getGameTime()).thenReturn(500L);
            NpcSpeechService.privateRefusal(actor,npc,person);
            verify(actor,times(2)).sendSystemMessage(any(Component.class));
        }
    }

}
