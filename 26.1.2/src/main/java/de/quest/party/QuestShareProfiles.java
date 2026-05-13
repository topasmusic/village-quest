package de.quest.party;

import de.quest.pilgrim.PilgrimContractType;
import de.quest.quest.daily.DailyQuestKeys;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryQuestKeys;
import de.quest.quest.weekly.WeeklyQuestKeys;
import de.quest.quest.weekly.WeeklyQuestService;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class QuestShareProfiles {
    private static final Map<DailyQuestService.DailyQuestType, SharedQuestProfile> DAILY = new EnumMap<>(DailyQuestService.DailyQuestType.class);
    private static final Map<WeeklyQuestService.WeeklyQuestType, SharedQuestProfile> WEEKLY = new EnumMap<>(WeeklyQuestService.WeeklyQuestType.class);
    private static final Map<String, SharedQuestProfile> STORY = new HashMap<>();
    private static final Map<PilgrimContractType, SharedQuestProfile> PILGRIM = new EnumMap<>(PilgrimContractType.class);

    static {
        DAILY.put(DailyQuestService.DailyQuestType.HONEY, profile(
                Set.of(DailyQuestKeys.HONEY_PROGRESS, DailyQuestKeys.COMB_PROGRESS),
                Set.of(),
                Set.of()
        ));
        DAILY.put(DailyQuestService.DailyQuestType.PET_COLLAR, profile(
                Set.of(),
                Set.of(DailyQuestKeys.PET_COLLAR_DONE),
                Set.of()
        ));
        DAILY.put(DailyQuestService.DailyQuestType.WHEAT_HARVEST, profile(
                Set.of(DailyQuestKeys.WHEAT_PROGRESS, DailyQuestKeys.BREAD_PROGRESS),
                Set.of(),
                Set.of()
        ));
        DAILY.put(DailyQuestService.DailyQuestType.POTATO_HARVEST, profile(
                Set.of(DailyQuestKeys.POTATO_PROGRESS, DailyQuestKeys.CARROT_PROGRESS),
                Set.of(),
                Set.of()
        ));
        DAILY.put(DailyQuestService.DailyQuestType.WOODCUTTING, profile(
                Set.of(DailyQuestKeys.WOOD_PROGRESS),
                Set.of(),
                Set.of()
        ));
        DAILY.put(DailyQuestService.DailyQuestType.COAL_MINING, profile(
                Set.of(DailyQuestKeys.COAL_PROGRESS, DailyQuestKeys.IRON_PROGRESS),
                Set.of(),
                Set.of()
        ));
        DAILY.put(DailyQuestService.DailyQuestType.WOOL_WEAVING, profile(
                Set.of(DailyQuestKeys.SHEEP_PROGRESS, DailyQuestKeys.WOOL_PROGRESS),
                Set.of(),
                Set.of()
        ));
        DAILY.put(DailyQuestService.DailyQuestType.RIVER_MEAL, profile(
                Set.of(DailyQuestKeys.RIVER_FISH_PROGRESS, DailyQuestKeys.RIVER_COOKED_FISH_PROGRESS),
                Set.of(),
                Set.of()
        ));
        DAILY.put(DailyQuestService.DailyQuestType.AUTUMN_HARVEST, profile(
                Set.of(DailyQuestKeys.AUTUMN_PUMPKIN_PROGRESS, DailyQuestKeys.AUTUMN_MELON_PROGRESS),
                Set.of(),
                Set.of()
        ));
        DAILY.put(DailyQuestService.DailyQuestType.SMITH_SMELTING, profile(
                Set.of(DailyQuestKeys.SMITH_SMELT_ORE_PROGRESS, DailyQuestKeys.SMITH_SMELT_INGOT_PROGRESS),
                Set.of(),
                Set.of()
        ));
        DAILY.put(DailyQuestService.DailyQuestType.STALL_NEW_LIFE, profile(
                Set.of(DailyQuestKeys.STALL_BREED_PROGRESS),
                Set.of(),
                Set.of()
        ));
        DAILY.put(DailyQuestService.DailyQuestType.VILLAGE_TRADING, profile(
                Set.of(DailyQuestKeys.TRADE_PROGRESS, DailyQuestKeys.TRADE_EMERALD_PROGRESS),
                Set.of(),
                Set.of()
        ));
        DAILY.put(DailyQuestService.DailyQuestType.MARKET_ROUNDS, profile(
                Set.of(DailyQuestKeys.MARKET_ROUNDS_VISITS, DailyQuestKeys.MARKET_ROUNDS_TRADES),
                Set.of(),
                Set.of(DailyQuestKeys.MARKET_ROUNDS_VISITED_PREFIX)
        ));
        DAILY.put(DailyQuestService.DailyQuestType.ZOMBIE_CULL, profile(
                Set.of(DailyQuestKeys.ZOMBIE_CULL_PROGRESS),
                Set.of(),
                Set.of()
        ));
        DAILY.put(DailyQuestService.DailyQuestType.SKELETON_PATROL, profile(
                Set.of(DailyQuestKeys.SKELETON_PATROL_PROGRESS),
                Set.of(),
                Set.of()
        ));
        DAILY.put(DailyQuestService.DailyQuestType.SPIDER_SWEEP, profile(
                Set.of(DailyQuestKeys.SPIDER_SWEEP_PROGRESS),
                Set.of(),
                Set.of()
        ));
        DAILY.put(DailyQuestService.DailyQuestType.CREEPER_WATCH, profile(
                Set.of(DailyQuestKeys.CREEPER_WATCH_PROGRESS),
                Set.of(),
                Set.of()
        ));

        WEEKLY.put(WeeklyQuestService.WeeklyQuestType.HARVEST_FOR_VILLAGE, profile(
                Set.of(
                        WeeklyQuestKeys.HARVEST_WHEAT,
                        WeeklyQuestKeys.HARVEST_CARROT,
                        WeeklyQuestKeys.HARVEST_POTATO,
                        WeeklyQuestKeys.HARVEST_BREAD
                ),
                Set.of(),
                Set.of()
        ));
        WEEKLY.put(WeeklyQuestService.WeeklyQuestType.BAKEHOUSE_STOCK, profile(
                Set.of(
                        WeeklyQuestKeys.BAKEHOUSE_BREAD,
                        WeeklyQuestKeys.BAKEHOUSE_PIE,
                        WeeklyQuestKeys.BAKEHOUSE_POTATO
                ),
                Set.of(),
                Set.of()
        ));
        WEEKLY.put(WeeklyQuestService.WeeklyQuestType.SMITH_WEEK, profile(
                Set.of(
                        WeeklyQuestKeys.SMITH_ORE,
                        WeeklyQuestKeys.SMITH_GOLD_ORE,
                        WeeklyQuestKeys.SMITH_IRON,
                        WeeklyQuestKeys.SMITH_GOLD
                ),
                Set.of(),
                Set.of()
        ));
        WEEKLY.put(WeeklyQuestService.WeeklyQuestType.STALL_AND_PASTURE, profile(
                Set.of(
                        WeeklyQuestKeys.PASTURE_BREED,
                        WeeklyQuestKeys.PASTURE_SHEAR,
                        WeeklyQuestKeys.PASTURE_WOOL
                ),
                Set.of(),
                Set.of()
        ));
        WEEKLY.put(WeeklyQuestService.WeeklyQuestType.MARKET_WEEK, profile(
                Set.of(
                        WeeklyQuestKeys.MARKET_TRADES,
                        WeeklyQuestKeys.MARKET_EMERALDS,
                        WeeklyQuestKeys.MARKET_PILGRIM_PURCHASES
                ),
                Set.of(),
                Set.of()
        ));
        WEEKLY.put(WeeklyQuestService.WeeklyQuestType.NIGHT_WATCH, profile(
                Set.of(
                        WeeklyQuestKeys.NIGHTWATCH_ZOMBIES,
                        WeeklyQuestKeys.NIGHTWATCH_SKELETONS
                ),
                Set.of(),
                Set.of()
        ));
        WEEKLY.put(WeeklyQuestService.WeeklyQuestType.ROAD_WARDEN, profile(
                Set.of(
                        WeeklyQuestKeys.ROADWARDEN_HOSTILES,
                        WeeklyQuestKeys.ROADWARDEN_CREEPERS
                ),
                Set.of(),
                Set.of()
        ));

        STORY.put(storyKey(StoryArcType.FAILING_HARVEST, 0), profile(
                Set.of(StoryQuestKeys.FAILING_HARVEST_WHEAT, StoryQuestKeys.FAILING_HARVEST_POTATO),
                Set.of(),
                Set.of()
        ));
        STORY.put(storyKey(StoryArcType.FAILING_HARVEST, 1), profile(
                Set.of(StoryQuestKeys.FAILING_HARVEST_HONEY, StoryQuestKeys.FAILING_HARVEST_COMB),
                Set.of(),
                Set.of()
        ));
        STORY.put(storyKey(StoryArcType.FAILING_HARVEST, 2), profile(
                Set.of(StoryQuestKeys.FAILING_HARVEST_BREAD, StoryQuestKeys.FAILING_HARVEST_BAKED_POTATO),
                Set.of(),
                Set.of()
        ));
        STORY.put(storyKey(StoryArcType.FAILING_HARVEST, 3), profile(
                Set.of(StoryQuestKeys.FAILING_HARVEST_TRADES, StoryQuestKeys.FAILING_HARVEST_EMERALDS),
                Set.of(),
                Set.of()
        ));

        STORY.put(storyKey(StoryArcType.SILENT_FORGE, 0), profile(
                Set.of(
                        StoryQuestKeys.SILENT_FORGE_COAL_ORE,
                        StoryQuestKeys.SILENT_FORGE_IRON_ORE,
                        StoryQuestKeys.SILENT_FORGE_REDSTONE_ORE,
                        StoryQuestKeys.SILENT_FORGE_GOLD_ORE,
                        StoryQuestKeys.SILENT_FORGE_DIAMOND_ORE
                ),
                Set.of(),
                Set.of()
        ));
        STORY.put(storyKey(StoryArcType.SILENT_FORGE, 1), profile(
                Set.of(StoryQuestKeys.SILENT_FORGE_IRON_INGOT),
                Set.of(),
                Set.of()
        ));
        STORY.put(storyKey(StoryArcType.SILENT_FORGE, 2), profile(
                Set.of(
                        StoryQuestKeys.SILENT_FORGE_PICKAXE_CRAFTED,
                        StoryQuestKeys.SILENT_FORGE_BUCKET_CRAFTED,
                        StoryQuestKeys.SILENT_FORGE_SHEARS_CRAFTED,
                        StoryQuestKeys.SILENT_FORGE_SHIELD_CRAFTED
                ),
                Set.of(),
                Set.of()
        ));
        STORY.put(storyKey(StoryArcType.SILENT_FORGE, 3), profile(
                Set.of(
                        StoryQuestKeys.SILENT_FORGE_SHARPNESS_BOOK,
                        StoryQuestKeys.SILENT_FORGE_FIRE_PROTECTION_BOOK,
                        StoryQuestKeys.SILENT_FORGE_PROTECTION_BOOK,
                        StoryQuestKeys.SILENT_FORGE_BLAST_PROTECTION_BOOK,
                        StoryQuestKeys.SILENT_FORGE_PROJECTILE_PROTECTION_BOOK,
                        StoryQuestKeys.SILENT_FORGE_MASTER_EDGE,
                        StoryQuestKeys.SILENT_FORGE_MASTER_HELM,
                        StoryQuestKeys.SILENT_FORGE_MASTER_CHEST,
                        StoryQuestKeys.SILENT_FORGE_MASTER_LEGS,
                        StoryQuestKeys.SILENT_FORGE_MASTER_BOOTS
                ),
                Set.of(),
                Set.of()
        ));

        STORY.put(storyKey(StoryArcType.MARKET_ROAD_TROUBLES, 0), profile(
                Set.of(StoryQuestKeys.MARKET_ROAD_EMERALDS),
                Set.of(),
                Set.of()
        ));
        STORY.put(storyKey(StoryArcType.MARKET_ROAD_TROUBLES, 1), profile(
                Set.of(StoryQuestKeys.MARKET_ROAD_PAPER_CRAFTED, StoryQuestKeys.MARKET_ROAD_BOOK_CRAFTED),
                Set.of(),
                Set.of()
        ));
        STORY.put(storyKey(StoryArcType.MARKET_ROAD_TROUBLES, 2), profile(
                Set.of(StoryQuestKeys.MARKET_ROAD_TRADES),
                Set.of(
                        StoryQuestKeys.MARKET_ROAD_TOOLSMITH,
                        StoryQuestKeys.MARKET_ROAD_WEAPONSMITH,
                        StoryQuestKeys.MARKET_ROAD_FARMER,
                        StoryQuestKeys.MARKET_ROAD_FISHERMAN,
                        StoryQuestKeys.MARKET_ROAD_SHEPHERD,
                        StoryQuestKeys.MARKET_ROAD_LIBRARIAN
                ),
                Set.of()
        ));
        STORY.put(storyKey(StoryArcType.MARKET_ROAD_TROUBLES, 3), profile(
                Set.of(StoryQuestKeys.MARKET_ROAD_VILLAGERS, StoryQuestKeys.MARKET_ROAD_BELL),
                Set.of(),
                Set.of(StoryQuestKeys.MARKET_ROAD_GATHERED_PREFIX)
        ));

        STORY.put(storyKey(StoryArcType.RESTLESS_PENS, 0), profile(
                Set.of(StoryQuestKeys.RESTLESS_PENS_BREEDS),
                Set.of(),
                Set.of()
        ));
        STORY.put(storyKey(StoryArcType.RESTLESS_PENS, 1), profile(
                Set.of(StoryQuestKeys.RESTLESS_PENS_SHEARS),
                Set.of(),
                Set.of()
        ));
        STORY.put(storyKey(StoryArcType.RESTLESS_PENS, 2), profile(
                Set.of(StoryQuestKeys.RESTLESS_PENS_RIDE),
                Set.of(),
                Set.of()
        ));
        STORY.put(storyKey(StoryArcType.RESTLESS_PENS, 3), profile(
                Set.of(StoryQuestKeys.RESTLESS_PENS_CALL),
                Set.of(),
                Set.of()
        ));

        STORY.put(storyKey(StoryArcType.NIGHT_BELLS, 0), profile(
                Set.of(StoryQuestKeys.NIGHT_BELLS_ZOMBIES),
                Set.of(),
                Set.of()
        ));
        STORY.put(storyKey(StoryArcType.NIGHT_BELLS, 1), profile(
                Set.of(StoryQuestKeys.NIGHT_BELLS_SKELETONS, StoryQuestKeys.NIGHT_BELLS_SPIDERS),
                Set.of(),
                Set.of()
        ));
        STORY.put(storyKey(StoryArcType.NIGHT_BELLS, 2), profile(
                Set.of(StoryQuestKeys.NIGHT_BELLS_CREEPERS, StoryQuestKeys.NIGHT_BELLS_HOSTILES),
                Set.of(),
                Set.of()
        ));
        STORY.put(storyKey(StoryArcType.NIGHT_BELLS, 3), profile(
                Set.of(StoryQuestKeys.NIGHT_BELLS_RAID_WON),
                Set.of(StoryQuestKeys.NIGHT_BELLS_RAID_WAIT_FOR_FRESH),
                Set.of()
        ));

        PILGRIM.put(PilgrimContractType.QUENCH_FOR_THE_HALL, profile(
                Set.of("pilgrim_lantern_skeletons"),
                Set.of(),
                Set.of()
        ));
        PILGRIM.put(PilgrimContractType.WOOL_BEFORE_RAIN, profile(
                Set.of("pilgrim_smoke_creepers"),
                Set.of(),
                Set.of()
        ));
        PILGRIM.put(PilgrimContractType.TRACKS_IN_THE_DARK, profile(
                Set.of("pilgrim_tracks_zombies"),
                Set.of(),
                Set.of()
        ));
        PILGRIM.put(PilgrimContractType.FANGS_BY_THE_HEDGEROW, profile(
                Set.of("pilgrim_fangs_spiders"),
                Set.of(),
                Set.of()
        ));
        PILGRIM.put(PilgrimContractType.ASH_ON_THE_PASS, profile(
                Set.of("pilgrim_ash_blazes", "pilgrim_ash_wither_skeletons"),
                Set.of(),
                Set.of()
        ));
        PILGRIM.put(PilgrimContractType.SMOKE_OVER_BLACKSTONE, profile(
                Set.of("pilgrim_blackstone_magma_cubes", "pilgrim_blackstone_ghasts"),
                Set.of(),
                Set.of()
        ));
        PILGRIM.put(PilgrimContractType.STILLNESS_BEYOND_THE_GATE, profile(
                Set.of("pilgrim_stillness_endermen", "pilgrim_stillness_shulkers"),
                Set.of(),
                Set.of()
        ));
    }

    private QuestShareProfiles() {}

    public static boolean isDailyShareable(DailyQuestService.DailyQuestType type) {
        return type != null && DAILY.containsKey(type);
    }

    public static boolean isWeeklyShareable(WeeklyQuestService.WeeklyQuestType type) {
        return type != null && WEEKLY.containsKey(type);
    }

    public static boolean sharesDailyInt(DailyQuestService.DailyQuestType type, String key) {
        return shares(DAILY.get(type), key, false);
    }

    public static boolean sharesDailyFlag(DailyQuestService.DailyQuestType type, String key) {
        return shares(DAILY.get(type), key, true);
    }

    public static boolean sharesWeeklyInt(WeeklyQuestService.WeeklyQuestType type, String key) {
        return shares(WEEKLY.get(type), key, false);
    }

    public static boolean sharesWeeklyFlag(WeeklyQuestService.WeeklyQuestType type, String key) {
        return shares(WEEKLY.get(type), key, true);
    }

    public static SharedQuestProfile dailyProfile(DailyQuestService.DailyQuestType type) {
        return DAILY.get(type);
    }

    public static SharedQuestProfile weeklyProfile(WeeklyQuestService.WeeklyQuestType type) {
        return WEEKLY.get(type);
    }

    public static boolean isStoryShareable(StoryArcType type) {
        return type != null && type != StoryArcType.SHADOWS_ON_THE_TRADE_ROAD;
    }

    public static boolean sharesStoryInt(StoryArcType type, int chapterIndex, String key) {
        return shares(STORY.get(storyKey(type, chapterIndex)), key, false);
    }

    public static boolean sharesStoryFlag(StoryArcType type, int chapterIndex, String key) {
        return shares(STORY.get(storyKey(type, chapterIndex)), key, true);
    }

    public static SharedQuestProfile storyProfile(StoryArcType type, int chapterIndex) {
        return STORY.get(storyKey(type, chapterIndex));
    }

    public static String storyKey(StoryArcType type, int chapterIndex) {
        return type == null ? "" : type.id() + "#" + chapterIndex;
    }

    public static boolean isPilgrimShareable(PilgrimContractType type) {
        return type != null && PILGRIM.containsKey(type);
    }

    public static boolean sharesPilgrimInt(PilgrimContractType type, String key) {
        return shares(PILGRIM.get(type), key, false);
    }

    public static boolean sharesPilgrimFlag(PilgrimContractType type, String key) {
        return shares(PILGRIM.get(type), key, true);
    }

    public static SharedQuestProfile pilgrimProfile(PilgrimContractType type) {
        return PILGRIM.get(type);
    }

    private static SharedQuestProfile profile(Set<String> intKeys, Set<String> flagKeys, Set<String> flagPrefixes) {
        return new SharedQuestProfile(intKeys, flagKeys, flagPrefixes);
    }

    private static boolean shares(SharedQuestProfile profile, String key, boolean flag) {
        if (profile == null || key == null || key.isEmpty()) {
            return false;
        }
        return flag ? profile.matchesFlag(key) : profile.intKeys().contains(key);
    }

    public record SharedQuestProfile(Set<String> intKeys, Set<String> flagKeys, Set<String> flagPrefixes) {
        public boolean matchesFlag(String key) {
            if (key == null || key.isEmpty()) {
                return false;
            }
            if (flagKeys.contains(key)) {
                return true;
            }
            for (String prefix : flagPrefixes) {
                if (key.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }
    }
}
