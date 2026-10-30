package de.quest.quest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Numeric contracts from the quest producers, independent of locale, wrapping and font. */
public final class QuestObjectiveProgress {
    private QuestObjectiveProgress() {}

    public record Part(Component label, int current, int target) {
        public boolean complete() { return target > 0 && current >= target; }
        public Component line() {
            return Component.translatable("screen.village-quest.ui_review.objective_row", label, current, target);
        }
    }

    public static List<Part> parts(Component source) {
        if (!(source.getContents() instanceof TranslatableContents t)) return List.of();
        String[] labels = labels(t.getKey());
        var args = t.getArgs();
        if (labels == null || args.length != labels.length * 2) return List.of();
        var result = new ArrayList<Part>();
        for (int i = 0; i < labels.length; i++) {
            if (!(args[2*i] instanceof Integer current) || !(args[2*i+1] instanceof Integer target)
                    || current < 0 || target <= 0) return List.of();
            result.add(new Part(Component.translatable(labels[i]), current, target));
        }
        return List.copyOf(result);
    }

    /** Split before wrapping, retaining authored siblings instead of discarding text. */
    public static List<Component> separate(Component source) {
        var grouped=QuestShortObjective.rows(source);
        if(grouped.size()>1) return grouped.stream().flatMap(c->separate(c).stream()).toList();
        var parts = parts(source);
        var result = new ArrayList<Component>();
        if (parts.isEmpty()) result.add(source.plainCopy().withStyle(source.getStyle()));
        else for (var part : parts) result.add(part.line().copy().withStyle(source.getStyle()));
        for (var sibling : source.getSiblings()) result.addAll(separate(sibling));
        return parts.isEmpty() && source.getSiblings().isEmpty() ? List.of(source) : List.copyOf(result);
    }

    public static boolean complete(Component source) {
        if (source == null || !source.getSiblings().isEmpty()) return false;
        var parts = parts(source);
        if (!parts.isEmpty()) return parts.stream().allMatch(Part::complete);
        if (!(source.getContents() instanceof TranslatableContents t)) return false;
        if(t.getKey().equals("quest.village-quest.story.shadows_on_the_trade_road.rescue.progress.wave")
                || t.getKey().equals("quest.village-quest.story.shadows_on_the_trade_road.rescue.progress.survivors")) return false;
        Object[] a = t.getArgs();
        if ((t.getKey().equals("screen.village-quest.ui_review.objective_row")
                || t.getKey().equals("quest.village-quest.tracker.friend_material")) && a.length == 3)
            return completedPair(a[1], a[2]);
        // Only producer-owned progress keys; arbitrary prose containing fractions is not a status.
        return t.getKey().startsWith("quest.village-quest.")
                && (t.getKey().contains(".progress") || t.getKey().contains(".stage.")
                    || java.util.Set.of("quest.village-quest.story.shrines_between_roads.chapter_3.people",
                        "quest.village-quest.story.shrines_between_roads.chapter_5.trust",
                        "quest.village-quest.story.shrines_between_roads.chapter_5.shrines").contains(t.getKey()))
                && a.length == 2 && completedPair(a[0], a[1]);
    }

    private static boolean completedPair(Object current, Object target) {
        return current instanceof Integer n && target instanceof Integer required && required > 0 && n >= required;
    }

