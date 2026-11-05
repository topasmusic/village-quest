package de.quest.kingdom;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
final class FoundingPreviewTest {
    private static NativeFoundingObserver.Evidence proof(int adults,int beds,boolean job,boolean loaded) {
        var residents=new ArrayList<VillageFoundingRegistry.Resident>();
        for(int i=0;i<beds;i++) residents.add(new VillageFoundingRegistry.Resident(new UUID(0,i),new BlockPos(i,64,0),job&&i==0?new BlockPos(2,64,2):null));
        return new NativeFoundingObserver.Evidence(adults,residents,loaded,4);
    }
    @Test void missingPopulationBedsJobAndUnknownEvidenceNeverPermitFounding() {
        assertFalse(KingdomBuildingService.foundingEvidenceReady(null,-1,100));
        assertFalse(KingdomBuildingService.foundingEvidenceReady(proof(0,0,false,true),100,100));
        assertFalse(KingdomBuildingService.foundingEvidenceReady(proof(2,3,true,true),100,100));
        assertFalse(KingdomBuildingService.foundingEvidenceReady(proof(3,2,true,true),100,100));
        assertFalse(KingdomBuildingService.foundingEvidenceReady(proof(3,3,false,true),100,100));
        assertFalse(KingdomBuildingService.foundingEvidenceReady(proof(3,3,true,false),100,100));
    }
    @Test void onlyFreshCompleteEvidenceEnablesTheReadOnlyPreview() {
        var complete=proof(3,3,true,true);
        assertTrue(KingdomBuildingService.foundingEvidenceReady(complete,100,100));
        assertTrue(KingdomBuildingService.foundingEvidenceReady(complete,100,140));
        assertFalse(KingdomBuildingService.foundingEvidenceReady(complete,100,141));
        assertFalse(KingdomBuildingService.foundingEvidenceReady(complete,100,99));
        assertFalse(KingdomBuildingService.foundingEvidenceReady(complete,-1,0));
    }
}
