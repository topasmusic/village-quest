package de.quest.quest;

import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Explicit reviewed instruction extensions. Narrative keys and formatting arguments stay intact. */
public final class QuestRuleText {
    private QuestRuleText() {}
    private static final Set<String> RULES = Set.of(
            "quest.village-quest.daily.wheat.offer.2",
            "quest.village-quest.daily.river.offer.2",
            "quest.village-quest.daily.smelt.offer.2",
            "quest.village-quest.repeatable.peaceful.offer",
            "quest.village-quest.weekly.harvest.offer.2",
            "quest.village-quest.weekly.smith.offer.2",
            "quest.village-quest.story.restless_pens.chapter_4.offer.2",
            "quest.village-quest.story.night_bells.peaceful.description.0",
            "quest.village-quest.story.shadows_on_the_trade_road.chapter_3.offer.2",
            "quest.village-quest.story.shadows_on_the_trade_road.chapter_4.offer.2",
            "quest.village-quest.story.shadows_on_the_trade_road.chapter_6.offer.2",
            "quest.village-quest.story.the_empty_caravan.chapter_5.offer.1",
            "quest.village-quest.story.the_empty_caravan.chapter_5.offer.2",
            "quest.village-quest.story.shrines_between_roads.chapter_1.offer.1",
            "quest.village-quest.story.shrines_between_roads.chapter_2.offer.2",
            "quest.village-quest.story.shrines_between_roads.chapter_4.offer.1",
            "quest.village-quest.story.shrines_between_roads.chapter_5.offer.1",
            "quest.village-quest.story.artifact_journey.chapter_1.instructions",
            "quest.village-quest.story.artifact_journey.chapter_2.instructions",
            "quest.village-quest.story.artifact_journey.chapter_3.instructions",
            "quest.village-quest.friends.lutte.2.objective",
            "quest.village-quest.friends.marius.1.objective");
    private static final Set<String> GOALS = Set.of(
            "quest.village-quest.story.living_kingdoms.chapter_1.offer.2");
    public static MutableComponent translate(String key, Object... args) {
        return extend(Component.translatable(key,args));
    }
    public static MutableComponent extend(Component original) {
        var result=original.copy();
        if(original.getContents() instanceof TranslatableContents text) {
            if(GOALS.contains(text.getKey())) append(result,text.getKey()+".objective","task_header");
            if(RULES.contains(text.getKey())) append(result,text.getKey()+".rules","rules_header");
        }
        return result;
    }
    private static void append(MutableComponent result,String key,String heading) {
        if(result.getSiblings().stream().anyMatch(c -> c.getContents() instanceof TranslatableContents text && text.getKey().equals(key))) return;
        result.append(Component.literal("\n\n")).append(Component.translatable("quest.village-quest.instructions."+heading))
                .append(Component.literal("\n")).append(Component.translatable(key));
    }
    public static boolean heading(Component component) {
        return component.getContents() instanceof TranslatableContents text && Set.of(
                "quest.village-quest.instructions.task_header","quest.village-quest.instructions.rules_header",
                "quest.village-quest.instructions.delivery_header").contains(text.getKey());
    }
    /** One consumed-bundle heading; keep each exact quantity/item as a separate native component. */
    public static void appendDelivery(MutableComponent text,Component row) {
        if(text.getSiblings().stream().noneMatch(QuestRuleText::deliveryHeading))
            text.append(Component.literal("\n\n")).append(Component.translatable("quest.village-quest.instructions.delivery_header"));
        text.append(Component.literal("\n")).append(row);
    }
    private static boolean deliveryHeading(Component component) {
        return component.getContents() instanceof TranslatableContents text && text.getKey().equals("quest.village-quest.instructions.delivery_header");
    }
    public static List<Component> extend(List<Component> lines) { return lines.stream().map(c -> (Component)extend(c)).toList(); }
}
