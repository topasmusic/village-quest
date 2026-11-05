package de.quest.reward;

import de.quest.quest.repeatable.RepeatableTargetProfile;
import java.util.List;
import java.util.Map;

/** Registry-free, deterministic extra packages; base quest rewards live in their original services. */
public final class VanillaRewardRegistry {
    public record Goods(String item, int count, String potion, Map<String, Integer> enchantments) { }
    public record Option(String id, long currency, List<Goods> goods, boolean diamondGate) { }
    public static List<Option> options(String key, RepeatableTargetProfile profile) {
        List<Option> options = switch (key) {
            case "friends:lutte" -> List.of(new Option("precision",0,List.of(new Goods("netherite_pickaxe",1,"",Map.of("efficiency",4,"unbreaking",3))),false),
                    new Option("masterblade",0,List.of(new Goods("netherite_sword",1,"",Map.of("sharpness",4,"unbreaking",3))),false));
            case "intro" -> alternatives(2, goods("bread", 8));
            case "daily:HONEY", "daily:POTATO_HARVEST", "daily:AUTUMN_HARVEST", "daily:STALL_NEW_LIFE" -> alternatives(3, goods("golden_carrot", 8));
            case "daily:WHEAT_HARVEST" -> alternatives(5, goods("golden_carrot", 12));
            case "daily:WOODCUTTING" -> alternatives(8, goods("iron_ingot", 16), goods("coal", 16));
            case "daily:COAL_MINING", "daily:SMITH_SMELTING" -> withDiamonds(alternatives(12, goods("iron_ingot", 16), goods("coal", 32)), 2);
            case "daily:RIVER_MEAL" -> alternatives(6, goods("golden_carrot", 16));
            case "weekly:HARVEST_FOR_VILLAGE" -> List.of(pack("supplies", goods("golden_carrot", 48)), pack("work", goods("glass", 64), goods("lantern", 16)), coins(20));
            case "weekly:BAKEHOUSE_STOCK" -> List.of(pack("supplies", goods("golden_carrot", 64)), pack("work", goods("rail", 64), goods("powered_rail", 8)), coins(26));
            case "weekly:SMITH_WEEK" -> withDiamonds(alternatives(32, goods("golden_carrot", 64), goods("golden_apple", 2)), 6);
            case "weekly:STALL_AND_PASTURE" -> List.of(pack("supplies", goods("golden_carrot", 48)), pack("work", goods("leather", 48)), coins(20));
            case "pilgrim:quench_for_the_hall", "pilgrim:wool_before_rain", "pilgrim:tracks_in_the_dark", "pilgrim:fangs_by_the_hedgerow" -> alternatives(11, goods("golden_apple", 1), potion("strong_healing", 2));
            case "pilgrim:ash_on_the_pass" -> List.of(pack("supplies", potion("long_fire_resistance", 2), goods("golden_carrot", 16)),
                    pack("materials", goods("blaze_rod", 4)), coins(16));
            case "pilgrim:smoke_over_blackstone" -> alternatives(16, potion("long_fire_resistance", 2), goods("golden_carrot", 16));
            case "pilgrim:stillness_beyond_the_gate" -> alternatives(20, goods("shulker_shell", 2));
            case "story:failing_harvest", "story:restless_pens" -> key.endsWith("restless_pens")
                    ? List.of(pack("supplies", goods("golden_carrot", 48)), pack("work", goods("bookshelf", 16)), coins(20)) : alternatives(20, goods("golden_carrot", 48));
            case "story:silent_forge" -> withDiamonds(alternatives(32, enchanted("diamond_pickaxe", Map.of("efficiency", 4, "unbreaking", 3))), 8);
            case "story:market_road_troubles" -> withDiamonds(alternatives(24, goods("rail", 64), goods("powered_rail", 16), goods("lantern", 16)), 6);
            case "story:night_bells" -> withDiamonds(alternatives(32, enchanted("diamond_boots", Map.of("feather_falling", 4, "unbreaking", 3))), 8);
            case "story:shadows_on_the_trade_road" -> withDiamonds(alternatives(24, enchanted("bow", Map.of("power", 4, "unbreaking", 3)), enchanted("shield", Map.of("unbreaking", 3))), 6);
            case "story:the_empty_caravan" -> withDiamonds(alternatives(32, goods("golden_carrot", 48), goods("golden_apple", 2), potion("strong_healing", 2)), 8);
            case "story:shrines_between_roads" -> withDiamonds(alternatives(32, enchanted("diamond_pickaxe", Map.of("silk_touch", 1, "efficiency", 4, "unbreaking", 3))), 8);
            case "concord" -> withDiamonds(alternatives(48, goods("rail", 128), goods("powered_rail", 16), goods("lantern", 32)), 12);
            case "story:artifact_journey" -> List.of(
                    pack("supplies",goods("stone_bricks",512),goods("oak_log",256),goods("lantern",64),goods("diamond",12)),
                    pack("roads",goods("rail",256),goods("powered_rail",32),goods("lantern",64),goods("diamond",12)),
                    new Option("coins",128,List.of(goods("diamond",12)),false));
            default -> List.of();
        };
        if ((!key.startsWith("daily:") && !key.startsWith("weekly:")) || profile == RepeatableTargetProfile.NORMAL) return options;
        return options.stream().map(o -> new Option(o.id(), scale((int)o.currency(), profile),
                o.goods().stream().map(g -> new Goods(g.item(), key.equals("weekly:SMITH_WEEK") && o.id().equals("diamonds")
                        ? 5 + profile.id() : scale(g.count(), profile), g.potion(), g.enchantments())).toList(), o.diamondGate())).toList();
    }
    public static int scale(int count, RepeatableTargetProfile profile) {
        if (count == 0) return 0;
        return Math.max(1, (count * (profile == RepeatableTargetProfile.LIGHT ? 88 : profile == RepeatableTargetProfile.HEAVY ? 118 : 100) + 50) / 100);
    }
    public static Goods goods(String item, int count) { return new Goods(item, count, "", Map.of()); }
    private static Goods potion(String id, int count) { return new Goods("potion", count, id, Map.of()); }
    private static Goods enchanted(String item, Map<String, Integer> enchantments) { return new Goods(item, 1, "", Map.copyOf(enchantments)); }
    private static Option pack(String id, Goods... goods) { return new Option(id, 0, List.of(goods), false); }
    private static Option coins(int amount) { return new Option("coins", amount, List.of(), false); }
    private static List<Option> alternatives(int amount, Goods... goods) { return List.of(pack("supplies", goods), coins(amount)); }
    private static List<Option> withDiamonds(List<Option> options, int amount) {
        var result = new java.util.ArrayList<>(options);
        result.add(result.size() - 1, new Option("diamonds", 0, List.of(goods("diamond", amount)), true));
        return List.copyOf(result);
    }
    private VanillaRewardRegistry() { }
}
