package de.quest.client.screen;

import de.quest.network.Payloads.JournalActionPayload;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Converts the server journal view-model into compact cards; contains no rendering state. */
final class JournalContentBuilder {
    private static final int MUTED = 0xFF8A7661;
    private static final int TEAL = 0xFF236B68;
    private static final int GOLD = 0xFF9A6620;
    private static final int GREEN = 0xFF47713F;
    private static final int BLUE = 0xFF3F667F;
    private static final int PURPLE = 0xFF725083;
    private static final int CRISIS = 0xFF9B4038;
    private static final int STRAINED = 0xFFB0712E;
    private static final int RECOVERING = 0xFF3B7D78;
    private final JournalScreenData data;

    JournalContentBuilder(JournalScreenData data) {
        this.data = data;
    }

    List<JournalCard> overviewCards() {
        int reputationTotal = data.farmingReputation + data.craftingReputation + data.animalReputation
                + data.tradeReputation + data.monsterReputation;
        return List.of(
                card("overview_progress", "screen.village-quest.journal.v2.overview.progress",
                        Component.translatable("screen.village-quest.journal.v2.overview.progress_short",
                                data.completed, data.discovered),
                        List.of(
                                Component.translatable("screen.village-quest.journal.summary.total", data.total),
                                Component.translatable("screen.village-quest.journal.summary.discovered", data.discovered),
                                Component.translatable("screen.village-quest.journal.summary.active", data.active),
                                Component.translatable("screen.village-quest.journal.summary.completed", data.completed)), TEAL),
                card("overview_village", "screen.village-quest.journal.v2.overview.village",
                        Component.translatable("screen.village-quest.journal.summary.reputation", reputationTotal),
                        List.of(Component.translatable("screen.village-quest.journal.summary.projects",
                                completedProjectCount())), GOLD),
                nextActionCard());
    }

    List<JournalCard> activeQuestCards() {
        List<JournalCard> cards = new ArrayList<>();
        addActive(cards, data.dailyActive, "daily", "screen.village-quest.journal.active.daily",
                data.dailyTitle, data.dailyProgress, "screen.village-quest.journal.active.daily_hint",
                BLUE, JournalActionPayload.ACTION_CANCEL_DAILY);
        addActive(cards, data.weeklyActive, "weekly", "screen.village-quest.journal.active.weekly",
                data.weeklyTitle, data.weeklyProgress, "screen.village-quest.journal.active.weekly_hint",
                GOLD, JournalActionPayload.ACTION_CANCEL_WEEKLY);
        addActive(cards, data.storyActive, "story", "screen.village-quest.journal.active.story",
                data.storyTitle, data.storyProgress, "screen.village-quest.journal.v2.active.story_hint",
                GREEN, -1);
        addActive(cards, data.pilgrimActive, "pilgrim", "screen.village-quest.journal.active.pilgrim",
                data.pilgrimTitle, data.pilgrimProgress, "screen.village-quest.journal.v2.active.pilgrim_hint",
                0xFF9B6B34, -1);
        addActive(cards, data.specialActive, "special", "screen.village-quest.journal.active.special",
                data.specialTitle, data.specialProgress, "screen.village-quest.journal.v2.active.special_hint",
                PURPLE, -1);
        if (cards.isEmpty()) {
            cards.add(card("active_none", "screen.village-quest.journal.active.none",
                    Component.translatable("screen.village-quest.journal.active.none_hint"),
                    List.of(Component.translatable("screen.village-quest.journal.v2.active.none_body")), MUTED));
        }
        return List.copyOf(cards);
    }

    List<JournalCard> networkCards() {
        List<JournalCard> cards = new ArrayList<>();
        Component nextAction = data.networkNextAction.getString().isBlank()
                ? Component.translatable("screen.village-quest.journal.network.empty")
                : data.networkNextAction;
        cards.add(new JournalCard("network_next",
                Component.translatable("screen.village-quest.journal.network.next.title"),
                nextAction,
                List.of(Component.translatable("screen.village-quest.journal.network.next.hint")),
                TEAL, -1));

        var summary = data.networkSummary;
        List<Component> summaryDetails = new ArrayList<>();
        summaryDetails.add(Component.translatable("screen.village-quest.journal.network.summary.renown",
                summary.renown(), summary.nextRankThreshold()));
        summaryDetails.add(Component.translatable("screen.village-quest.journal.network.summary.profile",
                summary.profile()));
        summaryDetails.add(Component.translatable("screen.village-quest.journal.network.summary.specialization",
                summary.specialization()));
        cards.add(new JournalCard("network_summary",
                Component.translatable("screen.village-quest.journal.network.summary.title"),
                Component.translatable("screen.village-quest.journal.network.summary.subtitle",
                        summary.rank(), summary.honor()),
                List.copyOf(summaryDetails), GOLD, -1));

        for (var village : data.networkVillages) {
            cards.add(new JournalCard("network_village_" + village.index(), village.villageType(),
                    Component.translatable("screen.village-quest.journal.network.village.subtitle",
                            village.bondLevel(), village.condition()),
                    List.of(
                            Component.translatable("screen.village-quest.journal.network.village.need",
                                    village.need()),
                            Component.translatable("screen.village-quest.journal.network.village.support",
                                    village.support(), 100),
                            Component.translatable("screen.village-quest.journal.network.village.energy",
                                    village.energyProgress(), 3)),
                    conditionAccent(village.conditionKey()), -1));
        }

        List<Component> guildLines = data.networkGuildLines;
        Component guildSubtitle = guildLines.isEmpty()
                ? Component.translatable("command.village-quest.guild.none") : guildLines.getFirst();
        List<Component> guildDetails = guildLines.size() <= 1
                ? List.of(Component.translatable("screen.village-quest.journal.network.guild.hint"))
                : List.copyOf(guildLines.subList(1, guildLines.size()));
        cards.add(new JournalCard("network_guild",
                Component.translatable("screen.village-quest.journal.network.guild.title"),
                guildSubtitle, guildDetails, PURPLE, -1));
        return List.copyOf(cards);
    }

