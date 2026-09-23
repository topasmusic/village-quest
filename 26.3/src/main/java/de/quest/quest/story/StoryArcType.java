package de.quest.quest.story;

import java.util.List;

public enum StoryArcType {
    FAILING_HARVEST("failing_harvest", 4),
    SILENT_FORGE("silent_forge", 4),
    MARKET_ROAD_TROUBLES("market_road_troubles", 4),
    RESTLESS_PENS("restless_pens", 4),
    SHADOWS_ON_THE_TRADE_ROAD("shadows_on_the_trade_road", 6),
    THE_EMPTY_CARAVAN("the_empty_caravan", 6),
    SHRINES_BETWEEN_ROADS("shrines_between_roads", 6),
    NIGHT_BELLS("night_bells", 4);

    private final String id;
    private final int chapterCount;

    StoryArcType(String id, int chapterCount) {
        this.id = id;
        this.chapterCount = chapterCount;
    }

    public String id() {
        return id;
    }

    /** Registry-free persistence bound used while loading saves. */
    public int chapterCount() {
        return chapterCount;
    }

    public static List<StoryArcType> coreQuestmasterArcs() {
        return List.of(
                FAILING_HARVEST,
                SILENT_FORGE,
                MARKET_ROAD_TROUBLES,
                RESTLESS_PENS
        );
    }

    public static List<StoryArcType> questmasterArcs() {
        return List.of(
                FAILING_HARVEST,
                SILENT_FORGE,
                MARKET_ROAD_TROUBLES,
                RESTLESS_PENS,
                NIGHT_BELLS,
                SHADOWS_ON_THE_TRADE_ROAD,
                THE_EMPTY_CARAVAN,
                SHRINES_BETWEEN_ROADS
        );
    }

    public static boolean isQuestmasterArc(StoryArcType type) {
        return type != null && questmasterArcs().contains(type);
    }

    public static StoryArcType fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (StoryArcType type : values()) {
            if (type.id.equalsIgnoreCase(raw)) {
                return type;
            }
        }
        return null;
    }
}
