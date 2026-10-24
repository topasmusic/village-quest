package de.quest.shop;

import de.quest.economy.ProsperityService;
import de.quest.reward.RewardShopCatalog;
import de.quest.reward.VanillaRewardRegistry;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Single source for real utility-kit deliveries, alongside the existing six reward packs. */
public final class ShopAcquisitionCatalog {
    private static VanillaRewardRegistry.Goods item(String id,int count) { return VanillaRewardRegistry.goods(id,count); }
    private static RewardShopCatalog.Package pack(long price,VanillaRewardRegistry.Goods... goods) {
        return new RewardShopCatalog.Package(price,List.of(goods));
    }
    private static final Map<String,RewardShopCatalog.Package> UTILITY=Map.of(
            "beekeepers_kit",pack(24,item("beehive",1),item("campfire",1),item("glass_bottle",8)),
            "road_camp_kit",pack(30,item("white_bed",1),item("campfire",2),item("lantern",16),item("chest",4),item("torch",64),item("bread",32),item("cooked_beef",24)),
            "village_ledger_desk",pack(30,item("lectern",1),item("chiseled_bookshelf",2),item("bookshelf",4),item("item_frame",8),item("lantern",4),item("writable_book",1)),
            "apiary_supply_crate",pack(33,item("beehive",2),item("campfire",2),item("glass_bottle",32),item("candle",8),item("oak_trapdoor",16)),
            "smithing_supply_rack",pack(36,item("smithing_table",2),item("grindstone",2),item("lantern",4),item("iron_chain",24),item("iron_bars",32)),
            "market_stall_kit",pack(33,item("barrel",4),item("lantern",8),item("red_wool",32),item("white_wool",32),item("spruce_fence",16),item("spruce_sign",8)),
            "pasture_tack_bundle",pack(39,item("lead",8),item("hay_block",16),item("oak_fence",32),item("oak_fence_gate",4),item("lantern",4),item("white_carpet",8)),
            "watch_post_kit",pack(45,item("bell",2),item("spyglass",1),item("lantern",8),item("soul_lantern",4),item("torch",64),item("dark_oak_fence",32)));
    private static final Map<String,RewardShopCatalog.Package> ALL=createAll();
    private ShopAcquisitionCatalog() {}
    private static Map<String,RewardShopCatalog.Package> createAll() {
        var all=new java.util.TreeMap<>(RewardShopCatalog.all()); all.putAll(UTILITY); return Map.copyOf(all);
    }
    public static Map<String,RewardShopCatalog.Package> all() { return ALL; }
    public static RewardShopCatalog.Package utility(String id) {
        var pack=UTILITY.get(id); if(pack==null) throw new IllegalArgumentException("Unknown utility kit"); return pack;
    }
    public static long minimumPrice(String id) {
        var pack=ALL.get(id); if(pack==null) throw new IllegalArgumentException("Unknown acquisition pack");
        return ProsperityService.minimumShopPrice(pack.price());
    }
    public static ItemStack[] utilityStacks(String id) {
        return utility(id).goods().stream().map(goods -> {
            var item=BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(goods.item()));
            if(item==null || item==Items.AIR || !goods.potion().isEmpty() || !goods.enchantments().isEmpty())
                throw new IllegalStateException("Invalid plain utility goods");
            return new ItemStack(item,goods.count());
        }).toArray(ItemStack[]::new);
    }
}
