package de.quest.network;

import java.util.HashSet;
import java.util.Set;

/** Only fixed content identifiers may leave the client. No server-provided free text. */
public final class GameplayStatsMetrics {
    private static final Set<String> CONTENT = new HashSet<>();
    private static final Set<String> ALLOWED = new HashSet<>();
    static {
        String daily = "honey pet_collar wheat_harvest potato_harvest woodcutting coal_mining wool_weaving river_meal autumn_harvest smith_smelting stall_new_life village_trading market_rounds zombie_cull skeleton_patrol spider_sweep creeper_watch";
        quests("daily", daily, true); quests("bonus", daily, true);
        quests("weekly", "harvest_for_village bakehouse_stock smith_week stall_and_pasture market_week night_watch road_warden", true);
        quests("pilgrim", "roadmarks_for_the_compass quench_for_the_hall wool_before_rain tracks_in_the_dark fangs_by_the_hedgerow ash_on_the_pass smoke_over_blackstone stillness_beyond_the_gate", false);
        quests("encounter", "provisions company greetings zombies iron light", false);
        for (String pair : "failing_harvest:4 silent_forge:4 market_road_troubles:4 restless_pens:4 shadows_on_the_trade_road:6 the_empty_caravan:6 shrines_between_roads:6 night_bells:4 living_kingdoms:5 artifact_journey:5".split(" ")) {
            String[] arc = pair.split(":");
            for (int chapter = 1; chapter <= Integer.parseInt(arc[1]); chapter++) {
                ALLOWED.add("story.started." + arc[0] + "_" + chapter);
                ALLOWED.add("story.completed." + arc[0] + "_" + chapter);
            }
        }

        quests("special", "relic merchant_seal shepherd_flute apiarist_smoker surveyor_compass friend_simon_1 friend_simon_2 friend_simon_3 friend_lutte_1 friend_lutte_2 friend_lutte_3 friend_marius_1 friend_marius_2 friend_marius_3 simon_herbs", false);
        quests("afterstory", "granary forge pasture apiary archive", false);
        quests("town", "shared_table sparks_for_the_road long_drive lanterns_in_bloom ink_between_villages", false);
        quests("commission", "reinforced_ploughs winter_feed pollinated_orchards seed_register horseshoes_and_tack smoker_and_lantern_fittings tool_register flowering_pastures herd_register wax_sealed_road_books", true);
        quests("freight", "bakers roadworks iron archive fodder apiary watch festival forge_fuel leather_bales wax_reserves writers_paper", true);
        quests("shared", "common_storehouse road_waymarkers travelling_archive", false);
        for (String key : "village_ledger apiary_charter forge_charter market_charter pasture_charter watch_bell caravan_yard wayshrine_network".split(" ")) ALLOWED.add("project.unlocked."+key);
        for (String action : "aid town freight friends acceptance turnin".split(" "))
            for (String reason : "anchor stale materials prerequisite cooldown capacity unavailable".split(" ")) ALLOWED.add("reject."+action+"."+reason);
        Set<String> base = Set.copyOf(ALLOWED);
        for (String metric : base) {
            String[] parts=metric.split("\\.",3);
            if (!parts[1].equals("started")) continue;
            CONTENT.add(parts[0]+"."+parts[2]);
            for (String outcome : new String[]{"completed","abandoned"})
                for (String coverage : new String[]{"full","partial"})
                    for (String bucket : "lt1m 1to5m 5to15m 15to60m 1to4h ge4h unknown".split(" "))
                        ALLOWED.add("duration."+outcome+"_"+coverage+"_"+bucket+"."+parts[2]);
        }
        ALLOWED.add("play.seconds.active");
        for (String bucket : "lt1m 1to5m 5to15m 15to60m 1to4h ge4h".split(" ")) ALLOWED.add("session.length."+bucket);
        for (String bucket : "first same_day next_day 2to7d 8to30d gt30d".split(" ")) ALLOWED.add("return.gap."+bucket);
        for (String bucket : "lt30 30to59 ge60".split(" ")) ALLOWED.add("performance.fps."+bucket);
        for (String difficulty : "peaceful easy normal hard".split(" ")) ALLOWED.add("context.difficulty."+difficulty);
        for (String profile : "relaxed standard hardened".split(" ")) ALLOWED.add("context.profile."+profile);
        for (String target : new String[]{"general","daily","weekly","story","special","town","commission","freight","shared","afterstory"})
            for (String answer : "helpful confusing easy balanced hard progress rewards ui".split(" ")) ALLOWED.add("feedback."+target+"."+answer);
    }
    private GameplayStatsMetrics() {}
    private static void quests(String prefix, String keys, boolean cancel) {
        for (String key : keys.split(" ")) {
            ALLOWED.add("quest.started." + prefix + "_" + key);
            ALLOWED.add("quest.completed." + prefix + "_" + key);
            if (cancel) ALLOWED.add("quest.abandoned." + prefix + "_" + key);
        }
    }
    public static boolean content(String key) { return CONTENT.contains(key); }
    public static boolean valid(String metric) { return metric != null && ALLOWED.contains(metric); }
    public static Set<String> all() { return Set.copyOf(ALLOWED); }
}
