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
    static Component village(VillageKey key) { return text("desk.village", key.anchorX(), key.anchorZ()); }
    static Component yes(boolean allowed) { return text(allowed ? "available" : "blocked"); }
    static String signed(int value) { return value > 0 ? "+" + value : Integer.toString(value); }
    static List<JournalCard> trustCards(View view) {
        if (view == null) return List.of(new JournalCard("social_loading", text("title"), text("loading"), List.of(), TEAL, -1));
        List<JournalCard> cards = new ArrayList<>(); List<Component> details = new ArrayList<>();
        details.add(text("separate"));
        if (!view.enabled()) details.add(text("disabled"));
        if (!view.writable()) details.add(text("readonly"));
        details.add(text("trade_limit", view.buyLimit()));
        details.add(text("dispatch", text(view.dispatchAllowed()?"desk.logistics.standing_clear":"desk.logistics.standing_blocked")));
        details.add(text("convoy", text(view.convoyAllowed()?"desk.logistics.standing_clear":"desk.logistics.standing_blocked")));
        if (view.rewardPercent() > 0) details.add(text("reward_bonus", view.rewardPercent()));
        if (view.enabled() && view.writable()) {
        if (view.probation() > 0) details.add(text("probation", view.probation()));
        if (SocialReputationRules.rankFor(view.guildTrust()) != SocialReputationRules.SocialRank.RESPECTED) {
            int next = progressTarget(view.guildTrust());
            details.add(text("next_rank", next));
        }
        details.add(progressHint(view.guildTrust()));
        details.add(text("earn")); details.add(text("quest_sources")); details.add(text("mandate_bond")); details.add(text("daily_cap", 8, 8));
        }
        cards.add(new JournalCard("social_guild", text("guild"), !view.enabled() ? text("disabled") : !view.writable() ? text("readonly") : standing(view.guildTrust()), details, view.guildTrust() < -19 ? RED : TEAL, -1));
        if (view.reparation() != null) cards.add(caseCard(view.reparation()));
        for (var village : view.villages()) cards.add(new JournalCard("social_village_" + village.key(), village(village.key()),
                relationshipSummary(village.bond(),village.trust(),village.hints(),village.affected()),
                relationshipDetails(village.bond(),village.trust(),village.affected(),village.hints()),
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
    static Component relationshipText(String key, Object... args) { return de.quest.reputation.VillageRelationship.text(key,args); }
    static Component localStanding(int trust, de.quest.reputation.VillageRelationship.Hints hints) {
        return !hints.enabled() ? relationshipText("disabled") : !hints.available() ? relationshipText("unavailable")
                : text("local_standing",rank(trust),trust);
    }
    static Component relationshipSummary(Component bond, int trust, de.quest.reputation.VillageRelationship.Hints hints, boolean affected) {
        return relationshipText("summary",relationshipText("bond",bond),localStanding(trust,hints),
                hints.conflict()==2 || hints.enabled() && hints.available() && affected ? relationshipText("conflict_short") : Component.empty());
    }
    static List<Component> relationshipDetails(Component bond, int trust, boolean affected, de.quest.reputation.VillageRelationship.Hints hints) {
        List<Component> lines=new ArrayList<>();
        lines.add(relationshipText("title"));
        lines.add(relationshipText("bond",bond)); lines.add(relationshipText("earned"));
        lines.add(localStanding(trust,hints));
        if(hints.enabled() && hints.available()) {
            lines.add(text(affected ? "local_case" : "local_open"));
            if(affected || trust <= -20) lines.add(text("case_locations"));
            else { lines.add(progressHint(trust)); lines.add(relationshipText("improve")); }
        }
        if(hints.conflict()==2) { lines.add(relationshipText("conflict")); lines.add(hints.repair()); }
        else if(hints.conflict()==0) lines.add(relationshipText("conflict_unavailable"));
        lines.add(hints.nextBond()); lines.add(text("mandate_bond"));
        return List.copyOf(lines);
    }
    static int progressTarget(int trust) {
        var current=SocialReputationRules.rankFor(trust);
        for(int next=SocialReputationRules.clamp(trust)+1;next<=100;next++)
            if(SocialReputationRules.rankFor(next)!=current)return next;
        return 100;
    }
    static Component standing(int trust) { return text("standing",rank(trust),trust,progressTarget(trust)); }
    static Component nextRank(int trust) {
        return SocialReputationRules.rankFor(trust)==SocialReputationRules.SocialRank.RESPECTED
                ? text("desk.maximum_rank") : text("desk.next_rank",rank(progressTarget(trust)),progressTarget(trust));
    }
    static Component progressHint(int trust) {
        return SocialReputationRules.rankFor(trust)==SocialReputationRules.SocialRank.RESPECTED
                ? text("desk.maximum_hint",100) : text("desk.remaining",progressTarget(trust)-trust);
    }
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