    private static String[] labels(String key) {
        return switch (key) {
            case "quest.village-quest.pilgrim.contract.expedition.progress" -> new String[]{
                "quest.village-quest.tracker.expedition.entered","quest.village-quest.tracker.expedition.distance",
                "quest.village-quest.tracker.checkpoints","quest.village-quest.tracker.supplies","quest.village-quest.tracker.expedition.returned"};
            case "quest.village-quest.daily.wheat.progress" -> pair("item.minecraft.wheat", "item.minecraft.bread");
            case "quest.village-quest.daily.wood.progress" -> pair("screen.village-quest.ui_review.logs", "screen.village-quest.ui_review.mined_coal");
            case "quest.village-quest.daily.wool.progress" -> pair("entity.minecraft.sheep", "screen.village-quest.ui_review.wool");
            case "quest.village-quest.daily.autumn.progress" -> pair("block.minecraft.pumpkin", "block.minecraft.melon");
            case "quest.village-quest.daily.coal.progress" -> pair("item.minecraft.raw_iron", "item.minecraft.coal");
            case "quest.village-quest.daily.honey.progress" -> pair("quest.village-quest.tracker.harvest_honey", "quest.village-quest.tracker.harvest_comb");
            case "quest.village-quest.daily.market_rounds.progress" -> pair("quest.village-quest.tracker.visit_merchants", "quest.village-quest.tracker.trades");
            case "quest.village-quest.daily.potato.progress" -> pair("item.minecraft.potato", "item.minecraft.carrot");
            case "quest.village-quest.daily.river.progress" -> pair("quest.village-quest.tracker.catch_fish", "quest.village-quest.tracker.cook_fish");
            case "quest.village-quest.daily.smelt.progress" -> pair("item.minecraft.raw_iron", "item.minecraft.iron_ingot");
            case "quest.village-quest.daily.trade.progress", "quest.village-quest.weekly.market.progress.1",
                 "quest.village-quest.story.failing_harvest.chapter_4.progress" -> pair("quest.village-quest.tracker.trades", "item.minecraft.emerald");
            case "quest.village-quest.story.failing_harvest.chapter_1.progress" -> pair("item.minecraft.wheat", "item.minecraft.potato");
            case "quest.village-quest.story.failing_harvest.chapter_2.progress" -> pair("item.minecraft.honey_bottle", "item.minecraft.honeycomb");
            case "quest.village-quest.story.failing_harvest.chapter_3.progress" -> pair("item.minecraft.bread", "item.minecraft.baked_potato");
            case "quest.village-quest.story.silent_forge.chapter_2.progress.2" -> pair("block.minecraft.blast_furnace", "block.minecraft.cauldron");
            case "quest.village-quest.story.silent_forge.chapter_3.progress.1" -> pair("item.minecraft.iron_pickaxe", "item.minecraft.bucket");
            case "quest.village-quest.story.silent_forge.chapter_3.progress.2" -> pair("item.minecraft.shears", "item.minecraft.shield");
            case "quest.village-quest.story.market_road_troubles.chapter_2.progress.1" -> pair("item.minecraft.paper", "item.minecraft.book");
            case "quest.village-quest.story.night_bells.chapter_2.progress" -> pair("entity.minecraft.skeleton", "entity.minecraft.spider");
            case "quest.village-quest.story.night_bells.chapter_3.progress" -> pair("entity.minecraft.creeper", "quest.village-quest.tracker.hostiles");
            case "quest.village-quest.story.shadows_on_the_trade_road.rescue.progress.peaceful",
                 "quest.village-quest.story.the_empty_caravan.chapter_5.progress.peaceful" -> pair("quest.village-quest.tracker.checkpoints", "quest.village-quest.tracker.supplies");
            case "quest.village-quest.weekly.harvest.stage.1a", "quest.village-quest.weekly.harvest.progress.1" -> pair("item.minecraft.wheat", "item.minecraft.carrot");
            case "quest.village-quest.weekly.harvest.progress.2" -> pair("item.minecraft.potato", "item.minecraft.bread");
            case "quest.village-quest.weekly.smith.stage.1", "quest.village-quest.weekly.smith.progress.1" -> pair("item.minecraft.raw_iron", "item.minecraft.raw_gold");
            case "quest.village-quest.weekly.smith.stage.2", "quest.village-quest.weekly.smith.progress.2" -> pair("item.minecraft.iron_ingot", "item.minecraft.gold_ingot");
            case "quest.village-quest.weekly.bakehouse.progress.1" -> pair("item.minecraft.bread", "item.minecraft.pumpkin_pie");
            case "quest.village-quest.weekly.pasture.progress.1" -> pair("quest.village-quest.tracker.breed", "quest.village-quest.tracker.shear");
            case "quest.village-quest.pilgrim.contract.ash_on_the_pass.progress.1" -> pair("entity.minecraft.blaze", "entity.minecraft.wither_skeleton");
            case "quest.village-quest.pilgrim.contract.smoke_over_blackstone.progress.1" -> pair("entity.minecraft.magma_cube", "entity.minecraft.ghast");
            case "quest.village-quest.pilgrim.contract.stillness_beyond_the_gate.progress.1" -> pair("entity.minecraft.enderman", "entity.minecraft.shulker");
            case "quest.village-quest.pilgrim.contract.roadmarks_for_the_compass.progress.2" -> pair("text.village-quest.special.surveyor_compass.mode.village", "text.village-quest.special.surveyor_compass.mode.pillager_outpost");
            case "quest.village-quest.pilgrim.contract.roadmarks_for_the_compass.progress.3" -> pair("text.village-quest.special.surveyor_compass.mode.woodland_mansion", "text.village-quest.special.surveyor_compass.mode.swamp_hut");
            case "quest.village-quest.special.surveyor_compass_biomes.progress.1" -> pair("biome.minecraft.desert", "biome.minecraft.jungle");
            case "quest.village-quest.special.surveyor_compass_biomes.progress.2" -> pair("biome.minecraft.frozen_peaks", "biome.minecraft.mushroom_fields");
            default -> null;
        };
    }

    private static String[] pair(String first, String second) { return new String[]{first, second}; }
}
