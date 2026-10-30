package de.quest.quest;

import de.quest.quest.daily.*;
import de.quest.quest.weekly.*;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.*;

/** Material alternatives for the six repeatable patrols. Uses the existing personal/party hand-in paths. */
public final class PeacefulRepeatableSupplies {
    private PeacefulRepeatableSupplies() {}
    public static boolean daily(ServerLevel world, DailyQuestService.DailyQuestType type) {
        return world != null && world.getDifficulty() == Difficulty.PEACEFUL && !dailyItems(type).isEmpty();
    }
    public static boolean weekly(ServerLevel world, WeeklyQuestService.WeeklyQuestType type) {
        return world != null && world.getDifficulty() == Difficulty.PEACEFUL && !weeklyItems(type).isEmpty();
    }
    private static Map<Item,Integer> dailyItems(DailyQuestService.DailyQuestType type) {
        if (type == null) return Map.of();
        return switch (type) {
            case ZOMBIE_CULL -> Map.of(Items.COBBLESTONE,16);
            case SKELETON_PATROL -> Map.of(Items.TORCH,16);
            case SPIDER_SWEEP -> Map.of(Items.OAK_FENCE,8);
            case CREEPER_WATCH -> Map.of(Items.COAL,12);
            default -> Map.of();
        };
    }
    private static Map<Item,Integer> weeklyItems(WeeklyQuestService.WeeklyQuestType type) {
        if (type == null) return Map.of();
        var items = new LinkedHashMap<Item,Integer>();
        if (type == WeeklyQuestService.WeeklyQuestType.NIGHT_WATCH) {
            items.put(Items.COBBLESTONE,32); items.put(Items.TORCH,16);
        } else if (type == WeeklyQuestService.WeeklyQuestType.ROAD_WARDEN) {
            items.put(Items.BREAD,32); items.put(Items.OAK_FENCE,16);
        }
        return items;
    }
    private static Component row(Item item, int current, int amount) {
        return Component.translatable("screen.village-quest.ui_review.objective_row",
                Component.translatable(item.getDescriptionId()), current, amount);
    }
    public static Component dailyProgress(ServerLevel world, UUID owner, DailyQuestService.DailyQuestType type) {
        var item = dailyItems(type).entrySet().iterator().next();
        return row(item.getKey(), DailyQuestService.countCompletionItem(world,
                world.getServer().getPlayerList().getPlayer(owner), item.getKey()), item.getValue());
    }
    public static List<Component> weeklyProgress(ServerLevel world, UUID owner, WeeklyQuestService.WeeklyQuestType type) {
        var player = world.getServer().getPlayerList().getPlayer(owner);
        return weeklyItems(type).entrySet().stream().map(e -> row(e.getKey(),
                WeeklyQuestService.countCompletionItem(world,player,e.getKey()),e.getValue())).toList();
    }
    public static boolean dailyComplete(ServerLevel world, ServerPlayer player, DailyQuestService.DailyQuestType type) {
        return player != null && !dailyItems(type).isEmpty() && dailyItems(type).entrySet().stream()
                .allMatch(e -> DailyQuestService.countCompletionItem(world,player,e.getKey()) >= e.getValue());
    }
    public static boolean weeklyComplete(ServerLevel world, ServerPlayer player, WeeklyQuestService.WeeklyQuestType type) {
        return player != null && !weeklyItems(type).isEmpty() && weeklyItems(type).entrySet().stream()
                .allMatch(e -> WeeklyQuestService.countCompletionItem(world,player,e.getKey()) >= e.getValue());
    }
    public static boolean consumeDaily(ServerLevel world, ServerPlayer player, DailyQuestService.DailyQuestType type) {
        return DailyQuestService.consumeCompletionItemRequirements(world,player,dailyItems(type));
    }
    public static boolean consumeWeekly(ServerLevel world, ServerPlayer player, WeeklyQuestService.WeeklyQuestType type) {
        return WeeklyQuestService.consumeCompletionItemRequirements(world,player,weeklyItems(type));
    }
    public static List<Component> dailyDescription(DailyQuestService.DailyQuestType type) { return description(dailyItems(type)); }
    public static List<Component> weeklyDescription(WeeklyQuestService.WeeklyQuestType type) { return description(weeklyItems(type)); }
    private static List<Component> description(Map<Item,Integer> items) {
        var result = new ArrayList<Component>();
        result.add(de.quest.quest.QuestRuleText.translate("quest.village-quest.repeatable.peaceful.offer"));
        for (var item : items.entrySet()) result.add(Component.translatable("quest.village-quest.repeatable.peaceful.supply",
                item.getValue(),Component.translatable(item.getKey().getDescriptionId())));
        return List.copyOf(result);
    }
    public static Component blocked() { return Component.translatable("quest.village-quest.repeatable.peaceful.missing"); }
    private static Component completion(int line) { return Component.translatable("quest.village-quest.repeatable.peaceful.complete."+line); }
    public static DailyQuestCompletion completion(DailyQuestCompletion base) {
        return new DailyQuestCompletion(base.title(),completion(1),completion(2),completion(3),base.currencyReward(),
                base.rewardB(),base.rewardC(),base.levels(),base.reputationTrack(),base.reputationAmount());
    }
    public static WeeklyQuestCompletion completion(WeeklyQuestCompletion base) {
        return new WeeklyQuestCompletion(base.title(),completion(1),completion(2),completion(3),base.currencyReward(),
                base.rewardB(),base.rewardC(),base.levels(),base.reputationTrack(),base.reputationAmount());
    }
}