    private static int conditionAccent(String conditionKey) {
        return switch (conditionKey == null ? "stable" : conditionKey) {
            case "crisis" -> CRISIS;
            case "strained" -> STRAINED;
            case "recovering" -> RECOVERING;
            case "thriving" -> GREEN;
            default -> GOLD;
        };
    }

    List<JournalCard> guideCards() {
        return List.of(guide("guide_start", "start", GREEN), guide("guide_quests", "quests", GOLD),
                guide("guide_prosperity", "prosperity", PURPLE), guide("guide_routes", "routes", TEAL),
                guide("guide_controls", "controls", BLUE));
    }

    private JournalCard nextActionCard() {
        if (data.storyActive) return nextAction(data.storyTitle, data.storyProgress,
                "screen.village-quest.journal.v2.active.story_hint", GREEN);
        if (data.specialActive) return nextAction(data.specialTitle, data.specialProgress,
                "screen.village-quest.journal.v2.active.special_hint", PURPLE);
        if (data.weeklyActive) return nextAction(data.weeklyTitle, data.weeklyProgress,
                "screen.village-quest.journal.active.weekly_hint", GOLD);
        if (data.dailyActive) return nextAction(data.dailyTitle, data.dailyProgress,
                "screen.village-quest.journal.active.daily_hint", BLUE);
        if (data.pilgrimActive) return nextAction(data.pilgrimTitle, data.pilgrimProgress,
                "screen.village-quest.journal.v2.active.pilgrim_hint", 0xFF9B6B34);
        if (!data.networkNextAction.getString().isBlank()) {
            return new JournalCard("overview_next",
                    Component.translatable("screen.village-quest.journal.v2.overview.next"),
                    data.networkNextAction,
                    List.of(Component.translatable("screen.village-quest.journal.network.next.hint")), TEAL, -1);
        }
        return card("overview_next", "screen.village-quest.journal.v2.overview.next",
                Component.translatable("screen.village-quest.journal.v2.overview.next_short"),
                List.of(Component.translatable("screen.village-quest.journal.v2.overview.next_body"),
                        Component.translatable(data.hasCaravanLedger
                                ? "screen.village-quest.journal.v2.overview.routes_ready"
                                : "screen.village-quest.journal.v2.overview.routes_locked")), BLUE);
    }

    private JournalCard nextAction(Component title, Component progress, String hintKey, int accent) {
        List<Component> details = new ArrayList<>();
        if (progress != null && !progress.getString().isBlank()) details.add(progress);
        details.add(Component.translatable(hintKey));
        Component subtitle = title == null || title.getString().isBlank()
                ? Component.translatable("screen.village-quest.journal.v2.overview.next_short") : title;
        return new JournalCard("overview_next",
                Component.translatable("screen.village-quest.journal.v2.overview.next"),
                subtitle, List.copyOf(details), accent, -1);
    }

    private void addActive(List<JournalCard> cards, boolean active, String id, String labelKey,
                           Component title, Component progress, String hintKey, int accent, int cancelAction) {
        if (!active) return;
        List<Component> details = new ArrayList<>();
        if (progress != null && !progress.getString().isBlank()) details.add(progress);
        details.add(Component.translatable(hintKey));
        cards.add(new JournalCard(id, title == null || title.getString().isBlank()
                ? Component.translatable(labelKey) : title, Component.translatable(labelKey),
                List.copyOf(details), accent, cancelAction));
    }

    private JournalCard guide(String id, String suffix, int accent) {
        return new JournalCard(id,
                Component.translatable("screen.village-quest.journal.v2.guide." + suffix + ".title"),
                Component.translatable("screen.village-quest.journal.v2.guide." + suffix + ".short"),
                List.of(Component.translatable("screen.village-quest.journal.v2.guide." + suffix + ".body")),
                accent, -1);
    }

    private JournalCard card(String id, String titleKey, Component subtitle,
                             List<Component> details, int accent) {
        return new JournalCard(id, Component.translatable(titleKey), subtitle, details, accent, -1);
    }

    private int completedProjectCount() {
        int count = 0;
        if (data.hasVillageLedgerProject) count++;
        if (data.hasApiaryCharterProject) count++;
        if (data.hasForgeCharterProject) count++;
        if (data.hasMarketCharterProject) count++;
        if (data.hasPastureCharterProject) count++;
        if (data.hasWatchBellProject) count++;
        if (data.hasCaravanYardProject) count++;
        if (data.hasWayshrineNetworkProject) count++;
        return count;
    }
}
