package de.quest.client.screen;

import de.quest.reputation.ReputationViewService.*;
import de.quest.reputation.SocialReputationRules;
import de.quest.village.VillageLifeState.VillageKey;
import java.util.*;
import net.minecraft.network.chat.Component;

/** Existing journal cards provide shared scrolling, expansion and typography. */
final class ReputationPanel {
    private static final int TEAL = 0xFF236B68, GOLD = 0xFF9A6620, RED = 0xFF8B3934;
    private ReputationPanel() {}
    static Component text(String key, Object... args) { return Component.translatable("screen.village-quest.reputation." + key, args); }
    static Component rank(int points) { return text("rank." + SocialReputationRules.rankFor(points).name().toLowerCase(Locale.ROOT)); }
    static Component village(VillageKey key) { return text("village", key.anchorX(), key.anchorZ(), key.dimension()); }
    static Component yes(boolean allowed) { return text(allowed ? "available" : "blocked"); }
    static String signed(int value) { return value > 0 ? "+" + value : Integer.toString(value); }
    static List<JournalCard> trustCards(View view) {
        if (view == null) return List.of(new JournalCard("social_loading", text("title"), text("loading"), List.of(), TEAL, -1));
        List<JournalCard> cards = new ArrayList<>(); List<Component> details = new ArrayList<>();
        details.add(text("separate"));
        if (!view.enabled()) details.add(text("disabled"));
        if (!view.writable()) details.add(text("readonly"));
        details.add(text("trade_limit", view.buyLimit()));
        details.add(text("dispatch", yes(view.dispatchAllowed()))); details.add(text("convoy", yes(view.convoyAllowed())));
        if (view.rewardPercent() > 0) details.add(text("reward_bonus", view.rewardPercent()));
        if (view.probation() > 0) details.add(text("probation", view.probation()));
        if (view.guildTrust() < 100) {
            int next = progressTarget(view.guildTrust());
            details.add(text("next_rank", next));
        }
        details.add(text("earn")); details.add(text("daily_cap", 8, 8));
        cards.add(new JournalCard("social_guild", text("guild"), text("standing", rank(view.guildTrust()), view.guildTrust()), details, view.guildTrust() < -19 ? RED : TEAL, -1));
        if (view.reparation() != null) cards.add(caseCard(view.reparation()));
        for (var village : view.villages()) cards.add(new JournalCard("social_village_" + village.key(), village(village.key()),
                text("standing", rank(village.trust()), village.trust()), List.of(text("local_separate"), text(village.affected() ? "local_case" : "local_open")),
                village.affected() ? RED : GOLD, -1));
        if (view.villageTotal() == 0) cards.add(new JournalCard("social_no_village", text("villages"), text("no_village"), List.of(text("no_village_body")), GOLD, -1));
        if (!view.history().isEmpty()) {
            List<Component> recent = new ArrayList<>();
            for (var entry : view.history().stream().limit(3).toList()) {
                recent.add(text("recent_change", text("event." + entry.event().toLowerCase(Locale.ROOT)), signed(entry.guildDelta())));
                if (entry.village() != null) recent.add(text("history_local", village(entry.village()), signed(entry.localDelta())));
            }
            cards.add(new JournalCard("social_recent", text("recent"), text("all_history"), recent, GOLD, -1));
        }
        return List.copyOf(cards);
    }
    static int progressTarget(int trust) { return trust < 0 ? 0 : trust < 20 ? 20 : trust < 60 ? 60 : 100; }
    static int progressFloor(int trust) { return trust < 0 ? -100 : 0; }
    static int progressPixels(int trust, int width) {
        int floor = progressFloor(trust), target = progressTarget(trust);
        return Math.clamp(trust - floor, 0, target - floor) * width / (target - floor);
    }
    static JournalCard caseCard(CaseView active) {
        List<Component> details = new ArrayList<>();
        details.add(text("cause", text("event.offence." + SocialReputationRules.Offence.values()[active.offence()].name().toLowerCase(Locale.ROOT))));
        details.add(text("case_lore"));
        details.add(text("case_time", (active.ticksRemaining() + 1199) / 1200));
        details.add(text("case_locations")); details.add(text("case_reset"));
        for (var aid : active.aid()) details.add(text("aid_line", text("material." + aid.material()), aid.supplied(), aid.required()));
        details.add(text("case_finish"));
        return new JournalCard("social_case_" + active.id(), text("reparation"), text(active.major() ? "case_major" : "case_minor"), details, RED, -1);
    }
    static List<JournalCard> historyCards(View view) {
        if (view == null || view.history().isEmpty()) return List.of(new JournalCard("social_history_empty", text("history"), text("history_empty"), List.of(text("history_limit")), GOLD, -1));
        List<JournalCard> cards = new ArrayList<>();
        for (var entry : view.history()) {
            List<Component> details = new ArrayList<>(); details.add(text("history_guild", signed(entry.guildDelta())));
            if (entry.village() != null) details.add(text("history_local", village(entry.village()), signed(entry.localDelta())));
            if (entry.affectedCount() > 1) details.add(text("history_many", entry.affectedCount()));
            cards.add(new JournalCard("social_history_" + entry.id() + "_" + entry.event(), text("event." + entry.event().toLowerCase(Locale.ROOT)),
                    text("history_guild", signed(entry.guildDelta())), details, entry.guildDelta() < 0 ? RED : TEAL, -1));
        }
        return List.copyOf(cards);
    }
}
