package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.data.PlayerQuestData;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.*;

final class KingdomStoryGuidanceTest {
    private final UUID town=UUID.randomUUID(), owner=UUID.randomUUID();
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private static String key(Component text) { return ((TranslatableContents)text.getContents()).getKey(); }

    @Test void unboundAndMissingTargetsExplainRecoveryWithoutCreatingVillageOrProgress() {
        var data=new PlayerQuestData(); var state=new LivingKingdomsState();
        assertTrue(key(KingdomStoryGuidance.next(data,state,0)).endsWith(".target"));
        KingdomStoryProgress.bind(data,town,false);
        assertTrue(key(KingdomStoryGuidance.next(data,state,0)).endsWith(".recover_target"));
        assertEquals(0,data.getStoryChapterProgress(KingdomStoryProgress.ARC));
    }
    @Test void constructionHintsFollowEarnedTargetEvidenceAndFinishAtTheRealClaim() {
        var data=new PlayerQuestData(); var state=new LivingKingdomsState();
        state.registerCustom(town,owner,VillageKey.overworld(0,0),new BlockPos(0,64,0),"Home").orElseThrow();
        KingdomStoryProgress.bind(data,town,false);
        assertTrue(key(KingdomStoryGuidance.next(data,state,0)).endsWith(".building"));
        int x=0;
        for(var role:List.of(BuildingScanner.Role.COUNCIL,BuildingScanner.Role.INN,BuildingScanner.Role.MARKET,BuildingScanner.Role.STOREHOUSE)) {
            var pos=new BlockPos(x,64,0); x+=4;
            var report=new BuildingScanner.Report(role,BuildingScanner.Status.VALID,Map.of(),Map.of(),Set.of());
            assertEquals(KingdomAnchorRegistry.Result.APPLIED,state.anchors().bind(owner,town,0,UUID.randomUUID(),-1,pos,
                    Direction.NORTH,BuildingScanner.Range.BASIC,report,0,0));
        }
        assertTrue(key(KingdomStoryGuidance.next(data,state,0)).endsWith(".claim"));
        assertTrue(key(KingdomStoryGuidance.next(data,state,1)).endsWith(".place"));
        state=LivingKingdomsState.fromNbt(LivingKingdomsState.toNbt(state));
        assertTrue(key(KingdomStoryGuidance.next(data,state,1)).endsWith(".place"),"Reload must not erase earned buildings");
    }
    @Test void staffTradeDeliveryAndRecognitionHintsAdvanceOnlyOnConfirmedTargetDeeds() {
        var data=new PlayerQuestData(); var state=new LivingKingdomsState();
        state.registerCustom(town,owner,VillageKey.overworld(0,0),new BlockPos(0,64,0),"Home").orElseThrow();
        KingdomStoryProgress.bind(data,town,false);
        for(var pair:List.of(Map.entry(2,KingdomStoryProgress.Deed.MEET_LEADER),Map.entry(2,KingdomStoryProgress.Deed.MEET_INNKEEPER),
                Map.entry(2,KingdomStoryProgress.Deed.MEET_STOREKEEPER),Map.entry(3,KingdomStoryProgress.Deed.MARKET_TRADE),
                Map.entry(3,KingdomStoryProgress.Deed.STORE_DELIVERY),Map.entry(4,KingdomStoryProgress.Deed.FIRST_PROJECT))) {
            var before=LivingKingdomsState.toNbt(state).copy();
            assertTrue(key(KingdomStoryGuidance.next(data,state,pair.getKey())).endsWith("."+pair.getValue().name().toLowerCase(Locale.ROOT)));
            assertEquals(before,LivingKingdomsState.toNbt(state));
            assertFalse(KingdomStoryProgress.record(data,UUID.randomUUID(),pair.getValue(),"wrong",true));
            assertTrue(KingdomStoryProgress.record(data,town,pair.getValue(),pair.getValue().name(),true));
        }
        for(int chapter:new int[]{2,3,4}) assertTrue(key(KingdomStoryGuidance.next(data,state,chapter)).endsWith(".claim"));
        assertFalse(data.hasMilestoneFlag(KingdomStoryProgress.CHECK_EARNED));
    }
}
