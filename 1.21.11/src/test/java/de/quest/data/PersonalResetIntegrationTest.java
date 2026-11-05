package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Real native player/quest journal bridge with controlled independent document interruption. No game runs. */
final class PersonalResetIntegrationTest {
    @BeforeAll static void bootstrap(){PersonalResetItemsTest.boot();}
    static final class Fixture {
        QuestState state=QuestState.fromNbt(new CompoundTag());
        final PersonalResetItemsTest.Fixture storage;
        final RegistryAccess.Frozen registries=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        final UUID actor,other=UUID.randomUUID();Tag proof;
        int saves,failAt,detaches;boolean detached;
        Fixture() throws Exception {
            storage=new PersonalResetItemsTest.Fixture();actor=storage.player.getUUID();when(storage.player.registryAccess()).thenReturn(registries);
            var data=state.getPlayerData(actor);data.setCurrencyBalance(80);de.quest.quest.daily.FirstDailyChoiceService.migratePre24Progress(data);
            state.getPlayerData(other).setCurrencyBalance(99);
            storage.inventory[0]=new ItemStack(Items.DIAMOND,12);
            storage.ender[0]=new ItemStack(Items.PAPER);net.minecraft.world.item.component.CustomData.update(
                    net.minecraft.core.component.DataComponents.CUSTOM_DATA,storage.ender[0],t->t.putString("vq_archive_id","ledger"));
        }
        PersonalResetTransaction next(){return PersonalResetTransaction.prepare(UUID.randomUUID(),actor,state,PersonalResetItems.plan(storage.player),registries).orElseThrow();}
        PersonalResetJournalPort port(PersonalResetTransaction tx){return new PersonalResetJournalPort(state,storage.player,
            new PersonalResetJournalPort.PlayerProof(){public Tag get(){return proof==null?null:proof.copy();}public void set(CompoundTag tag){proof=tag.copy();}},
            new PersonalResetJournalPort.Shared(){public boolean ready(){return true;}public boolean detach(){detaches++;detached=true;return true;}public boolean detached(){return detached;}},
            ()->++saves!=failAt,tx);}
        void reload(){state=QuestState.fromNbt(QuestState.toNbt(state));proof=proof.copy();}
    }
    @Test void nativeJournalCommitsAndAcknowledgesThenSupportsAnotherGenerationAfterReload() throws Exception {
        var f=new Fixture();
        for(int generation=2;generation<=3;generation++) {
            var tx=f.next();var port=f.port(tx);
            assertEquals(PersonalResetCoordinator.Result.COMMITTED,new PersonalResetCoordinator(port).execute(tx.receipt()));
            assertEquals(generation,f.state.getPlayerData(f.actor).progressGeneration());assertEquals(0,f.state.getPlayerData(f.actor).getCurrencyBalance());
            assertEquals(99,f.state.getPlayerData(f.other).getCurrencyBalance());assertEquals(12,f.storage.inventory[0].getCount());assertTrue(f.storage.ender[0].isEmpty());
            assertFalse(port.canBeginNext());assertTrue(port.acknowledge());assertTrue(port.canBeginNext());
            assertTrue(f.state.confirmPersonalResetTerminal(f.actor,(CompoundTag)f.state.personalResetTransaction(f.actor)));
            f.reload();assertFalse(f.state.personalResetUnlocked(f.actor));
        }
    }
    @Test void eachNativeCheckpointFailureRecoversWithoutReplacingForeignProgressOrPayingAgain() throws Exception {
        for(int failure=1;failure<=5;failure++) {
            var f=new Fixture();var tx=f.next();f.failAt=failure;
            assertEquals(PersonalResetCoordinator.Result.HELD,new PersonalResetCoordinator(f.port(tx)).execute(tx.receipt()));
            f.reload();f.failAt=0;var result=new PersonalResetCoordinator(f.port(null)).recover();
            assertTrue(result==PersonalResetCoordinator.Result.CANCELLED || result==PersonalResetCoordinator.Result.COMMITTED);
            var image=QuestState.toNbt(f.state);var count=f.detaches;
            assertEquals(result,new PersonalResetCoordinator(f.port(null)).recover());assertEquals(image,QuestState.toNbt(f.state));assertEquals(count,f.detaches);
            assertEquals(99,f.state.getPlayerData(f.other).getCurrencyBalance());assertEquals(12,f.storage.inventory[0].getCount());
        }
    }
    @Test void savedPersonalDocumentWithOldNativePlayerFinishesOriginalItemsExactlyOnce() throws Exception {
        var f=new Fixture();var tx=f.next();var port=f.port(tx);port.prepare(tx.receipt());
        var personal=tx.withStage(PersonalResetCoordinator.Stage.PERSONAL).toNbt();
        assertTrue(f.state.commitPersonalReset(f.actor,tx.before(),tx.after(),tx.toNbt(),personal));f.reload();
        assertEquals(PersonalResetCoordinator.Result.COMMITTED,new PersonalResetCoordinator(f.port(null)).recover());
        assertTrue(f.storage.ender[0].isEmpty());assertEquals(2,f.state.getPlayerData(f.actor).progressGeneration());
    }
    @Test void sharedStageWithAnOlderGuildDocumentRetriesOwnerDetach() throws Exception {
        var f=new Fixture();var tx=f.next();var port=f.port(tx);port.prepare(tx.receipt());assertTrue(port.applyInventory());
        port.playerStage(PersonalResetCoordinator.Stage.AFTER);assertTrue(port.applyPersonal());port.questStage(PersonalResetCoordinator.Stage.SHARED);f.reload();
        assertEquals(PersonalResetCoordinator.Result.COMMITTED,new PersonalResetCoordinator(f.port(null)).recover());assertEquals(1,f.detaches);
    }
    @Test void unknownOrForeignProofRefusesWithoutAnyMutation() throws Exception {
        var f=new Fixture();var tx=f.next();f.port(tx).prepare(tx.receipt());
        var raw=(CompoundTag)f.proof;raw.putString("actor",f.other.toString());var before=QuestState.toNbt(f.state);
        assertEquals(PersonalResetCoordinator.Result.HELD,new PersonalResetCoordinator(f.port(null)).recover());
        assertEquals(before,QuestState.toNbt(f.state));assertEquals(0,f.detaches);assertFalse(f.storage.ender[0].isEmpty());
    }
}
