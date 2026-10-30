package de.quest.kingdom;

import de.quest.data.QuestState;
import de.quest.economy.CurrencyService;
import de.quest.entity.KingdomNpcEntity;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Native money/inventory/save bridge. Offers and private GUI sessions are supplied by the market UI service. */
public final class KingdomMarketTransferService {
    private static final AttachmentType<Tag> PROOF=AttachmentRegistry.create(Identifier.parse(MarketSavedCheckpoint.PLAYER_PROOF),
            builder -> builder.persistent(MarketPlayerProofCodec.CODEC).copyOnDeath());
    private KingdomMarketTransferService() {}
    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server) -> reconcilePlayer(handler.player));
    }
    public static MarketTradeCoordinator.Result execute(ServerPlayer actor,KingdomMarketLedger.Quote quote,MarketInventoryPlan plan) {
        if(actor != null && de.quest.shrine.VillageIntroRequestService.blocksActions(actor.level(),actor.getUUID())) return MarketTradeCoordinator.Result.HELD;
        if(actor==null || quote==null || !actor.level().getServer().isSameThread() || !actor.getUUID().equals(quote.actor()) || !actor.isAlive() || actor.isSpectator()
                || actor.isRemoved()) return MarketTradeCoordinator.Result.REJECTED;
        var world=actor.level(); var state=LivingKingdomsState.get(world.getServer()); var ledger=state.markets();
        if(ledger.receipt(quote.id()).isPresent()) return finish(actor,quote.id(),coordinator(actor,state).reconcile(quote.id()));
        if(quote.side()==KingdomMarketLedger.Side.SELL && (!KingdomMarketSourceService.ready(world.getServer())
                || !ledger.sourceAllows(quote))) return MarketTradeCoordinator.Result.REJECTED;
        var visit=ledger.visitById(quote.visit()); var person=state.people().person(quote.trader());
        if(visit.isEmpty() || !visit.get().open() || person.isEmpty() || person.get().body()==null
                || !(world.getEntity(person.get().body()) instanceof KingdomNpcEntity npc) || !npc.isAlive() || actor.distanceToSqr(npc)>64
                || !world.hasChunkAt(npc.blockPosition()) || KingdomNpcService.available(world,npc).filter(p -> p.id().equals(quote.trader())).isEmpty()) return MarketTradeCoordinator.Result.REJECTED;
        var anchor=state.anchors().official(visit.get().market(),BuildingScanner.Role.MARKET);
        if(!KingdomNpcService.tradeAllowed(world,actor,quote.trader(),visit.get().market())) return MarketTradeCoordinator.Result.REJECTED;
        if(anchor.isEmpty() || !state.anchors().mayTrade(anchor.get().id(),world.getServer().overworld().getGameTime())
                || state.anchors().report(anchor.get().id()).filter(report -> report.walkable().contains(npc.blockPosition())).isEmpty()) return MarketTradeCoordinator.Result.REJECTED;
        if(quote.side()==KingdomMarketLedger.Side.SELL && !KingdomMarketDemandService.observe(actor,person.get().village())) return MarketTradeCoordinator.Result.REJECTED;
        return finish(actor,quote.id(),coordinator(actor,state).execute(quote,plan,world.getServer().overworld().getGameTime()));
    }
    public static void reconcilePlayer(ServerPlayer actor) {
        if(actor==null || actor.isRemoved() || !actor.level().getServer().isSameThread()) return;
        if(actor.connection != null && !actor.connection.isAcceptingMessages()
                || de.quest.shrine.VillageIntroRequestService.blocksActions(actor.level(),actor.getUUID())) return;
        var state=LivingKingdomsState.get(actor.level().getServer()); var coordinator=coordinator(actor,state);
        var pending=state.markets().pending(actor.getUUID());
        for(var receipt:pending) finish(actor,receipt.id(),coordinator.reconcile(receipt.id()));
        // Also recover a saved terminal ack whose shared receipt was already committed before logout.
        QuestState.get(actor.level().getServer()).getPlayerData(actor.getUUID()).marketWallet().marker()
                .filter(m -> pending.stream().noneMatch(r -> r.id().equals(m.receipt())))
                .ifPresent(m -> finish(actor,m.receipt(),coordinator.reconcile(m.receipt())));
    }
    private static MarketTradeCoordinator.Result finish(ServerPlayer actor,java.util.UUID receipt,MarketTradeCoordinator.Result result) {
        if(result==MarketTradeCoordinator.Result.COMMITTED) KingdomStoryService.marketCommitted(actor.level(),actor,receipt);
        return result;
    }
    private static MarketTradeCoordinator coordinator(ServerPlayer actor,LivingKingdomsState state) {
        var quests=QuestState.get(actor.level().getServer()); var target=(AttachmentTarget)actor;
        var playerProof=new MarketTradeCoordinator.PlayerProof() {
            public Tag get() { var proof=target.getAttached(PROOF); return proof==null?null:proof.copy(); }
            public void set(CompoundTag next) { target.setAttached(PROOF,next.copy()); }
            public MarketWalletJournal originalWallet(java.util.UUID owner) {
                var original=quests.getPlayersView().get(owner); return original==null?null:original.marketWallet();
            }
        };
        return new MarketTradeCoordinator(state.markets(),actor.getUUID(),quests.getPlayerData(actor.getUUID()),actor.getInventory(),playerProof,
                actor.registryAccess(),() -> NativeMarketSaveBarrier.persist(actor,state,quests),after -> CurrencyService.applyMarketReceipt(actor.level(),actor.getUUID(),after),
                quote -> { if(!KingdomStoryService.prepareMarketTrade(actor.level(),actor,quote)) throw new IllegalStateException("Unconfirmed story context"); });
    }
}
