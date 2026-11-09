package de.quest.network;
import static org.junit.jupiter.api.Assertions.*;
import de.quest.quest.daily.DailyQuestService.DailyQuestType;
import de.quest.quest.weekly.WeeklyQuestService.WeeklyQuestType;
import de.quest.quest.story.StoryArcType;
import de.quest.pilgrim.PilgrimContractType;
import de.quest.ambient.EncounterQuestProgress.Kind;
import org.junit.jupiter.api.Test;
final class GameplayStatsMetricsTest {
    @org.junit.jupiter.api.BeforeAll static void registries() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    @Test void allowlistMatchesAllActualHookCatalogs() {
        for (var type : DailyQuestType.values()) for (String prefix : new String[]{"daily_", "bonus_"})
            for (String action : new String[]{"started", "completed", "abandoned"})
                assertTrue(GameplayStatsMetrics.valid("quest."+action+"."+prefix+type.name().toLowerCase(java.util.Locale.ROOT)));
        for (var type : WeeklyQuestType.values()) for (String action : new String[]{"started", "completed", "abandoned"})
            assertTrue(GameplayStatsMetrics.valid("quest."+action+".weekly_"+type.name().toLowerCase(java.util.Locale.ROOT)));
        for (var type : PilgrimContractType.values()) for (String action : new String[]{"started", "completed"})
            assertTrue(GameplayStatsMetrics.valid("quest."+action+".pilgrim_"+type.id()));
        for (var type : Kind.values()) for (String action : new String[]{"started", "completed"})
            assertTrue(GameplayStatsMetrics.valid("quest."+action+".encounter_"+type.key()));
        for (var arc : StoryArcType.values()) for (int chapter=1;chapter<=arc.chapterCount();chapter++)
            for (String action : new String[]{"started", "completed"})
                assertTrue(GameplayStatsMetrics.valid("story."+action+"."+arc.id()+"_"+chapter));
    }
    @Test void noFreeTextOrPlayerIdsCanBeEncodedAsMetrics() {
        for (String metric : new String[]{"quest.started.player_alice","quest.completed.123e4567-e89b-12d3-a456-426614174000","story.started.failing_harvest_99","story.abandoned.failing_harvest_1","quest.completed.daily_honey\nname",""})
            assertFalse(GameplayStatsMetrics.valid(metric),metric);
        assertFalse(GameplayStatsMetrics.valid(null));
        assertThrows(UnsupportedOperationException.class,()->GameplayStatsMetrics.all().add("quest.started.alice"));
    }

    @Test void allClientMetricsExactlyMatchTheBackendAndNewNativeContent() throws Exception {
        var catalog=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of("stats/metrics.json"))).getAsJsonArray();
        var expected=new java.util.HashSet<String>();for(var metric:catalog)expected.add(metric.getAsString());
        assertEquals(expected,GameplayStatsMetrics.all());
        for(var type:de.quest.caravan.TradeContractType.values()) assertTrue(GameplayStatsMetrics.valid("quest.completed.freight_"+type.key()));
        for(var type:de.quest.guildtown.GuildTownStory.values()) assertTrue(GameplayStatsMetrics.valid("quest.started.town_"+type.key()));
        for(var type:de.quest.guildtown.GuildTownCommission.values()) assertTrue(GameplayStatsMetrics.valid("quest.abandoned.commission_"+type.key()));
        for(var type:de.quest.guildtown.GuildTownSharedProject.values()) assertTrue(GameplayStatsMetrics.valid("quest.completed.shared_"+type.key()));
    }
}
