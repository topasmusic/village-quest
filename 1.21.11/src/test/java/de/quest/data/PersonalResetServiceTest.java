package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.guild.VillageGuildState;
import de.quest.guildtown.GuildTownSharedState;
import de.quest.kingdom.LivingKingdomsState;
import de.quest.party.QuestPartyService;
import de.quest.village.LivingVillageNetworkState;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import org.junit.jupiter.api.*;

/** Native service and attachment/action gate tests. File I/O and party documents have separate native tests. */
final class PersonalResetServiceTest {
    @BeforeAll @SuppressWarnings("unchecked") static void bootstrap(){
        PersonalResetItemsTest.boot();
        try(var registry=mockStatic(AttachmentRegistry.class)) {
            var type=mock(AttachmentType.class);registry.when(()->AttachmentRegistry.create(any(),any())).thenReturn(type);
            PersonalResetService.initializeAttachment();
        }
    }
    static final class Fixture implements AutoCloseable {
        final PersonalResetIntegrationTest.Fixture f;
        final ServerLevel world=mock(ServerLevel.class);final MinecraftServer server=mock(MinecraftServer.class);
        final org.mockito.MockedStatic<QuestState> states=mockStatic(QuestState.class,CALLS_REAL_METHODS);
        Fixture() throws Exception {
            f=new PersonalResetIntegrationTest.Fixture();when(f.storage.player.level()).thenReturn(world);when(world.getServer()).thenReturn(server);
            when(world.registryAccess()).thenReturn(f.registries);when(server.overworld()).thenReturn(world);when(server.isSameThread()).thenReturn(true);
            states.when(()->QuestState.get(server)).thenAnswer(c->f.state);
            var target=(AttachmentTarget)f.storage.player;when(target.getAttached(any())).thenAnswer(c->f.proof);
            doAnswer(c->{f.proof=c.getArgument(1,net.minecraft.nbt.Tag.class).copy();return null;}).when(target).setAttached(any(),any());
            var mode=mock(ServerPlayerGameMode.class);var field=ServerPlayer.class.getField("gameMode");field.setAccessible(true);field.set(f.storage.player,mode);
            when(mode.getGameModeForPlayer()).thenReturn(net.minecraft.world.level.GameType.SURVIVAL);when(f.storage.player.isAlive()).thenReturn(true);
        }
        public void close(){states.close();}
    }
    @Test void missingUnknownOrForeignPlayerProofBlocksLoginBeforeAnyPlayerMutation() throws Exception {
        for(int variant=0;variant<3;variant++)try(var fixture=new Fixture()) {
            var f=fixture.f;var tx=f.next();f.port(tx).prepare(tx.receipt());
            if(variant==0)f.proof=null;
            if(variant==1)((CompoundTag)f.proof).putInt("schema",99);
            if(variant==2)((CompoundTag)f.proof).putString("actor",f.other.toString());
            var before=QuestState.toNbt(f.state);
            assertFalse(PersonalResetService.recoverBeforeActions(f.storage.player));assertTrue(PersonalResetService.blocksActions(fixture.world,f.actor));
            assertEquals(before,QuestState.toNbt(f.state));assertFalse(f.storage.ender[0].isEmpty());
        }
    }
    @Test void terminalAckCannotUnlockUntilActualCheckpointSucceedsAndReloadMustVerifyAgain() throws Exception {
        try(var fixture=new Fixture();var disk=mockStatic(PersonalResetSaveBarrier.class)) {
            var f=fixture.f;var tx=f.next();var port=f.port(tx);
            assertEquals(PersonalResetCoordinator.Result.COMMITTED,new PersonalResetCoordinator(port).execute(tx.receipt()));
            disk.when(()->PersonalResetSaveBarrier.persist(f.storage.player,true)).thenReturn(false);
            assertFalse(PersonalResetService.recoverBeforeActions(f.storage.player));assertTrue(PersonalResetService.blocksActions(fixture.world,f.actor));
            disk.when(()->PersonalResetSaveBarrier.persist(f.storage.player,true)).thenReturn(true);
            assertTrue(PersonalResetService.recoverBeforeActions(f.storage.player));assertFalse(PersonalResetService.blocksActions(fixture.world,f.actor));
            f.reload();assertTrue(PersonalResetService.blocksActions(fixture.world,f.actor));
            assertTrue(PersonalResetService.recoverBeforeActions(f.storage.player));assertFalse(PersonalResetService.blocksActions(fixture.world,f.actor));
        }
    }
    @Test void nativeServiceCommitsWithOwnerOnlySharedCleanupAndPreservesProjectEntitlements() throws Exception {
        try(var fixture=new Fixture();var disk=mockStatic(PersonalResetSaveBarrier.class);var intro=mockStatic(de.quest.shrine.VillageIntroRequestService.class);
            var guilds=mockStatic(VillageGuildState.class,CALLS_REAL_METHODS);var shareds=mockStatic(GuildTownSharedState.class,CALLS_REAL_METHODS);
            var networks=mockStatic(LivingVillageNetworkState.class,CALLS_REAL_METHODS);var kingdoms=mockStatic(LivingKingdomsState.class,CALLS_REAL_METHODS);
            var party=mockStatic(QuestPartyService.class);var routes=mockStatic(de.quest.caravan.TradeRouteService.class);
            var shadows=mockStatic(de.quest.content.story.ShadowsTradeRoadEncounterService.class);var empty=mockStatic(de.quest.content.story.EmptyCaravanStoryService.class);
            var lutte=mockStatic(de.quest.quest.special.LutteCoastalQuest.class);var friends=mockStatic(de.quest.quest.special.FriendsQuestUiService.class);
            var master=mockStatic(de.quest.questmaster.QuestMasterUiService.class);var rewards=mockStatic(de.quest.reward.VanillaRewardService.class);
            var drops=mockStatic(de.quest.quest.QuestDropTracker.class);var harvest=mockStatic(de.quest.quest.QuestHarvestTracker.class);
            var tracker=mockStatic(de.quest.quest.QuestTrackerService.class);var books=mockStatic(de.quest.quest.QuestBookHelper.class)) {
            var f=fixture.f;var server=fixture.server;var world=fixture.world;
            var guild=VillageGuildState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();guild.create(f.actor,"Reset safety");
            guild.invite(f.actor,f.other);guild.accept(f.other);
            var shared=GuildTownSharedState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();var guildId=guild.guildFor(f.actor).orElseThrow().id();
            shared.accept(guildId,de.quest.guildtown.GuildTownSharedProject.ROAD_WAYMARKERS);
            shared.contribute(guildId,f.actor,java.util.UUID.randomUUID(),48);assertEquals(1,shared.pendingClaims(f.actor));
            var sharedBefore=GuildTownSharedState.TYPE.codec().encodeStart(NbtOps.INSTANCE,shared).getOrThrow();
            var network=LivingVillageNetworkState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
            var kingdom=LivingKingdomsState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
            guilds.when(()->VillageGuildState.get(server)).thenReturn(guild);shareds.when(()->GuildTownSharedState.get(server)).thenReturn(shared);
            networks.when(()->LivingVillageNetworkState.get(server)).thenReturn(network);kingdoms.when(()->LivingKingdomsState.get(server)).thenReturn(kingdom);
            var detached=new java.util.concurrent.atomic.AtomicBoolean();
            party.when(()->QuestPartyService.detachForPersonalRestart(world,f.actor)).thenAnswer(c->{detached.set(true);return null;});
            party.when(()->QuestPartyService.personalRestartDetached(world,f.actor)).thenAnswer(c->detached.get());
            intro.when(()->de.quest.shrine.VillageIntroRequestService.recoverBeforeActions(f.storage.player)).thenReturn(true);
            disk.when(()->PersonalResetSaveBarrier.persist(f.storage.player,false)).thenReturn(true);
            disk.when(()->PersonalResetSaveBarrier.persist(f.storage.player,true)).thenReturn(true);
            assertTrue(PersonalResetService.restart(f.storage.player));assertFalse(PersonalResetService.blocksActions(world,f.actor));
            assertEquals(2,f.state.getPlayerData(f.actor).progressGeneration());assertEquals(99,f.state.getPlayerData(f.other).getCurrencyBalance());
            assertTrue(guild.guildFor(f.actor).isEmpty());assertEquals(de.quest.guild.VillageGuildRole.LEADER,guild.guildFor(f.other).orElseThrow().role(f.other));
            assertEquals(sharedBefore,GuildTownSharedState.TYPE.codec().encodeStart(NbtOps.INSTANCE,shared).getOrThrow());
            assertEquals(1,shared.pendingClaims(f.actor));routes.verify(()->de.quest.caravan.TradeRouteService.removeOwnerRuntimes(world,f.actor));
            books.verify(()->de.quest.quest.QuestBookHelper.refreshQuestBook(world,f.storage.player));
            assertTrue(PersonalResetService.recoverBeforeActions(f.storage.player));
        }
    }
}
