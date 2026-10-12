package de.quest.reward;
import java.util.*;

/** Fixed shop contents and list prices. Transport commissions remain a separate economy. */
public final class RewardShopCatalog {
    public record Package(long price, List<VanillaRewardRegistry.Goods> goods) { }
    private static VanillaRewardRegistry.Goods item(String id, int count) { return VanillaRewardRegistry.goods(id,count); }
    private static VanillaRewardRegistry.Goods potion(String id) { return new VanillaRewardRegistry.Goods("potion",2,id,Map.of()); }
    private static final Map<String,Package> PACKAGES = Map.of(
            "proviantbeutel", new Package(12,List.of(item("golden_carrot",24))),
            "hunters_satchel", new Package(14,List.of(item("golden_apple",1),potion("strong_healing"))),
            "nether_supplies", new Package(20,List.of(potion("long_fire_resistance"),item("golden_carrot",16))),
            "light_supplies", new Package(30,List.of(item("glass",64),item("lantern",16))),
            "rail_supplies", new Package(32,List.of(item("rail",64),item("powered_rail",8))),
            "leather_supplies", new Package(32,List.of(item("leather",48))));
    public static Package get(String id) { return PACKAGES.get(id); }
    public static Map<String,Package> all() { return PACKAGES; }
    private RewardShopCatalog() { }
}
