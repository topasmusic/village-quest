package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.archive.GuildArchiveService.ArchiveItem;
import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.guildtown.GuildTownProgress;
import de.quest.quest.story.StoryArcType;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

final class ArtifactJourneyProgressTest {
    private static final UUID TOWN=UUID.randomUUID(),OTHER=UUID.randomUUID();
    private static PlayerQuestData start(int chapter) {
        var data=new PlayerQuestData(); data.setActiveStoryArc(StoryArcType.ARTIFACT_JOURNEY);
        data.setStoryChapterProgress(ArtifactJourneyProgress.ARC,chapter);
        assertTrue(ArtifactJourneyProgress.begin(data,TOWN)); return data;
    }
    @Test void onlyRealConcordVillageCompletionAndAllPersonalRightsUnlockEntry() {
        var data=new PlayerQuestData(); var rights=EnumSet.allOf(ArchiveItem.class);
        assertFalse(ArtifactJourneyProgress.unlocked(data,rights));
        data.setStoryCompleted("night_bells",true); data.setStoryCompleted("living_kingdoms",true);
        assertFalse(ArtifactJourneyProgress.unlocked(data,rights));
        GuildTownProgress.markFinaleClaimed(data);
        assertTrue(ArtifactJourneyProgress.unlocked(data,rights));
        rights.remove(ArchiveItem.STARREACH_RING); assertFalse(ArtifactJourneyProgress.unlocked(data,rights));
        rights.add(ArchiveItem.STARREACH_RING); rights.remove(ArchiveItem.VILLAGE_CHECK);
        assertFalse(ArtifactJourneyProgress.unlocked(data,rights));
    }
    @Test void phaseTargetEpochAndReplayAreCheckedBeforeProgressChanges() {
        var data=start(0); var epoch=ArtifactJourneyProgress.epoch(data);
        assertFalse(ArtifactJourneyProgress.record(data,OTHER,epoch,ArtifactJourneyProgress.Deed.NAVIGATE,"nav"));
        assertFalse(ArtifactJourneyProgress.record(data,TOWN,UUID.randomUUID(),ArtifactJourneyProgress.Deed.NAVIGATE,"nav"));
        assertFalse(ArtifactJourneyProgress.record(data,TOWN,epoch,ArtifactJourneyProgress.Deed.HIVE,"hive"));
        assertTrue(ArtifactJourneyProgress.record(data,TOWN,epoch,ArtifactJourneyProgress.Deed.NAVIGATE,"nav"));
        assertFalse(ArtifactJourneyProgress.record(data,TOWN,epoch,ArtifactJourneyProgress.Deed.NAVIGATE,"nav"));
        assertFalse(ArtifactJourneyProgress.ready(data));
        assertTrue(ArtifactJourneyProgress.record(data,TOWN,epoch,ArtifactJourneyProgress.Deed.LEDGER,"ledger"));
        assertTrue(ArtifactJourneyProgress.record(data,TOWN,epoch,ArtifactJourneyProgress.Deed.SIGIL,"sigil"));
        assertTrue(ArtifactJourneyProgress.ready(data));
        data.setActiveStoryArc(null); assertFalse(ArtifactJourneyProgress.ready(data));
    }
    @Test void rebindAndReacceptDiscardStaleSceneActionsButKeepEarnedChapters() {
        var data=start(2); var epoch=ArtifactJourneyProgress.epoch(data);
        ArtifactJourneyProgress.record(data,TOWN,epoch,ArtifactJourneyProgress.Deed.CHECK,"check");
        assertTrue(ArtifactJourneyProgress.begin(data,OTHER));
        assertFalse(ArtifactJourneyProgress.has(data,ArtifactJourneyProgress.Deed.CHECK));
        assertEquals(2,data.getStoryChapterProgress(ArtifactJourneyProgress.ARC));
        assertFalse(ArtifactJourneyProgress.record(data,OTHER,epoch,ArtifactJourneyProgress.Deed.REACH,"old"));
        assertTrue(ArtifactJourneyProgress.begin(data,OTHER));
        assertNotEquals(epoch,ArtifactJourneyProgress.epoch(data));
    }
    @Test void savedPersonalProofsAndCharterSurviveReloadWithoutMintingCompletion() {
        var quests=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow(); var owner=UUID.randomUUID();
        var data=quests.getPlayerData(owner); data.setActiveStoryArc(StoryArcType.ARTIFACT_JOURNEY);
        data.setStoryChapterProgress(ArtifactJourneyProgress.ARC,4); ArtifactJourneyProgress.begin(data,TOWN);
        assertFalse(ArtifactJourneyProgress.choose(data,1));
        var epoch=ArtifactJourneyProgress.epoch(data);
        for(var deed:ArtifactJourneyProgress.required(4)) assertTrue(ArtifactJourneyProgress.record(data,TOWN,epoch,deed,deed.name()));
        assertFalse(ArtifactJourneyProgress.ready(data)); assertTrue(ArtifactJourneyProgress.choose(data,1));
        assertTrue(ArtifactJourneyProgress.ready(data));
        quests=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,QuestState.TYPE.codec().encodeStart(NbtOps.INSTANCE,quests).getOrThrow()).getOrThrow();
        data=quests.getPlayerData(owner); assertEquals(1,ArtifactJourneyProgress.choice(data)); assertTrue(ArtifactJourneyProgress.ready(data));
        assertFalse(data.hasStoryCompleted(ArtifactJourneyProgress.ARC));
        assertFalse(GuildTownProgress.finaleClaimed(data));
    }
    @Test void futureSchemaAndUnboundedEvidenceCannotBeRewrittenOrAccepted() {
        var data=start(0); var epoch=ArtifactJourneyProgress.epoch(data);
        assertFalse(ArtifactJourneyProgress.record(data,TOWN,epoch,ArtifactJourneyProgress.Deed.NAVIGATE,"x".repeat(129)));
        data.setTradeRouteInt(ArtifactJourneyProgress.PREFIX+"schema",99);
        assertFalse(ArtifactJourneyProgress.begin(data,OTHER));
        assertFalse(ArtifactJourneyProgress.record(data,TOWN,epoch,ArtifactJourneyProgress.Deed.NAVIGATE,"nav"));
        assertEquals(99,data.getTradeRouteInt(ArtifactJourneyProgress.PREFIX+"schema"));
    }
    @Test void aCanceledOrReacceptedFreightContractCannotBorrowAnOldSatchelProof() {
        var data=start(3); var route=UUID.randomUUID(); var contract=UUID.randomUUID();
        assertFalse(ArtifactJourneyProgress.pack(data,route,4,contract));
        ArtifactJourneyProgress.record(data,TOWN,ArtifactJourneyProgress.epoch(data),ArtifactJourneyProgress.Deed.SATCHEL,"packed");
        assertTrue(ArtifactJourneyProgress.pack(data,route,4,contract));
        assertTrue(ArtifactJourneyProgress.matchesCargo(data,route,4,contract));
        assertFalse(ArtifactJourneyProgress.matchesCargo(data,route,4,UUID.randomUUID()));
        assertFalse(ArtifactJourneyProgress.matchesCargo(data,route,5,contract));
        assertFalse(ArtifactJourneyProgress.matchesCargo(data,UUID.randomUUID(),4,contract));
        ArtifactJourneyProgress.begin(data,TOWN);
        assertFalse(ArtifactJourneyProgress.matchesCargo(data,route,4,contract));
    }
    @Test void lostTargetCanBeReplacedBetweenChaptersWithoutTakingAnotherStorySlot() {
        var data=new PlayerQuestData(); data.setStoryChapterProgress(ArtifactJourneyProgress.ARC,2);
        assertTrue(ArtifactJourneyProgress.select(data,OTHER)); assertEquals(OTHER,ArtifactJourneyProgress.target(data));
        assertNull(data.getActiveStoryArc()); assertEquals(2,data.getStoryChapterProgress(ArtifactJourneyProgress.ARC));
        data.setActiveStoryArc(StoryArcType.NIGHT_BELLS); assertFalse(ArtifactJourneyProgress.select(data,TOWN));
        assertEquals(OTHER,ArtifactJourneyProgress.target(data));
    }
    @Test void allFiveChaptersNeedTheirOwnFreshActionsAndTheFinalCharter() {
        var data=start(0);
        for(int chapter=0;chapter<5;chapter++) {
            data.setStoryChapterProgress(ArtifactJourneyProgress.ARC,chapter); ArtifactJourneyProgress.begin(data,TOWN);
            assertFalse(ArtifactJourneyProgress.ready(data));
            for(var deed:ArtifactJourneyProgress.required(chapter))
                assertTrue(ArtifactJourneyProgress.record(data,TOWN,ArtifactJourneyProgress.epoch(data),deed,deed.name()));
            if(chapter==4) { assertFalse(ArtifactJourneyProgress.ready(data)); ArtifactJourneyProgress.choose(data,2); }
            assertTrue(ArtifactJourneyProgress.ready(data));
        }
    }
    @Test void charterPreferenceIsSetBeforeExistingRewardServiceOpensTheClaimScreen() {
        var data=start(4);
        for(var deed:ArtifactJourneyProgress.required(4)) ArtifactJourneyProgress.record(data,TOWN,ArtifactJourneyProgress.epoch(data),deed,deed.name());
        assertTrue(ArtifactJourneyProgress.choose(data,2));
        assertEquals("roads",data.vanillaRewards().preference(),"VanillaRewardService.complete opens the screen before chapter.onClaimed");
        assertTrue(ArtifactJourneyProgress.choose(data,1)); assertEquals("supplies",data.vanillaRewards().preference());
    }
}
