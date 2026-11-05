package de.quest.client.ui;

import de.quest.VillageQuest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;

/** Client-only artwork selection from stable identities, never from translated prose. */
public final class QuestArtwork {
    public enum Motif {
        VILLAGE_HOME("village_home", true), FIELDS("fields_harvest", true),
        BAKEHOUSE("bakehouse", true), WORKSHOP("carpenter", true),
        MINE("mine_ore", true), FORGE("forge", true), PASTURE("pasture_shepherd", true),
        COMPANION("animal_companion", true), APIARY("apiary", true), FISHING("river_fish", true),
        MARKET("market_trade", true), CARAVAN("caravan_road", true), ARCHIVE("archive_knowledge", true),
        PROJECTS("village_projects", true), COUNCIL("diplomacy_council", true),
        WAYSHRINE("wayshrine_exploration", true), WATCH("night_watch", true),
        RELIC("relic_discovery", true), NETHER("nether_pass", true), END("end_gate", true),
        // An operating furnace or abundant field would misrepresent these stories.
        SILENT_FORGE("silent_forge", true), FAILING_HARVEST("failing_harvest", true);

        private final String key;
        private final boolean illustrated;

        Motif(String key) { this(key, false); }
        Motif(String key, boolean illustrated) {
            this.key = key;
            this.illustrated = illustrated;
        }

