package de.quest.commands;

import java.util.List;

/** Ordered, bounded tour catalog shared by tests and the client. */
public final class UiTestTourSteps {
    private UiTestTourSteps() {}
    public record Step(String family, String state) {}
    public static final List<Step> ALL = List.of(
            new Step("market", "goods"), new Step("market", "limited"), new Step("market", "details"),
            new Step("journal", "overview"), new Step("journal", "network"), new Step("journal", "quests"), new Step("journal", "quests_reset"),
            new Step("journal", "atlas"), new Step("journal", "atlas_trust"), new Step("journal", "atlas_charters"), new Step("journal", "guide"),
            new Step("questmaster", "available"), new Step("questmaster", "active"), new Step("questmaster", "locked"), new Step("questmaster", "daily_reset"), new Step("questmaster", "weekly_reset"),
            new Step("trust", "neutral"), new Step("trust", "amends"), new Step("trust", "resettlement"),
            new Step("pilgrim", "goods"), new Step("pilgrim", "contract"),
            new Step("prosperity", "upgrades"), new Step("prosperity", "services"), new Step("prosperity", "collection"),
            new Step("noticeboard", "known"), new Step("noticeboard", "trusted"), new Step("noticeboard", "allied"),
            new Step("noticejourney", "welcome"), new Step("noticejourney", "greeting"),
            new Step("noticejourney", "available"), new Step("noticejourney", "active"),
            new Step("noticejourney", "choice"), new Step("noticejourney", "ready"),
            new Step("noticejourney", "paused"), new Step("noticejourney", "away"),


            new Step("dispatch", "available"), new Step("dispatch", "active"),
            new Step("caravan", "travel"), new Step("caravan", "cargo"), new Step("caravan", "incident"), new Step("caravan", "paused"),
            new Step("wayshrine", "owner"), new Step("wayshrine", "guest"),
            new Step("routes", "network"), new Step("guildpath", "progress"),
            new Step("building", "founding"), new Step("building", "council"), new Step("building", "inn"),
            new Step("building", "market_anchor"), new Step("building", "storehouse"), new Step("building", "places"),
            new Step("building", "travel"), new Step("building", "projects"), new Step("building", "relations"),
            new Step("building", "relations_net"), new Step("building", "diplomacy"), new Step("building", "crisis"),
            new Step("building", "naming"), new Step("building", "release"), new Step("building", "village_choice"), new Step("building", "check_help"),
            new Step("villagecheck", "overview"),
            new Step("reward", "choice"), new Step("adminjournal", "overview"));
    public static int move(int current, int direction) {
        return Math.clamp((long) current + Integer.signum(direction), 0, ALL.size() - 1);
    }
}
