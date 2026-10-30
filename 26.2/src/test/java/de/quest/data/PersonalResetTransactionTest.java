package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class PersonalResetTransactionTest {
    @BeforeAll static void bootstrap(){PersonalResetItemsTest.boot();}
    private static final class Fixture {
        final PersonalResetItemsTest.Fixture inventory;
        final UUID actor,receipt=UUID.randomUUID();final QuestState state=QuestState.fromNbt(new CompoundTag());
        final RegistryAccess.Frozen registries=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        final PersonalResetTransaction tx;
        Fixture() throws Exception {
            inventory=new PersonalResetItemsTest.Fixture();actor=inventory.player.getUUID();
            var data=state.getPlayerData(actor);data.setCurrencyBalance(80);
            de.quest.quest.daily.FirstDailyChoiceService.migratePre24Progress(data);
            tx=PersonalResetTransaction.prepare(receipt,actor,state,PersonalResetItems.plan(inventory.player),registries).orElseThrow();
        }
    }
    @Test void frozenTransactionRoundTripsAndChangingStageDoesNotChangeItsIdentity() throws Exception {
        var f=new Fixture();var loaded=PersonalResetTransaction.fromNbt(f.tx.toNbt(),f.registries).orElseThrow();
        assertEquals(f.actor,loaded.actor());assertEquals(f.receipt,loaded.receipt());assertEquals(1,loaded.generation());
        assertTrue(loaded.sameTransaction(f.tx.withStage(PersonalResetCoordinator.Stage.PERSONAL)));
        assertEquals(f.tx.toNbt(),loaded.toNbt());assertTrue(loaded.items(f.registries).matchesBefore(f.inventory.player));
        assertThrows(IllegalStateException.class,loaded::acknowledgedImage);
    }
    @Test void foreignInventoryForgedGenerationOrRewardUnknownSchemaAndEarlyAckRefuse() throws Exception {
        var f=new Fixture();
        for(int variant=0;variant<5;variant++) {
            var raw=f.tx.toNbt();
            if(variant==0)raw.getCompoundOrEmpty("items").putString("owner",UUID.randomUUID().toString());
            if(variant==1)raw.putInt("generation",9);
            if(variant==2)raw.getCompoundOrEmpty("after").getCompoundOrEmpty("questManager").getListOrEmpty("currencyBalance").add(new CompoundTag());
            if(variant==3)raw.putInt("schema",999);
            if(variant==4)raw.putBoolean("acknowledged",true);
            assertTrue(PersonalResetTransaction.fromNbt(raw,f.registries).isEmpty(),"variant="+variant);
        }
    }
    @Test void journalCompareAndSetAndPersonalPublicationSurviveReloadAsOneQuestDocument() throws Exception {
        var f=new Fixture();var other=UUID.randomUUID();f.state.getPlayerData(other).setCurrencyBalance(25);
        assertTrue(f.state.comparePersonalResetTransaction(f.actor,null,f.tx.toNbt()));
        assertFalse(f.state.comparePersonalResetTransaction(f.actor,null,f.tx.toNbt()));
        var personal=f.tx.withStage(PersonalResetCoordinator.Stage.PERSONAL).toNbt();
        assertTrue(f.state.commitPersonalReset(f.actor,f.tx.before(),f.tx.after(),f.tx.toNbt(),personal));
        var loaded=QuestState.fromNbt(QuestState.toNbt(f.state));
        assertEquals(2,loaded.getPlayerData(f.actor).progressGeneration());assertEquals(0,loaded.getPlayerData(f.actor).getCurrencyBalance());
        assertEquals(personal,loaded.personalResetTransaction(f.actor));assertEquals(25,loaded.getPlayerData(other).getCurrencyBalance());
        assertFalse(loaded.commitPersonalReset(f.actor,f.tx.before(),f.tx.after(),f.tx.toNbt(),personal));
    }
    @Test void unknownOwnerRecordAndForeignOuterJournalAreRetainedAndNeverOverwritten() throws Exception {
        var f=new Fixture();
        for(Tag unknown:new Tag[]{StringTag.valueOf("future"),new IntArrayTag(new int[]{3,8})}) {
            var root=QuestState.toNbt(f.state);root.put("personalResetTransactions",unknown);
            var loaded=QuestState.fromNbt(root);
            assertEquals(unknown,loaded.personalResetTransaction(f.actor));
            assertFalse(loaded.comparePersonalResetTransaction(f.actor,unknown,f.tx.toNbt()));
            assertEquals(unknown,QuestState.toNbt(loaded).get("personalResetTransactions"));
        }
        var root=QuestState.toNbt(f.state);var owners=new CompoundTag();var unknown=StringTag.valueOf("future-owner");
        owners.put(f.actor.toString(),unknown);root.put("personalResetTransactions",owners);
        var loaded=QuestState.fromNbt(root);assertEquals(unknown,loaded.personalResetTransaction(f.actor));
        assertFalse(loaded.comparePersonalResetTransaction(f.actor,null,f.tx.toNbt()));
        assertEquals(root,QuestState.toNbt(loaded));
    }
    @Test void aStaleJournalCannotPublishThePersonalResetOrReplaceItsEvidence() throws Exception {
        var f=new Fixture();assertTrue(f.state.comparePersonalResetTransaction(f.actor,null,f.tx.toNbt()));
        var other=f.tx.withStage(PersonalResetCoordinator.Stage.PERSONAL).toNbt();
        assertFalse(f.state.commitPersonalReset(f.actor,f.tx.before(),f.tx.after(),other,other));
        assertEquals(80,f.state.getPlayerData(f.actor).getCurrencyBalance());assertEquals(f.tx.toNbt(),f.state.personalResetTransaction(f.actor));
    }
}
