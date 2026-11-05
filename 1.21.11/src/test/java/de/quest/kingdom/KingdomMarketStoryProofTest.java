package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.data.QuestState;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

final class KingdomMarketStoryProofTest {
    @Test void projectTradeNeedsCommittedNativeAcknowledgementButDoesNotNeedTheStoryTarget() {
        var quests=fixture();var ledger=org.mockito.Mockito.mock(KingdomMarketLedger.class);
        var actual=org.mockito.Mockito.mock(KingdomMarketLedger.ReceiptView.class);
        var quote=org.mockito.Mockito.mock(KingdomMarketLedger.Quote.class);
        var visit=org.mockito.Mockito.mock(KingdomMarketLedger.VisitView.class);var visitId=UUID.randomUUID();
        org.mockito.Mockito.when(ledger.receipt(RECEIPT)).thenReturn(java.util.Optional.of(actual));
        org.mockito.Mockito.when(actual.phase()).thenReturn(KingdomMarketLedger.Phase.COMMITTED);
        org.mockito.Mockito.when(actual.quote()).thenReturn(quote);org.mockito.Mockito.when(quote.actor()).thenReturn(ACTOR);
        org.mockito.Mockito.when(quote.visit()).thenReturn(visitId);org.mockito.Mockito.when(ledger.visitById(visitId)).thenReturn(java.util.Optional.of(visit));
        org.mockito.Mockito.when(visit.market()).thenReturn(OTHER);
        assertTrue(KingdomMarketStoryProof.completeNative(quests,ledger,ACTOR,RECEIPT).isEmpty());
        assertFalse(KingdomStoryProgress.hasProject(quests.getPlayerData(ACTOR),OTHER,KingdomStoryProgress.Deed.MARKET_TRADE));
        org.mockito.Mockito.when(ledger.nativeAcknowledged(RECEIPT)).thenReturn(true);
        assertEquals(Set.of(ACTOR),KingdomMarketStoryProof.completeNative(quests,ledger,ACTOR,RECEIPT));
        assertTrue(KingdomStoryProgress.hasProject(quests.getPlayerData(ACTOR),OTHER,KingdomStoryProgress.Deed.MARKET_TRADE));
        assertFalse(KingdomStoryProgress.has(quests.getPlayerData(ACTOR),KingdomStoryProgress.Deed.MARKET_TRADE));
        assertTrue(KingdomMarketStoryProof.completeNative(quests,ledger,ACTOR,RECEIPT).isEmpty(),"Replay must not generate a new event");
        assertTrue(KingdomMarketStoryProof.completeNative(quests,ledger,MEMBER,RECEIPT).isEmpty());
        assertFalse(KingdomStoryProgress.hasProject(quests.getPlayerData(MEMBER),OTHER,KingdomStoryProgress.Deed.MARKET_TRADE));
    }
    private static final UUID ACTOR=new UUID(1,1),MEMBER=new UUID(2,2),LATE=new UUID(3,3),TOWN=new UUID(4,4),OTHER=new UUID(5,5),RECEIPT=new UUID(6,6);
    private static QuestState fixture() {
        var quests=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        for(var id:List.of(ACTOR,MEMBER,LATE)) KingdomStoryProgress.bind(quests.getPlayerData(id),TOWN,false);
        return quests;
    }
    @Test void preparedRecipientsSurviveSerializationButAttemptsAndLateMembersCannotCount() {
        var quests=fixture();
        assertTrue(KingdomMarketStoryProof.prepare(quests,ACTOR,RECEIPT,TOWN,List.of(ACTOR,MEMBER)));
        quests=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,QuestState.TYPE.codec().encodeStart(NbtOps.INSTANCE,quests).getOrThrow()).getOrThrow();
        assertTrue(KingdomMarketStoryProof.complete(quests,ACTOR,RECEIPT,TOWN,false).isEmpty());
        assertFalse(KingdomStoryProgress.has(quests.getPlayerData(ACTOR),KingdomStoryProgress.Deed.MARKET_TRADE));
        assertEquals(Set.of(ACTOR,MEMBER),KingdomMarketStoryProof.complete(quests,ACTOR,RECEIPT,TOWN,true));
        assertFalse(KingdomStoryProgress.has(quests.getPlayerData(LATE),KingdomStoryProgress.Deed.MARKET_TRADE));
        assertTrue(KingdomMarketStoryProof.complete(quests,ACTOR,RECEIPT,TOWN,true).isEmpty());
    }
    @Test void changingAwayAndBackToTheSameVillageCannotReuseAnOldTrade() {
        var quests=fixture(); var data=quests.getPlayerData(ACTOR);
        assertTrue(KingdomMarketStoryProof.prepare(quests,ACTOR,RECEIPT,TOWN,List.of(ACTOR)));
        assertTrue(KingdomStoryProgress.bind(data,OTHER,true)); assertTrue(KingdomStoryProgress.bind(data,TOWN,true));
        assertTrue(KingdomMarketStoryProof.complete(quests,ACTOR,RECEIPT,TOWN,true).isEmpty());
        assertFalse(KingdomStoryProgress.has(data,KingdomStoryProgress.Deed.MARKET_TRADE));
        var fresh=new UUID(7,7); assertTrue(KingdomMarketStoryProof.prepare(quests,ACTOR,fresh,TOWN,List.of(ACTOR)));
        assertEquals(Set.of(ACTOR),KingdomMarketStoryProof.complete(quests,ACTOR,fresh,TOWN,true));
    }
    @Test void aTradeMadeBeforeChoosingItsVillageCannotBecomeAStoryReceiptLater() {
        var quests=fixture(); KingdomStoryProgress.bind(quests.getPlayerData(ACTOR),OTHER,true);
        assertTrue(KingdomMarketStoryProof.prepare(quests,ACTOR,RECEIPT,TOWN,List.of(ACTOR)));
        KingdomStoryProgress.bind(quests.getPlayerData(ACTOR),TOWN,true);
        assertTrue(KingdomMarketStoryProof.complete(quests,ACTOR,RECEIPT,TOWN,true).isEmpty());
    }
    @Test void legacyReceiptsWrongOwnerWrongHostAndMalformedFutureProofCannotCreateProgress() {
        var quests=fixture();
        assertTrue(KingdomMarketStoryProof.complete(quests,ACTOR,RECEIPT,TOWN,true).isEmpty());
        KingdomMarketStoryProof.prepare(quests,ACTOR,RECEIPT,TOWN,List.of(ACTOR));
        assertTrue(KingdomMarketStoryProof.complete(quests,MEMBER,RECEIPT,TOWN,true).isEmpty());
        assertTrue(KingdomMarketStoryProof.complete(quests,ACTOR,new UUID(7,7),TOWN,true).isEmpty());
        assertTrue(KingdomMarketStoryProof.complete(quests,ACTOR,RECEIPT,OTHER,true).isEmpty());
        var data=quests.getPlayerData(ACTOR); data.setTradeRouteString(KingdomMarketStoryProof.KEY,"future proof");
        assertTrue(KingdomMarketStoryProof.complete(quests,ACTOR,RECEIPT,TOWN,true).isEmpty());
        assertFalse(KingdomMarketStoryProof.prepare(quests,ACTOR,RECEIPT,TOWN,List.of(ACTOR)));
        assertEquals("future proof",data.getTradeRouteString(KingdomMarketStoryProof.KEY));
    }
    @Test void duplicateOrOversizedRecipientsAreRejectedBeforeChangingSavedContext() {
        var quests=fixture(); var original=QuestState.TYPE.codec().encodeStart(NbtOps.INSTANCE,quests).getOrThrow();
        assertFalse(KingdomMarketStoryProof.prepare(quests,ACTOR,RECEIPT,TOWN,List.of(ACTOR,ACTOR)));
        assertFalse(KingdomMarketStoryProof.prepare(quests,ACTOR,RECEIPT,TOWN,List.of(ACTOR,MEMBER,LATE,TOWN,OTHER)));
        assertEquals(original,QuestState.TYPE.codec().encodeStart(NbtOps.INSTANCE,quests).getOrThrow());
    }
}
