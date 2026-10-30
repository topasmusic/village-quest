package de.quest.shrine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.data.*;
import de.quest.kingdom.LivingKingdomsState;
import de.quest.quest.QuestGuidanceService;
import de.quest.village.VillageLifeState;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Real journal, inventory plans, native reload, Trust and authority. No world/runtime simulation.
 * Starts with already earned Daily/Welcome evidence; checkpoint is an in-memory codec boundary,
 * not a physical player-file or crash test. */
final class EarlyVillageJourneyTest {
    @BeforeAll static void bootstrap() {
        VillageIntroTransactionTest.bootstrap();
        for(var item:List.of(Items.OAK_LOG,Items.COBBLESTONE))
            item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                    .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64).build());
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void realStarterAbgabenAndEitherTrustPathReachMandateWithoutRouteOrCycleChange(boolean trades) {
        QuestState[] quests={QuestState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow()};
        var owner=UUID.randomUUID(); var kingdoms=new LivingKingdomsState();
        var village=kingdoms.registerGenerated(VillageLifeState.VillageKey.overworld(0,0),new BlockPos(0,64,0),"Start").orElseThrow();
        var data=quests[0].getPlayerData(owner);
        var contact=VillageContactService.establish(data,0,0,VillageBondType.GRANARY).contact();
        int index=contact.villageIndex(); String base=VillageBondService.villageKey(index,"");
        data.setTradeRouteString(base+"canonical_id",village.id().toString());
        data.setTradeRouteFlag("guild_intro.first_daily_completed",true);
        data.setTradeRouteFlag("guild_intro.welcome_completed",true);
        data.setTradeRouteInt("guild_intro.welcome_village",index+1);
        data.setLastRewardDay(77); data.setTradeRouteInt(base+"request_day",77);
        var progress=VillageIntroRequestProgress.open(data,village.id(),0,false);
        assertEquals(3,progress.offers(0,false).size());
        assertFalse(kingdoms.canManageVillage(owner,village.id()));
        var bag=new SimpleContainer(36); bag.setItem(0,new ItemStack(Items.OAK_LOG,16));
        bag.setItem(1,new ItemStack(Items.COBBLESTONE,16));
        CompoundTag[] proof={null};
        int completedMask=0,paidMask=0;
        for(var type:trades?List.of(VillageIntroRequestType.REPAIR_TIMBER):
                List.of(VillageIntroRequestType.REPAIR_TIMBER,VillageIntroRequestType.PATH_STONE)) {
            data=quests[0].getPlayerData(owner);
            int c=VillageBondProgression.snapshot(data,index).actualCompletions();
            progress=VillageIntroRequestProgress.open(data,village.id(),c,false);
            assertTrue(progress.accept(type,1,c,false));
            var tx=VillageIntroTransaction.prepare(UUID.randomUUID(),owner,data.progressGeneration(),progress.episode(),village.id(),
                    VillageIntroInventoryPlan.plan(bag,type).orElseThrow(),c,data.getCurrencyBalance(),
                    data.getPilgrimInt("prosperity.stat.earned"),completedMask,paidMask,VillageIntroTransactionTest.registries()).orElseThrow();
            var port=new VillageIntroJournalPort(quests[0],owner,index,bag,VillageIntroTransactionTest.registries(),
                    new VillageIntroJournalPort.PlayerProof() {
                        public net.minecraft.nbt.Tag get() { return proof[0]==null?null:proof[0].copy(); }
                        public void set(CompoundTag tag) { proof[0]=tag.copy(); }
                    },()->QuestState.TYPE.codec().encodeStart(NbtOps.INSTANCE,quests[0]).result().isPresent(),tx);
            assertEquals(VillageIntroDeliveryCoordinator.Result.COMMITTED,new VillageIntroDeliveryCoordinator(port).execute(tx.receipt()));
            long paid=quests[0].getPlayerData(owner).getCurrencyBalance();
            assertEquals(VillageIntroDeliveryCoordinator.Result.COMMITTED,new VillageIntroDeliveryCoordinator(port).execute(tx.receipt()));
            assertEquals(paid,quests[0].getPlayerData(owner).getCurrencyBalance());
            completedMask|=type.bit(); paidMask|=type.bit();
            quests[0]=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,QuestState.TYPE.codec().encodeStart(NbtOps.INSTANCE,quests[0]).getOrThrow()).getOrThrow();
            assertEquals(c+1,VillageBondProgression.snapshot(quests[0].getPlayerData(owner),index).actualCompletions());
        }
        data=quests[0].getPlayerData(owner);
        if(trades) {
            assertEquals(VillageBondLevel.KNOWN,VillageBondService.knownBondLevel(data,village.id()));
            var first=UUID.randomUUID(); var second=UUID.randomUUID();
            for(int i=0;i<4;i++) assertTrue(VillageTradeTrustService.recordTrade(data,index,village.id(),i<2?first:second,77,UUID.randomUUID()));
        }
        assertEquals(VillageBondLevel.TRUSTED,VillageBondService.knownBondLevel(data,village.id()));
        assertEquals(QuestGuidanceService.Status.READY,QuestGuidanceService.localTrust(data,index,kingdoms).status());
        assertTrue(VillageIntroRequestProgress.open(data,village.id(),trades?1:2,true).offers(trades?1:2,true).isEmpty());
        assertEquals(77,data.getLastRewardDay()); assertEquals(77,data.getTradeRouteInt(base+"request_day"));
        assertEquals(0,data.getTradeRouteInt("route_count")); assertEquals(0,data.getTradeRouteInt("guild_contracts_completed"));
        assertFalse(kingdoms.canManageVillage(owner,village.id()),"Trust alone never grants administration");
        assertTrue(kingdoms.recognizeMediator(village.id(),owner,true)); assertTrue(kingdoms.canManageVillage(owner,village.id()));
        var server=mock(MinecraftServer.class); var world=mock(ServerLevel.class); when(world.getServer()).thenReturn(server);
        try(var state=mockStatic(QuestState.class,CALLS_REAL_METHODS)) {
            state.when(()->QuestState.get(server)).thenReturn(quests[0]);
            assertFalse(de.quest.caravan.TradeRouteService.hasRouteAccess(world,owner),"Mandate does not invent a charter or route permit");
        }
    }
}