        public String key() { return key; }
        /** False means artwork is still outstanding, not permission to show another scene. */
        public boolean hasIllustration() { return illustrated; }
        public Identifier smallTexture() { return texture("64x24"); }
        public Identifier largeTexture() { return texture("192x72"); }
        private Identifier texture(String size) {
            return Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID,
                    "textures/gui/artwork/" + key + "_" + size + ".png");
        }
    }

    /** Authored image content, excluding only verified baked-in pale matting. */
    public record ContentArea(int textureWidth,int textureHeight,int x,int y,int width,int height) {}
    public static ContentArea contentArea(Identifier image) {
        String path=image.getPath();
        if(path.endsWith("/friends_herbs.png") || path.endsWith("/friends_coastal_signals.png") || path.endsWith("/friends_golem_repair.png") || path.endsWith("/raphael_harp.png"))return new ContentArea(768,288,0,0,768,288);
        boolean small=path.endsWith("_64x24.png");
        int width=small?64:192,height=small?24:72;
        String name=path.substring(path.lastIndexOf('/')+1);
        int top=small ? switch(name) {
            case "archive_knowledge_64x24.png", "mine_ore_64x24.png" -> 2;
            case "market_trade_64x24.png" -> 1;
            case "river_fish_64x24.png", "forge_64x24.png" -> 3;
            default -> 0;
        } : switch(name) {
            case "archive_knowledge_192x72.png" -> 6;
            case "pasture_shepherd_192x72.png", "river_fish_192x72.png" -> 5;
            case "animal_companion_192x72.png", "failing_harvest_192x72.png", "fields_harvest_192x72.png", "market_trade_192x72.png" -> 4;
            case "mine_ore_192x72.png" -> 2;
            default -> 0;
        };
        return new ContentArea(width,height,0,top,width,height-top);
    }

    private static final Map<String, Motif> TITLES;
    private static final Map<String, Motif> IDS;

    static {
        Map<String, Motif> titles = new HashMap<>();
        Map<String, Motif> ids = new HashMap<>();
        bind(titles, ids, "daily", Motif.APIARY, "honey");
        bind(titles, ids, "daily", Motif.COMPANION, "pet_collar");
        bind(titles, ids, "daily", Motif.BAKEHOUSE, "wheat", "potato");
        bind(titles, ids, "daily", Motif.WORKSHOP, "wood");
        bind(titles, ids, "daily", Motif.MINE, "coal");
        bind(titles, ids, "daily", Motif.PASTURE, "wool", "stall");
        bind(titles, ids, "daily", Motif.FISHING, "river");
        bind(titles, ids, "daily", Motif.FIELDS, "autumn");
        bind(titles, ids, "daily", Motif.FORGE, "smelt");
        bind(titles, ids, "daily", Motif.MARKET, "trade", "market_rounds");
        bind(titles, ids, "daily", Motif.WATCH,
                "zombie_cull", "skeleton_patrol", "spider_sweep", "creeper_watch");
        bind(titles, ids, "weekly", Motif.FIELDS, "harvest");
        bind(titles, ids, "weekly", Motif.BAKEHOUSE, "bakehouse");
        bind(titles, ids, "weekly", Motif.FORGE, "smith");
        bind(titles, ids, "weekly", Motif.PASTURE, "pasture");
        bind(titles, ids, "weekly", Motif.MARKET, "market");
        bind(titles, ids, "weekly", Motif.WATCH, "nightwatch", "roadwarden");
        bind(titles, ids, "story", Motif.FAILING_HARVEST, "failing_harvest");
        bind(titles, ids, "story", Motif.SILENT_FORGE, "silent_forge");
        bind(titles, ids, "story", Motif.CARAVAN,
                "market_road_troubles", "shadows_on_the_trade_road", "the_empty_caravan");
        bind(titles, ids, "story", Motif.PASTURE, "restless_pens");
        bind(titles, ids, "story", Motif.WAYSHRINE, "shrines_between_roads");
        bind(titles, ids, "story", Motif.WATCH, "night_bells");
        bind(titles, ids, "story", Motif.PROJECTS, "living_kingdoms");
        bind(titles, ids, "story", Motif.RELIC, "artifact_journey");
        bind(titles, ids, "special", Motif.RELIC, "shards");
        bind(titles, ids, "special", Motif.MARKET, "merchant");
        bind(titles, ids, "special", Motif.PASTURE, "flute");
        bind(titles, ids, "special", Motif.APIARY, "apiarist_smoker");
        bind(titles, ids, "special", Motif.WAYSHRINE,
                "surveyor_compass", "surveyor_compass_biomes");
        bind(titles, ids, "pilgrim.contract", Motif.WAYSHRINE, "roadmarks_for_the_compass");
        bind(titles, ids, "pilgrim.contract", Motif.WATCH,
                "quench_for_the_hall", "wool_before_rain", "tracks_in_the_dark", "fangs_by_the_hedgerow");
        bind(titles, ids, "pilgrim.contract", Motif.NETHER, "ash_on_the_pass", "smoke_over_blackstone");
        bind(titles, ids, "pilgrim.contract", Motif.END, "stillness_beyond_the_gate");
        String[] dailyNames = {"HONEY", "PET_COLLAR", "WHEAT_HARVEST", "POTATO_HARVEST",
                "WOODCUTTING", "COAL_MINING", "WOOL_WEAVING", "RIVER_MEAL", "AUTUMN_HARVEST",
                "SMITH_SMELTING", "STALL_NEW_LIFE", "VILLAGE_TRADING", "MARKET_ROUNDS",
                "ZOMBIE_CULL", "SKELETON_PATROL", "SPIDER_SWEEP", "CREEPER_WATCH"};
        String[] dailyTitles = {"honey", "pet_collar", "wheat", "potato", "wood", "coal", "wool",
                "river", "autumn", "smelt", "stall", "trade", "market_rounds", "zombie_cull",
                "skeleton_patrol", "spider_sweep", "creeper_watch"};
        for (int i = 0; i < dailyNames.length; i++) {
            ids.put("daily_first_" + dailyNames[i], titles.get("quest.village-quest.daily." + dailyTitles[i] + ".title"));
            ids.put("daily:" + dailyNames[i], titles.get("quest.village-quest.daily." + dailyTitles[i] + ".title"));
        }
        ids.put("weekly:HARVEST_FOR_VILLAGE", Motif.FIELDS);
        ids.put("weekly:BAKEHOUSE_STOCK", Motif.BAKEHOUSE);
        ids.put("weekly:SMITH_WEEK", Motif.FORGE);
        ids.put("weekly:STALL_AND_PASTURE", Motif.PASTURE);
        ids.put("weekly:MARKET_WEEK", Motif.MARKET);
        ids.put("weekly:NIGHT_WATCH", Motif.WATCH);
        ids.put("weekly:ROAD_WARDEN", Motif.WATCH);
        // Frozen reward tickets use colon identities; UI entries use underscores.
        for (var entry : Map.copyOf(ids).entrySet()) {
            if (entry.getKey().startsWith("story_")) ids.put("story:" + entry.getKey().substring(6), entry.getValue());
            if (entry.getKey().startsWith("pilgrim_")) ids.put("pilgrim:" + entry.getKey().substring(8), entry.getValue());
        }
        ids.put("intro", Motif.VILLAGE_HOME);
        ids.put("concord", Motif.COUNCIL);
        titles.put("screen.village-quest.notice_journey.welcome", Motif.VILLAGE_HOME);
        village(titles, Motif.FIELDS, "granary_seed", "granary_wheat", "granary_potatoes");
        village(titles, Motif.BAKEHOUSE, "granary_bread");
        village(titles, Motif.FORGE, "forge_coal", "forge_iron", "forge_copper", "forge_stone_bricks");
        village(titles, Motif.PASTURE, "pasture_fodder", "pasture_wool", "pasture_leather", "pasture_eggs");
        village(titles, Motif.APIARY, "apiary_flowers", "apiary_honey", "apiary_honeycomb", "apiary_candles");
        village(titles, Motif.ARCHIVE, "archive_paper", "archive_books", "archive_ink", "archive_feathers");
        titles.put("quest.village-quest.guild_town.story.shared_table.title", Motif.BAKEHOUSE);
        titles.put("quest.village-quest.guild_town.story.sparks_for_the_road.title", Motif.FORGE);
        titles.put("quest.village-quest.guild_town.story.long_drive.title", Motif.PASTURE);
        titles.put("quest.village-quest.guild_town.story.lanterns_in_bloom.title", Motif.APIARY);
        titles.put("quest.village-quest.guild_town.story.ink_between_villages.title", Motif.ARCHIVE);
        String[] villageTypes = {"granary", "forge", "pasture", "apiary", "archive"};
        Motif[] villageMotifs = {Motif.BAKEHOUSE, Motif.FORGE, Motif.PASTURE, Motif.APIARY, Motif.ARCHIVE};
        for (int i = 0; i < villageTypes.length; i++)
            titles.put("quest.village-quest.guild_town.afterstory." + villageTypes[i] + ".title", villageMotifs[i]);
        ids.put("special_smoker", Motif.APIARY);
        ids.put("special_shard_relic", Motif.RELIC);
        ids.put("special_merchant_seal", Motif.MARKET);
        ids.put("special_shepherd_flute", Motif.PASTURE);
        TITLES = Map.copyOf(titles);
        IDS = Map.copyOf(ids);
    }

    private QuestArtwork() {}

    private static void village(Map<String, Motif> titles, Motif motif, String... names) {
        for (String name : names) titles.put("text.village-quest.village_request." + name, motif);
    }

    private static void bind(Map<String, Motif> titles, Map<String, Motif> ids,
                             String category, Motif motif, String... names) {
        for (String name : names) {
            titles.put("quest.village-quest." + category + "." + name + ".title", motif);
            ids.put(category + "_" + name, motif);
            if (category.equals("pilgrim.contract")) {
                ids.put(name, motif);
                ids.put("pilgrim_" + name, motif);
            }
        }
    }

    public static Optional<Motif> forQuest(String id, Component title) {
        Motif known = id == null ? null : IDS.get(id);
        return known != null ? Optional.of(known) : forComponent(title);
    }

    public static Optional<Motif> forComponent(Component component) {
        return Optional.ofNullable(find(component, 0));
    }

    private static Motif find(Component component, int depth) {
        if (component == null || depth > 12) return null;
        if (component.getContents() instanceof TranslatableContents translated) {
            String key = translated.getKey();
            Motif direct = TITLES.get(key);
            if (direct != null) return direct;
            // Chapter titles share the story's neutral/appropriate state, never a future spoiler.
            if (key.startsWith("quest.village-quest.story.") && key.matches(".*\\.chapter_[0-9]+\\.title")) {
                int chapter = key.indexOf(".chapter_");
                Motif story = TITLES.get(key.substring(0, chapter) + ".title");
                if (story != null) return story;
            }
            for (Object argument : translated.getArgs()) {
                if (argument instanceof Component nested) {
                    Motif result = find(nested, depth + 1);
                    if (result != null) return result;
                }
            }
        }
        for (Component sibling : component.getSiblings()) {
            Motif result = find(sibling, depth + 1);
            if (result != null) return result;
        }
        return null;
    }
}
