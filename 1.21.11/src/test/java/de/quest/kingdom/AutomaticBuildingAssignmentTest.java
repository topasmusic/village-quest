package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import de.quest.network.BuildingPayloads;
import de.quest.data.*;
import de.quest.village.VillageLifeState.VillageKey;
import org.junit.jupiter.api.*;

/** Real assignment/registry state through the native desk adapter; no world or game loop. */
final class AutomaticBuildingAssignmentTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap(); }
    @Test void nativeDeskAutomaticallySelectsUniqueConnectionButRequiresAChoiceWhenANeighborAppears() throws Exception {
        var server=mock(MinecraftServer.class);var state=new LivingKingdomsState();UUID owner=UUID.randomUUID();
        var home=state.registerCustom(UUID.randomUUID(),owner,VillageKey.overworld(0,0),BlockPos.ZERO,"Home").orElseThrow();
        state.confirmZone(owner,home.id(),0,new LivingKingdomsState.Zone(-8,8,-4,4,-8,8));
        Fixture fixture;
        try(var states=mockStatic(LivingKingdomsState.class)) {
            states.when(() -> LivingKingdomsState.get(server)).thenReturn(state);
            fixture=desk(server,owner,new BlockPos(18,0,0),BuildingScanner.Role.INN);
        }
            fixture.offer();assertEquals(home.id(),fixture.policy.selected());
            var other=state.registerCustom(UUID.randomUUID(),owner,VillageKey.overworld(28,0),new BlockPos(28,0,0),"Other").orElseThrow();
            state.confirmZone(owner,other.id(),0,new LivingKingdomsState.Zone(20,36,-4,4,-8,8));
            fixture.offer();assertEquals(BuildingPayloads.NONE,fixture.policy.selected(),"A prior automatic choice is no explicit choice at a newly ambiguous border");
            assertTrue(fixture.policy.select(other.id()));set(fixture.desk,"explicitSelection",true);
            fixture.offer();assertEquals(other.id(),fixture.policy.selected());
    }
    @Test void repeatedConfirmationOfAnAlreadyRegisteredBuildingNeverMutatesOrRecordsAnotherQuestEvent() throws Exception {
        var server=mock(MinecraftServer.class);UUID owner=UUID.randomUUID();var state=new LivingKingdomsState();
        var home=state.registerCustom(UUID.randomUUID(),owner,VillageKey.overworld(0,0),BlockPos.ZERO,"Home").orElseThrow();
        state.confirmZone(owner,home.id(),0,new LivingKingdomsState.Zone(-8,8,-4,4,-8,8));
        var report=new BuildingScanner.Report(BuildingScanner.Role.INN,BuildingScanner.Status.VALID,Map.of(),Map.of(),Set.of());
        state.anchors().bind(owner,home.id(),1,UUID.randomUUID(),-1,BlockPos.ZERO,Direction.NORTH,BuildingScanner.Range.BASIC,report,0,0);
        var before=LivingKingdomsState.toNbt(state);
        Fixture fixture;
        try(var states=mockStatic(LivingKingdomsState.class)) {
            states.when(() -> LivingKingdomsState.get(server)).thenReturn(state);
            fixture=desk(server,owner,BlockPos.ZERO,BuildingScanner.Role.INN);
        }
        try(var quests=mockStatic(QuestState.class)) {
            var quest=mock(QuestState.class);quests.when(() -> QuestState.get(server)).thenReturn(quest);
            var data=new PlayerQuestData();data.setMilestoneFlag(KingdomBuildingService.BUILDINGS_UNLOCK,true);when(quest.getPlayerData(owner)).thenReturn(data);
            var world=mock(ServerLevel.class);when(world.getServer()).thenReturn(server);var player=mock(ServerPlayer.class);when(player.level()).thenReturn(world);when(player.getUUID()).thenReturn(owner);
            fixture.offer();
            var confirm=fixture.runtime.getClass().getDeclaredMethod("confirm",ServerPlayer.class,fixture.desk.getClass(),long.class);confirm.setAccessible(true);
            confirm.invoke(fixture.runtime,player,fixture.desk,50L);confirm.invoke(fixture.runtime,player,fixture.desk,55L);
            assertEquals(before,LivingKingdomsState.toNbt(state));
        }
    }
    private record Fixture(Object runtime,Object desk,BuildingSessionPolicy policy) {
        void offer() throws Exception { var method=runtime.getClass().getDeclaredMethod("offer",desk.getClass());method.setAccessible(true);method.invoke(runtime,desk); }
    }
    private static Fixture desk(MinecraftServer server,UUID owner,BlockPos pos,BuildingScanner.Role role) throws Exception {
        var rc=Class.forName(KingdomBuildingService.class.getName()+"$Runtime").getDeclaredConstructor(MinecraftServer.class);rc.setAccessible(true);
        var policy=new BuildingSessionPolicy(owner,"minecraft:overworld",pos,0);
        var dc=Class.forName(KingdomBuildingService.class.getName()+"$Desk").getDeclaredConstructor(BuildingSessionPolicy.class,BuildingScanner.Role.class,Direction.class);dc.setAccessible(true);
        return new Fixture(rc.newInstance(server),dc.newInstance(policy,role,Direction.NORTH),policy);
    }
    private static void set(Object obj,String field,Object value) throws Exception {var f=obj.getClass().getDeclaredField(field);f.setAccessible(true);f.set(obj,value);}
}
