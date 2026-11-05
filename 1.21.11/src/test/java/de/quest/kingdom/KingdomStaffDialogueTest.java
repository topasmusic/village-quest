package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import de.quest.data.QuestState;
import de.quest.village.VillageLifeState.Status;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

final class KingdomStaffDialogueTest {
    private static final UUID PERSON=UUID.randomUUID(),TOWN=UUID.randomUUID();
    private static KingdomPersonRegistry.View person(KingdomPersonRegistry.Role role,int personality,UUID visit) {
        return new KingdomPersonRegistry.View(PERSON,TOWN,role,0,0,1,UUID.randomUUID(),BlockPos.ZERO,
                personality*8L,"Mara",false,visit==null?KingdomPersonRegistry.Presence.PRESENT:KingdomPersonRegistry.Presence.VISITING,0,visit);
    }
    @Test void confirmedRecoveryAndAbsenceOverrideFamiliarWelcomeAndVisitingNeverClaimsToBeAtHome() {
        var leader=person(KingdomPersonRegistry.Role.LEADER,2,null);
        assertNotEquals(KingdomStaffDialogue.key(leader,Status.ACTIVE,false),KingdomStaffDialogue.key(leader,Status.ACTIVE,true));
        for(var status:List.of(Status.ABANDONED,Status.RECOVERING)) {
            assertEquals(KingdomStaffDialogue.key(leader,status,false),KingdomStaffDialogue.key(leader,status,true));
            assertTrue(KingdomStaffDialogue.key(leader,status,true).endsWith(status.name().toLowerCase(Locale.ROOT)));
        }
        var guest=person(KingdomPersonRegistry.Role.LEADER,2,UUID.randomUUID());
        assertTrue(KingdomStaffDialogue.key(guest,Status.ABANDONED,true).endsWith(".visiting"));
        assertThrows(IllegalArgumentException.class,() -> KingdomStaffDialogue.key(person(KingdomPersonRegistry.Role.MARKET_TRADER,0,null),Status.ACTIVE,false));
    }
    @Test void personalBoundedMemorySurvivesTheRealDocumentWithoutRetargetingOrClaimingAChapter() {
        var owner=UUID.randomUUID(); var other=UUID.randomUUID(); var quests=QuestState.TYPE.constructor().get();
        var data=quests.getPlayerData(owner); KingdomStoryProgress.bind(data,TOWN,false); data.setStoryChapterProgress(KingdomStoryProgress.ARC,3);
        data.setTradeRouteString("home_name","Unchanged");
        assertFalse(KingdomStaffDialogue.seen(data,PERSON)); assertTrue(KingdomStaffDialogue.remember(data,PERSON));
        assertFalse(KingdomStaffDialogue.remember(data,PERSON)); assertFalse(KingdomStaffDialogue.seen(quests.getPlayerData(other),PERSON));
        var saved=QuestState.TYPE.codec().encodeStart(NbtOps.INSTANCE,quests).getOrThrow();
        var restored=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,saved).getOrThrow().getPlayerData(owner);
        assertTrue(KingdomStaffDialogue.seen(restored,PERSON)); assertEquals(TOWN,KingdomStoryProgress.target(restored));
        assertEquals(3,restored.getStoryChapterProgress(KingdomStoryProgress.ARC)); assertEquals("Unchanged",restored.getTradeRouteString("home_name"));
        for(int i=0;i<16;i++) KingdomStaffDialogue.remember(restored,UUID.randomUUID());
        assertFalse(KingdomStaffDialogue.seen(restored,PERSON));
        assertEquals(16,restored.getTradeRouteString("kingdoms.staff.familiar").split(",").length);
    }
    @Test void everyReachableStaffSceneHasDistinctCompleteThreeLanguageTextAndBothActualNames() throws Exception {
        var keys=new HashSet<String>();
        for(var role:List.of(KingdomPersonRegistry.Role.LEADER,KingdomPersonRegistry.Role.INNKEEPER,KingdomPersonRegistry.Role.STOREKEEPER)) {
            for(int personality=0;personality<4;personality++) {
                var person=person(role,personality,null);
                assertTrue(keys.add(KingdomStaffDialogue.key(person,Status.ACTIVE,false)));
                assertTrue(keys.add(KingdomStaffDialogue.key(person,Status.ACTIVE,true)));
            }
            keys.add(KingdomStaffDialogue.key(person(role,0,null),Status.ABANDONED,false));
            keys.add(KingdomStaffDialogue.key(person(role,0,null),Status.RECOVERING,true));
            keys.add(KingdomStaffDialogue.key(person(role,0,UUID.randomUUID()),Status.ACTIVE,true));
        }
        assertEquals(33,keys.size());
        for(var locale:List.of("en_us","de_de","es_es")) {
            try(var reader=new InputStreamReader(getClass().getResourceAsStream("/assets/village-quest/lang/"+locale+".json"),StandardCharsets.UTF_8)) {
                var catalog=JsonParser.parseReader(reader).getAsJsonObject(); var texts=new HashSet<String>();
                for(var key:keys) {
                    assertTrue(catalog.has(key),locale+": "+key); String text=catalog.get(key).getAsString();
                    assertTrue(texts.add(text),"A distinct scene must not silently repeat another greeting: "+locale+" "+key);
                    String spoken=String.format(Locale.ROOT,text,"Mára","Peñabrisa");
                    assertTrue(spoken.contains("Mára") && spoken.contains("Peñabrisa"),locale+": "+key);
                    assertFalse(spoken.contains("%")); assertTrue(spoken.length()<1024);
                }
            }
        }
    }
}
