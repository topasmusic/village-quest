package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Protocol failure injection, not a server, world or ingame simulation. Native ports need separate tests. */
final class PersonalResetRecoveryTest {
    @Test void everyCheckpointFailurePreservesTheClaimAndRetryNeverRepeatsPersonalReplacement() {
        for(int failure=1;failure<=5;failure++) {
            var port=new Memory();port.failAt=failure;var receipt=UUID.randomUUID();
            assertEquals(PersonalResetCoordinator.Result.HELD,new PersonalResetCoordinator(port).execute(receipt));
            assertEquals(receipt,port.q.receipt());port.failAt=0;
            var outcome=new PersonalResetCoordinator(port).recover();
            assertTrue(outcome==PersonalResetCoordinator.Result.COMMITTED || outcome==PersonalResetCoordinator.Result.CANCELLED);
            int replacements=port.replacements,detaches=port.detaches,items=port.items;
            assertEquals(outcome,new PersonalResetCoordinator(port).recover());
            assertEquals(replacements,port.replacements);assertEquals(detaches,port.detaches);assertEquals(items,port.items);
            if(outcome==PersonalResetCoordinator.Result.COMMITTED) {
                assertEquals(1,replacements);assertEquals(1,items);assertTrue(port.detached);
            } else { assertEquals(0,replacements);assertEquals(0,detaches);assertEquals(0,items); }
        }
    }
    @Test void savedPersonalImageWithOldPlayerInventoryFinishesOnlyTheInventoryAndDetach() {
        var port=new Memory();port.seed(PersonalResetCoordinator.Stage.PERSONAL,PersonalResetCoordinator.Stage.BEFORE);
        port.personalAfter=true;
        assertEquals(PersonalResetCoordinator.Result.COMMITTED,new PersonalResetCoordinator(port).recover());
        assertEquals(0,port.replacements);assertEquals(1,port.items);assertTrue(port.detached);
    }
    @Test void partiallySavedSharedDetachRetriesIdempotentlyWithoutResettingAnyoneElse() {
        var port=new Memory();port.partialDetach=true;
        assertEquals(PersonalResetCoordinator.Result.HELD,new PersonalResetCoordinator(port).execute(UUID.randomUUID()));
        assertTrue(port.inventoryAfter);assertTrue(port.personalAfter);assertTrue(port.guildDetached);assertFalse(port.detached);
        port.partialDetach=false;
        assertEquals(PersonalResetCoordinator.Result.COMMITTED,new PersonalResetCoordinator(port).recover());
        assertEquals(1,port.replacements);assertEquals(1,port.items);assertEquals(1,port.guildDetaches);
    }
    @Test void unknownForeignMissingOrConflictingImagesHoldBeforeAnyMutation() {
        for(int scenario=0;scenario<5;scenario++) {
            var port=new Memory();port.seed(PersonalResetCoordinator.Stage.BEFORE,PersonalResetCoordinator.Stage.BEFORE);
            if(scenario==0)port.valid=false;
            if(scenario==1)port.p=null;
            if(scenario==2)port.p=new PersonalResetCoordinator.Proof(UUID.randomUUID(),PersonalResetCoordinator.Stage.BEFORE);
            if(scenario==3)port.inventoryConflict=true;
            if(scenario==4)port.personalConflict=true;
            var before=port.q;
            assertEquals(PersonalResetCoordinator.Result.HELD,new PersonalResetCoordinator(port).recover());
            assertEquals(before,port.q);assertEquals(0,port.items);assertEquals(0,port.replacements);assertEquals(0,port.detaches);
        }
    }
    @Test void noMutationFollowsAnUnconfirmedBeforeCheckpoint() {
        var port=new Memory();port.failAt=1;
        assertEquals(PersonalResetCoordinator.Result.HELD,new PersonalResetCoordinator(port).execute(UUID.randomUUID()));
        assertEquals(0,port.items);assertEquals(0,port.replacements);assertEquals(0,port.detaches);
    }
    @Test void failedPersonalPublicationRetainsTheAfterInventoryAndRetriesTheSameFrozenImage() {
        var port=new Memory();port.failPersonal=true;var receipt=UUID.randomUUID();
        assertEquals(PersonalResetCoordinator.Result.HELD,new PersonalResetCoordinator(port).execute(receipt));
        assertTrue(port.inventoryAfter);assertFalse(port.personalAfter);assertFalse(port.detached);
        port.failPersonal=false;
        assertEquals(PersonalResetCoordinator.Result.COMMITTED,new PersonalResetCoordinator(port).recover());
        assertEquals(receipt,port.q.receipt());assertEquals(1,port.items);assertEquals(1,port.replacements);
    }
    @Test void terminalReceiptsDoNotRestoreOldItemsAfterLegitimateLaterChanges() {
        var port=new Memory();port.seed(PersonalResetCoordinator.Stage.COMMITTED,PersonalResetCoordinator.Stage.COMMITTED);
        port.inventoryConflict=true;port.personalConflict=true;
        assertEquals(PersonalResetCoordinator.Result.COMMITTED,new PersonalResetCoordinator(port).recover());
        assertEquals(0,port.items);assertEquals(0,port.replacements);assertEquals(0,port.detaches);
    }
    private static final class Memory implements PersonalResetCoordinator.Port {
        PersonalResetCoordinator.Proof q,p;
        boolean valid=true,inventoryAfter,personalAfter,detached,guildDetached,partialDetach,inventoryConflict,personalConflict,failPersonal;
        int checkpoints,failAt,items,replacements,detaches,guildDetaches;
        void seed(PersonalResetCoordinator.Stage a,PersonalResetCoordinator.Stage b) {
            var id=UUID.randomUUID();q=new PersonalResetCoordinator.Proof(id,a);p=new PersonalResetCoordinator.Proof(id,b);
        }
        public boolean valid(){return valid;}
        public boolean validateNew(){return q==null && p==null;}
        public PersonalResetCoordinator.Proof questProof(){return q;}
        public PersonalResetCoordinator.Proof playerProof(){return p;}
        public void prepare(UUID id){q=new PersonalResetCoordinator.Proof(id,PersonalResetCoordinator.Stage.BEFORE);p=q;}
        public boolean inventoryBefore(){return !inventoryConflict&&!inventoryAfter;}
        public boolean inventoryAfter(){return !inventoryConflict&&inventoryAfter;}
        public boolean personalBefore(){return !personalConflict&&!personalAfter;}
        public boolean personalAfter(){return !personalConflict&&personalAfter;}
        public boolean applyInventory(){if(!inventoryBefore())return false;items++;inventoryAfter=true;return true;}
        public boolean applyPersonal(){if(failPersonal || !personalBefore())return false;replacements++;personalAfter=true;return true;}
        public boolean detachShared(){
            detaches++;
            if(!guildDetached){guildDetached=true;guildDetaches++;}
            if(partialDetach)throw new IllegalStateException("Interrupted after guild document");
            detached=true;return true;
        }
        public boolean sharedDetached(){return detached;}
        public void questStage(PersonalResetCoordinator.Stage s){q=new PersonalResetCoordinator.Proof(q.receipt(),s);}
        public void playerStage(PersonalResetCoordinator.Stage s){p=new PersonalResetCoordinator.Proof(p.receipt(),s);}
        public boolean checkpoint(){return ++checkpoints!=failAt;}
    }
}
