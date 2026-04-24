package de.quest.quest.story;

import java.util.List;

public enum StoryArcType {
    FAILING_HARVEST("failing_harvest"),
    SILENT_FORGE("silent_forge"),
    MARKET_ROAD_TROUBLES("market_road_troubles"),
    RESTLESS_PENS("restless_pens"),
    SHADOWS_ON_THE_TRADE_ROAD("shadows_on_the_trade_road"),
    NIGHT_BELLS("night_bells");

    private final String id;

    StoryArcType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
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
                SHADOWS_ON_THE_TRADE_ROAD
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
