package de.quest.shop;

import de.quest.economy.ProsperityService;
import de.quest.kingdom.MarketAcquisitionBounds;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.Identifier;

/** Actual single-item deliveries, including visual variants; depicted materials are not goods. */
public final class ShopSupplementalCatalog {
    public record Offer(long price,Identifier item) {
        public Offer { if(price<=0 || item==null) throw new IllegalArgumentException("Invalid supplemental offer"); }
        public MarketAcquisitionBounds.Acquisition acquisition() {
            return new MarketAcquisitionBounds.Acquisition(ProsperityService.minimumShopPrice(price),
                    List.of(new MarketAcquisitionBounds.Goods(item,1)));
        }
    }
    private static final Map<String,Offer> ALL=create();
    private ShopSupplementalCatalog() {}
    private static Map<String,Offer> create() {
        var all=new TreeMap<String,Offer>();
        for(String id:List.of("honigfass","bienenwabe","decor_barrel","decor_chiseled_bookshelf","decor_haybale",
                "decor_water_bucket","decor_lava_bucket","decor_furnace","decor_barrel_of_water",
                "decor_skeleton_plushie","decor_zombie_plushie","decor_koala_plushie","decor_creeper_plushie","decor_barrel_of_wine"))
            all.put(id,new Offer(50,Identifier.withDefaultNamespace("player_head")));
        for(String id:List.of("gemaelde_begleiter","gemaelde_majestaet","gemaelde_happy_doge")) painting(all,id,45);
        for(String id:List.of("gemaelde_pepe_the_almighty","gemaelde_over_there","gemaelde_the_legend")) painting(all,id,30);
        for(String id:List.of("gemaelde_good_doge","gemaelde_something_is_sus","gemaelde_ancient_warrior")) painting(all,id,15);
        all.put("zombie_trophy",new Offer(30,Identifier.withDefaultNamespace("zombie_head")));
        all.put("skeleton_trophy",new Offer(36,Identifier.withDefaultNamespace("skeleton_skull")));
        all.put("creeper_trophy",new Offer(48,Identifier.withDefaultNamespace("creeper_head")));
        for(String id:List.of("apiary_charter_plaque","village_ledger_plaque","forge_charter_plaque",
                "market_charter_plaque","pasture_charter_plaque","watch_bell_reliquary"))
            all.put(id,new Offer(35,Identifier.fromNamespaceAndPath("village-quest",id)));
        return Map.copyOf(all);
    }
    private static void painting(Map<String,Offer> all,String id,long price) {
        all.put(id,new Offer(price,Identifier.withDefaultNamespace("painting")));
    }
    public static Map<String,Offer> all() { return ALL; }
    /** A newly added shop offer must not silently disappear from the global price proof. */
    public static boolean coversCurrentOffers() {
        var covered=new java.util.HashSet<>(ShopAcquisitionCatalog.all().keySet());
        covered.addAll(ALL.keySet());
        return covered.equals(ShopService.offerIds());
    }
    public static Offer offer(String id) {
        var offer=ALL.get(id); if(offer==null) throw new IllegalArgumentException("Unknown supplemental offer"); return offer;
    }
}
