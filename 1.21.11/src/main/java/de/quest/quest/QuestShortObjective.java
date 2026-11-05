package de.quest.quest;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Current objectives only; descriptions remain in the journal. */
public final class QuestShortObjective {
    private QuestShortObjective() {}

    /** Independent requirements share a snapshot; phase selection stays with the producer. */
    public static Component current(List<Component> progress) {
        if(progress==null || progress.isEmpty()) return first(progress);
        var materials=new java.util.ArrayList<Component>();
        for(var line:progress) {
            var explicit=rows(line);
            if(explicit.size()>1 && explicit.stream().allMatch(c->c.getContents() instanceof TranslatableContents t
                    && t.getKey().equals("quest.village-quest.tracker.friend_material"))) return line;
            var delivery=turnInMaterials(line);
            if(!delivery.isEmpty()) return group(delivery);
            if(!(line.getContents() instanceof TranslatableContents text)) continue;
            String key=text.getKey();
            var parts=QuestObjectiveProgress.parts(line);
            if(!parts.isEmpty() && independentParts(key)) {
                for(var part:parts) materials.add(part.line());
            } else if(independentLine(key)) materials.add(line);
        }
        var next=first(progress);
        if(materials.isEmpty() || materials.stream().allMatch(QuestObjectiveProgress::complete)) return next;
        if(next.getContents() instanceof TranslatableContents text
                && !independentLine(text.getKey()) && !independentParts(text.getKey())
                && !text.getKey().equals("quest.village-quest.guidance.claim")) materials.addFirst(next);
        return group(materials);
    }

    public static List<Component> turnInMaterials(Component line) {
        if(!(line.getContents() instanceof TranslatableContents text)
                || !text.getKey().startsWith("text.village-quest.turnin_missing.")) return List.of();
        var rows=new java.util.ArrayList<Component>();
        var a=text.getArgs();
        for(int i=0;i+2<a.length;i+=3)
            if(a[i] instanceof Component item && a[i+1] instanceof Integer current && a[i+2] instanceof Integer target)
                rows.add(material(item,current,target));
        return List.copyOf(rows);
    }

    public static Component material(Component item,int current,int target) {
        return Component.translatable("quest.village-quest.tracker.friend_material",item,current,target);
    }

    /** Existing component codec preserves each row's translation, arguments and completion. */
    public static Component group(List<Component> rows) {
        if(rows.size()==1) return rows.getFirst();
        var result=Component.empty();
        for(var row:rows) { if(!result.getSiblings().isEmpty()) result.append("\n"); result.append(row); }
        return result;
    }

    public static List<Component> rows(Component objective) {
        if(objective.getContents().equals(Component.empty().getContents()) && objective.getSiblings().stream()
                .anyMatch(c->c.getString().equals("\n")))
            return objective.getSiblings().stream().filter(c->!c.getString().equals("\n")).toList();
        return List.of(objective);
    }

    private static boolean independentParts(String key) {
        return java.util.Set.of("quest.village-quest.daily.wheat.progress","quest.village-quest.daily.wood.progress",
                "quest.village-quest.daily.autumn.progress","quest.village-quest.daily.coal.progress",
                "quest.village-quest.daily.wool.progress","quest.village-quest.daily.river.progress",
                "quest.village-quest.daily.trade.progress","quest.village-quest.daily.market_rounds.progress",
                "quest.village-quest.daily.honey.progress","quest.village-quest.daily.potato.progress",
                "quest.village-quest.daily.smelt.progress","quest.village-quest.weekly.harvest.stage.1a",
                "quest.village-quest.weekly.harvest.progress.1","quest.village-quest.weekly.harvest.progress.2",
                "quest.village-quest.weekly.smith.stage.1","quest.village-quest.weekly.smith.stage.2",
                "quest.village-quest.weekly.smith.progress.1","quest.village-quest.weekly.smith.progress.2",
                "quest.village-quest.weekly.bakehouse.progress.1",
                "quest.village-quest.story.failing_harvest.chapter_1.progress",
                "quest.village-quest.story.failing_harvest.chapter_2.progress",
                "quest.village-quest.story.failing_harvest.chapter_3.progress",
                "quest.village-quest.story.failing_harvest.chapter_4.progress",
                "quest.village-quest.story.silent_forge.chapter_2.progress.2",
                "quest.village-quest.story.silent_forge.chapter_3.progress.1",
                "quest.village-quest.story.silent_forge.chapter_3.progress.2",
                "quest.village-quest.story.market_road_troubles.chapter_2.progress.1").contains(key);
    }

