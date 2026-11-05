package de.quest.data;

import de.quest.quest.story.StoryArcType;
import de.quest.quest.special.RelicQuestStage;
import de.quest.quest.special.ShardRelicQuestStage;

/** Durable payout frontiers, independent from replayable personal quest progression. */
public final class PersonalResetReceipts {
    public static final String PREFIX = "personal_reset.paid.";
    private PersonalResetReceipts() {}
    public static boolean has(PlayerQuestData data, String reward) { return data.hasTradeRouteFlag(PREFIX + reward); }
    public static void record(PlayerQuestData data, String reward) { data.setTradeRouteFlag(PREFIX + reward, true); }
    public static boolean storyPaid(PlayerQuestData data, StoryArcType arc, int chapter) {
        return arc != null && data.getTradeRouteInt(PREFIX + "story." + arc.id()) > chapter;
    }
    public static void storyPaidThrough(PlayerQuestData data, StoryArcType arc, int chapters) {
        String key = PREFIX + "story." + arc.id();
        data.setTradeRouteInt(key, Math.max(data.getTradeRouteInt(key), chapters));
    }
    public static boolean friendPaid(PlayerQuestData data, String who, int stage) {
        return data.getTradeRouteInt(PREFIX + "friends." + who) > stage;
    }
    public static void friendPaidThrough(PlayerQuestData data, String who, int stages) {
        String key = PREFIX + "friends." + who;
        data.setTradeRouteInt(key, Math.max(data.getTradeRouteInt(key), stages));
    }
    public static void captureLegacy(PlayerQuestData data) {
        captureLegacy(data, data);
    }
    public static void captureLegacy(PlayerQuestData data, PlayerQuestData destination) {
        for (var arc : StoryArcType.values()) storyPaidThrough(destination, arc,
                data.getStoryCompleted().contains(arc.id()) ? arc.chapterCount() : data.getStoryChapterProgress(arc.id()));
        for (String who : new String[]{"simon", "lutte", "marius"}) friendPaidThrough(destination, who, data.getTradeRouteInt("friends." + who + ".stage"));
        if (data.getShardRelicQuestStage() == ShardRelicQuestStage.COMPLETED) record(destination, "relic.shards");
        if (data.getMerchantSealQuestStage() == RelicQuestStage.COMPLETED) record(destination, "relic.merchant");
        if (data.getShepherdFluteQuestStage() == RelicQuestStage.COMPLETED) record(destination, "relic.shepherd");
        if (data.getApiaristSmokerQuestStage() == RelicQuestStage.COMPLETED) record(destination, "relic.apiarist");
        if (data.getSurveyorCompassQuestStage() == RelicQuestStage.COMPLETED) record(destination, "relic.surveyor");
        if (data.hasTradeRouteFlag("guild_intro.first_daily_completed") || data.getLastRewardDay() != PlayerQuestData.UNSET_DAY) record(destination, "intro");
        if (data.hasTradeRouteFlag("guild_intro.welcome_completed")) record(destination, "welcome");
        if (data.getTradeRouteInt("friends.lutte.materials") == 1 || data.getTradeRouteInt("friends.lutte.stage") >= 2) record(destination, "supplies.lutte");
        if (data.getStoryChapterProgress(StoryArcType.SHRINES_BETWEEN_ROADS.id()) > 4
                || data.getActiveStoryArc() == StoryArcType.SHRINES_BETWEEN_ROADS && data.getStoryChapterProgress(StoryArcType.SHRINES_BETWEEN_ROADS.id()) == 4)
            record(destination, "supplies.shrine_chain");
        for (int i=0;i<de.quest.shrine.VillageBondService.historicalVillageCount(data);i++) {
            String key=de.quest.shrine.VillageBondService.villageKey(i, "");
            if (data.getTradeRouteInt(key+"afterstory_state")==2) {
                String dimension=data.getTradeRouteString(key+"dimension");
                if(dimension.isBlank())dimension="minecraft:overworld";
                record(destination,"afterstory."+dimension+":"+data.getTradeRouteInt(key+"x")+":"+data.getTradeRouteInt(key+"z"));
            }
        }
        for (String key : data.getMilestoneFlags()) if (key.startsWith("guild_town.reward.")) record(destination, "town." + key.substring("guild_town.reward.".length()));
    }
}
