package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.data.QuestState;
import de.quest.village.VillageLifeState.VillageKey;
import de.quest.village.VillageNeed;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/** Invokes the native adapter with mocked world queries; no server or world runs. */
final class KingdomCrisisServiceTest {
    @Test void unrelatedManagedCouncilCannotAdvanceTheCaseButBothParticipatingCouncilsCan() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        var real=new LivingKingdomsState(); UUID actor=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
        real.registerCustom(a,actor,VillageKey.overworld(0,0),new BlockPos(0,64,0),"A").orElseThrow();
        real.registerCustom(b,UUID.randomUUID(),VillageKey.overworld(100,0),new BlockPos(100,64,0),"B").orElseThrow();
        real.registerCustom(c,actor,VillageKey.overworld(200,0),new BlockPos(200,64,0),"C").orElseThrow();
        var ownerB=real.village(b).orElseThrow().founder();
        var offer=real.diplomacy().offer(actor,a,b,0,0,KingdomDiplomacy.Type.SUPPLY,-1,KingdomDiplomacy.Terms.NEEDS_FIRST).orElseThrow();
        real.diplomacy().accept(ownerB,offer.id(),offer.revision()).orElseThrow();
        real.crises().start(actor,a,b,VillageNeed.GRANARY_PANTRY,3,1,false).orElseThrow();
        var state=spy(real); var anchors=mock(KingdomAnchorRegistry.class); doReturn(anchors).when(state).anchors();
        var anchor=mock(KingdomAnchorRegistry.AnchorView.class); when(anchor.role()).thenReturn(BuildingScanner.Role.COUNCIL);
        when(anchor.id()).thenReturn(UUID.randomUUID()); when(anchor.position()).thenReturn(BlockPos.ZERO);
        when(anchors.near(anyString(),any(BlockPos.class),eq(64),eq(8))).thenReturn(List.of(anchor));
        when(anchors.maySpawn(any(UUID.class),anyLong())).thenReturn(true);
        var server=mock(MinecraftServer.class); var world=mock(ServerLevel.class); var player=mock(ServerPlayer.class);
        when(server.isSameThread()).thenReturn(true); when(server.overworld()).thenReturn(world);
        when(world.getServer()).thenReturn(server); when(world.getGameTime()).thenReturn(100L);
        when(world.dimension()).thenReturn(Level.OVERWORLD); when(world.hasChunkAt(any(BlockPos.class))).thenReturn(true);
        when(player.level()).thenReturn(world); when(player.getUUID()).thenReturn(actor);
        when(player.blockPosition()).thenReturn(BlockPos.ZERO); when(player.isAlive()).thenReturn(true);
        var quests=QuestState.TYPE.constructor().get();
        try(var states=mockStatic(LivingKingdomsState.class); var documents=mockStatic(QuestState.class);
            var autonomy=mockStatic(KingdomAutonomyService.class); var demand=mockStatic(KingdomMarketDemandService.class)) {
            states.when(() -> LivingKingdomsState.get(server)).thenReturn(state);
            documents.when(() -> QuestState.get(server)).thenReturn(quests);
            demand.when(() -> KingdomMarketDemandService.current(any(ServerPlayer.class),any(UUID.class))).thenReturn(Optional.empty());
            when(anchor.villageId()).thenReturn(c); var before=real.crises().current(actor).orElseThrow();
            KingdomCrisisService.observe(player);
            assertEquals(before,real.crises().current(actor).orElseThrow(),"Council C must not alter an A/B crisis");
            autonomy.verifyNoInteractions(); // Saved-world scheduler owns initiative; local council observes only this case.
            for(UUID participating:List.of(a,b)) {
                when(anchor.villageId()).thenReturn(participating);
                int remaining=real.crises().current(actor).orElseThrow().remaining();
                KingdomCrisisService.observe(player);
                assertEquals(remaining-20,real.crises().current(actor).orElseThrow().remaining());
            }
            when(world.hasChunkAt(any(BlockPos.class))).thenReturn(false);
            before=real.crises().current(actor).orElseThrow(); KingdomCrisisService.observe(player);
            assertEquals(before,real.crises().current(actor).orElseThrow(),"Unknown chunks pause time");
            when(world.hasChunkAt(any(BlockPos.class))).thenReturn(true); when(player.isAlive()).thenReturn(false);
            KingdomCrisisService.observe(player); assertEquals(before,real.crises().current(actor).orElseThrow());
            when(player.isAlive()).thenReturn(true); when(player.getLastHurtByMob()).thenReturn(mock(net.minecraft.world.entity.monster.Monster.class));
            KingdomCrisisService.observe(player); assertEquals(before,real.crises().current(actor).orElseThrow(),"Recent combat pauses time");
        }
    }
}