    private static boolean independentLine(String key) {
        return key.equals("screen.village-quest.ui_review.objective_row")
                || key.equals("quest.village-quest.tracker.friend_material")
                || key.equals("quest.village-quest.weekly.harvest.stage.1b")
                || key.equals("quest.village-quest.weekly.bakehouse.progress.2")
                || key.startsWith("quest.village-quest.story.silent_forge.chapter_1.progress.")
                || key.equals("quest.village-quest.story.silent_forge.chapter_2.progress.1")
                || key.matches("quest\\.village-quest\\.story\\.silent_forge\\.chapter_4\\.progress\\.[12]")
                || key.startsWith("quest.village-quest.story.the_empty_caravan.chapter_3.progress.")
                || key.startsWith("quest.village-quest.story.the_empty_caravan.chapter_6.progress.")
                || key.matches("quest\\.village-quest\\.story\\.restless_pens\\.chapter_[124]\\.progress\\.2")
                || key.matches("quest\\.village-quest\\.special\\.(shards|merchant|flute|apiarist_smoker|surveyor_compass)\\.progress\\..+");
    }

    public static Component first(List<Component> progress) {
        if (progress == null || progress.isEmpty()) return Component.translatable("quest.village-quest.guidance.questmaster");
        for (var line : progress) {
            if (line.getContents() instanceof TranslatableContents text) {
                String key = text.getKey();
                if (key.startsWith("text.village-quest.turnin_missing.")) {
                    var a=text.getArgs();
                    for(int i=0;i+2<a.length;i+=3)
                        if(a[i] instanceof Component item && a[i+1] instanceof Integer current
                                && a[i+2] instanceof Integer required && current<required)
                            return Component.translatable("quest.village-quest.tracker.friend_material",item,current,required);
                }
                if (key.equals("text.village-quest.story.chapter_label") || key.endsWith(".progress.books")
                        || key.endsWith(".progress.equipment") || key.endsWith("_biomes.progress.summary")
                        || key.equals("quest.village-quest.pilgrim.contract.roadmarks_for_the_compass.progress.1")) continue;
                if (progress.size()>1 && (key.equals("quest.village-quest.story.shadows_on_the_trade_road.rescue.progress.count")
                        || key.equals("quest.village-quest.story.shadows_on_the_trade_road.chapter_6.progress.1"))) continue;
                if (key.equals("quest.village-quest.story.shadows_on_the_trade_road.rescue.progress.wave")) continue;
                String action = switch (key) {
                    case "quest.village-quest.story.shadows_on_the_trade_road.chapter_2.progress" -> "calibrate_compass";
                    case "quest.village-quest.story.shadows_on_the_trade_road.chapter_5.progress.1" -> "courier";
                    case "quest.village-quest.story.shadows_on_the_trade_road.chapter_5.progress.2" -> "deliver_warning";
                    case "quest.village-quest.story.shadows_on_the_trade_road.chapter_6.progress.2" -> "convoy_nights";
                    case "quest.village-quest.story.shadows_on_the_trade_road.rescue.progress.scouting" -> "scouting";
                    case "quest.village-quest.story.shadows_on_the_trade_road.rescue.progress.wait_night" -> "wait_night";
                    case "quest.village-quest.story.shadows_on_the_trade_road.rescue.progress.compass" -> "follow_distress";
                    default -> null;
                };
                if (action != null && !QuestObjectiveProgress.complete(line))
                    return Component.translatable("quest.village-quest.tracker."+action,text.getArgs());
                if (key.equals("quest.village-quest.story.shadows_on_the_trade_road.rescue.progress.hostiles"))
                    return Component.translatable("quest.village-quest.tracker.encounter_hostiles",text.getArgs());
                var parts = QuestObjectiveProgress.parts(line);
                if (!parts.isEmpty()) {
                    for (var part : parts) if (!part.complete()) return part.line();
                    continue;
                }
                if (QuestObjectiveProgress.complete(line)) continue;
                if (key.equals("quest.village-quest.special.shards.cache_line.1"))
                    return Component.translatable("quest.village-quest.tracker.cache");
                if (key.contains(".stage.") && (key.startsWith("quest.village-quest.daily.") || key.startsWith("quest.village-quest.weekly.")
                        || key.startsWith("quest.village-quest.story.market_road_troubles.chapter_2."))) {
                    var a = text.getArgs();
                    if (a.length == 2 && a[0] instanceof Integer && a[1] instanceof Integer)
                        return Component.translatable("quest.village-quest.tracker.stage."+key.substring("quest.village-quest.".length()), a);
                    if (key.endsWith(".3")) return Component.translatable("quest.village-quest.tracker.questmaster_bundle");
                }
            }
            return line;
        }
        return Component.translatable("quest.village-quest.guidance.claim");
    }

    public static Component compactAction(Component action) {
        if (!(action.getContents() instanceof TranslatableContents t)) return action;
        String key = t.getKey();
        String suffix = key.startsWith("quest.village-quest.guidance.") ? key.substring("quest.village-quest.guidance.".length())
                : key.startsWith("quest.village-quest.story.living_kingdoms.next.") ? key.substring("quest.village-quest.story.living_kingdoms.next.".length()) : "";
        if (java.util.Set.of("claim", "pending_delivery", "prepare_and_help", "council_prepare", "council_bind", "mandate", "recover_target",
                "building", "place", "meet_leader", "meet_innkeeper", "meet_storekeeper", "market_trade", "store_delivery", "first_project", "target").contains(suffix))
            return Component.translatable("quest.village-quest.tracker.next."+suffix, t.getArgs());
        return action;
    }
}
