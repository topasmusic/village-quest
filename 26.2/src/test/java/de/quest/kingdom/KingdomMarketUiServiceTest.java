package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.network.MarketPayloads;
import de.quest.village.VillageLifeState.VillageKey;
import de.quest.village.VillageNeed;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class KingdomMarketUiServiceTest {
    private static final UUID ACTOR=new UUID(1,1),BODY=new UUID(2,2);
    private record Fixture(LivingKingdomsState state,UUID town,UUID trader,UUID visit) {}
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var proto=DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build();
        for(var item:BuiltInRegistries.ITEM) item.builtInRegistryHolder().bindComponents(proto);
    }
    @Test void displayedServerQuoteFeedsTheExactNativeInventoryReceiptAndCannotRepeat() {
        var f=fixture(); var session=session(f,ACTOR); var saved=LivingKingdomsState.toNbt(f.state);
        var view=KingdomMarketUiService.snapshot(f.state,session,true,100,0,20,false,MarketPayloads.Feedback.UPDATED).orElseThrow();
        assertEquals(saved,LivingKingdomsState.toNbt(f.state)); assertEquals(1,view.rows().size());
        var row=view.rows().getFirst(); assertEquals(2,row.stock()); assertEquals(4,row.demand());
        assertEquals(MarketPayloads.Feedback.BUYBACKS_UNAVAILABLE,view.feedback());
        assertEquals(MarketPayloads.NONE,row.sellQuote()); assertEquals(0,row.sellPrice());
        long revision=view.revision();
        var quote=session.take(ACTOR,session.id(),"minecraft:overworld",BODY,0,20,revision,row.buyQuote()).orElseThrow();
        assertEquals(row.buyPrice(),quote.price()); assertEquals(32,quote.quantity());
        var bag=new SimpleContainer(36);
        var plan=MarketInventoryPlan.plan(bag,new ItemStack(Items.WHEAT),quote.side(),quote.quantity()).orElseThrow();
        var receipt=f.state.markets().prepareNative(quote,ACTOR,100,bag,plan,RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY),20).orElseThrow();
        assertEquals(100-row.buyPrice(),receipt.afterBalance()); assertEquals(32,receipt.afterItems());
        assertTrue(session.take(ACTOR,session.id(),"minecraft:overworld",BODY,0,20,revision,row.buyQuote()).isEmpty());
    }
    @Test void privateRefreshPreservesOtherPlayersQuoteAndClosedVisitCannotProduceAView() {
        var f=fixture(); var first=session(f,ACTOR); var second=session(f,new UUID(3,3));
        var one=KingdomMarketUiService.snapshot(f.state,first,true,100,0,20,false,MarketPayloads.Feedback.UPDATED).orElseThrow();
        var two=KingdomMarketUiService.snapshot(f.state,second,true,100,0,20,false,MarketPayloads.Feedback.UPDATED).orElseThrow();
        assertTrue(first.take(ACTOR,first.id(),"minecraft:overworld",BODY,0,20,one.revision(),two.rows().getFirst().buyQuote()).isEmpty());
        assertTrue(first.take(ACTOR,first.id(),"minecraft:overworld",BODY,0,20,one.revision(),one.rows().getFirst().buyQuote()).isPresent());
        f.state.markets().close(f.town);
        assertTrue(KingdomMarketUiService.snapshot(f.state,second,false,100,0,20,false,MarketPayloads.Feedback.UPDATED).isEmpty());
    }
    @Test void offThreadPacketAndNpcOpenDoNotReadActorOrNpcState() {
        var server=org.mockito.Mockito.mock(net.minecraft.server.MinecraftServer.class);
        var actor=org.mockito.Mockito.mock(net.minecraft.server.level.ServerPlayer.class);
        var npc=org.mockito.Mockito.mock(de.quest.entity.KingdomNpcEntity.class);
        var world=org.mockito.Mockito.mock(net.minecraft.server.level.ServerLevel.class);
        org.mockito.Mockito.when(world.getServer()).thenReturn(server);
        org.mockito.Mockito.when(server.isSameThread()).thenReturn(false);
        KingdomMarketUiService.handle(server,actor,new MarketPayloads.Action(new UUID(4,4),0,MarketPayloads.Kind.REFRESH,MarketPayloads.NONE,1));
        assertFalse(KingdomMarketUiService.open(world,actor,npc));
        org.mockito.Mockito.verifyNoInteractions(actor,npc);
    }
    @Test void committedStockChangesAndPersonalTrustReachTheActualDisplayedQuoteWithoutRefilling() {
        var f=fixture(); var policy=session(f,ACTOR);
        var before=KingdomMarketUiService.snapshot(f.state,policy,true,100,0,20,false,0,MarketPayloads.Feedback.UPDATED).orElseThrow();
        var row=before.rows().getFirst(); assertEquals(0,row.adjustment().availability()); assertEquals(3,row.adjustment().need());
        assertEquals(MarketPricePolicy.sell(4,row.adjustment().buying(),0),row.buyPrice());
        var quote=policy.take(ACTOR,policy.id(),"minecraft:overworld",BODY,0,20,before.revision(),row.buyQuote()).orElseThrow();
        var receipt=f.state.markets().prepare(quote,ACTOR,100,0,true,20).orElseThrow();
        assertTrue(f.state.markets().commit(receipt.id(),receipt.afterBalance(),receipt.afterItems()));
        var after=KingdomMarketUiService.snapshot(f.state,policy,false,receipt.afterBalance(),15,21,false,2,MarketPayloads.Feedback.COMMITTED).orElseThrow();
        var next=after.rows().getFirst(); assertEquals(1,next.stock()); assertEquals(16,after.budget());
        assertEquals(3,next.adjustment().availability()); assertEquals(6,next.adjustment().trust());
        assertEquals(MarketPricePolicy.sell(4,next.adjustment().buying(),15),next.buyPrice());
        assertEquals(MarketPayloads.NONE,next.sellQuote()); assertEquals(0,next.sellPrice());
        assertEquals(MarketPayloads.Feedback.COMMITTED,after.feedback());
        var restored=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(f.state));
        var reopened=KingdomMarketUiService.snapshot(restored,session(f,ACTOR),true,receipt.afterBalance(),15,22,false,2,MarketPayloads.Feedback.UPDATED).orElseThrow();
        assertEquals(next.adjustment(),reopened.rows().getFirst().adjustment());
        assertEquals(1,reopened.rows().getFirst().stock()); assertEquals(16,reopened.budget());
        assertEquals(next.buyPrice(),reopened.rows().getFirst().buyPrice());
    }
    private static MarketSessionPolicy session(Fixture f,UUID actor) { return new MarketSessionPolicy(actor,"minecraft:overworld",f.trader,BODY,0,f.visit,20); }
    private static Fixture fixture() {
        var state=new LivingKingdomsState(); var town=UUID.randomUUID(); var owner=UUID.randomUUID(); var pos=new BlockPos(0,64,0);
        state.registerCustom(town,owner,VillageKey.overworld(0,0),pos,"Auenhof").orElseThrow();
        var report=new BuildingScanner.Report(BuildingScanner.Role.MARKET,BuildingScanner.Status.VALID,Map.of(),Map.of(),Set.of(pos.north()));
        state.anchors().bind(owner,town,0,UUID.randomUUID(),-1,pos,Direction.NORTH,BuildingScanner.Range.BASIC,report,0,20);
        var trader=state.people().ensure(town,KingdomPersonRegistry.Role.MARKET_TRADER,0).orElseThrow().id();
        state.markets().observeNeed(town,VillageNeed.GRANARY_SEED_RESERVES,0);
        var goods=new KingdomMarketLedger.Goods("grain",Identifier.withDefaultNamespace("wheat"),32,2,4,4,2);
        var visit=state.markets().start(town,List.of(new KingdomMarketLedger.TraderPlan(trader,town,List.of(goods))),20).orElseThrow();
        return new Fixture(state,town,trader,visit.id());
    }
}
