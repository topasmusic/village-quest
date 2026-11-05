package de.quest.kingdom;

import de.quest.shrine.VillageBondType;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

/** Fixed Vanilla profiles; saved personality chooses a recurring specialty, never a GUI reroll. */
public final class KingdomMarketCatalog {
    private KingdomMarketCatalog() {}
    /** Partner level is earned village recognition, never an independent merchant level. */
    public record Partner(VillageBondType type,int level) {
        public Partner { if(type==null || level<0 || level>5) throw new IllegalArgumentException("Invalid regional partner"); }
    }
    private record Supply(String item,int tier,int lot,int stock,long price) {
        KingdomMarketLedger.Goods goods() { return extra(item,lot,price,stock); }
    }
    private static Supply supply(String item,int tier,int lot,int stock,long price) { return new Supply(item,tier,lot,stock,price); }
    private static KingdomMarketLedger.Goods extra(String item,int lot,long price,int stock) {
        return new KingdomMarketLedger.Goods(item,Identifier.withDefaultNamespace(item),lot,stock,0,price,0);
    }
    private static final Map<VillageBondType,List<Supply>> REGIONAL=Map.of(
        VillageBondType.GRANARY,List.of(
            supply("bone_meal",1,32,2,6),supply("barrel",1,8,2,7),supply("composter",1,8,2,6),
            supply("hay_block",1,16,2,8),supply("bread",1,32,2,6),supply("oak_fence",1,32,2,6),
            supply("charcoal",2,32,2,7),supply("scaffolding",2,32,2,8),supply("cauldron",2,4,2,9),
            supply("hopper",3,4,2,12),supply("dispenser",3,8,2,12),supply("target",4,8,2,12)),
        VillageBondType.FORGE,List.of(
            supply("coal",1,32,2,6),supply("iron_ingot",1,8,4,8),supply("copper_ingot",1,16,4,7),
            supply("iron_chain",1,16,2,9),supply("iron_bars",1,32,2,8),supply("stone_bricks",1,32,2,5),
            supply("rail",2,32,2,12),supply("minecart",2,1,2,8),supply("lightning_rod",2,8,2,8),
            supply("powered_rail",3,8,2,18),supply("detector_rail",3,8,2,14),supply("activator_rail",3,8,2,14),
            supply("hopper",3,4,2,14),supply("piston",3,4,2,14),supply("observer",3,4,2,14),
            supply("dropper",3,8,2,10),supply("comparator",4,4,2,18),supply("hopper_minecart",4,1,2,18)),
        VillageBondType.PASTURE,List.of(
            supply("hay_block",1,16,2,8),supply("lead",1,4,2,7),supply("oak_fence",1,32,2,6),
            supply("white_wool",1,32,2,6),supply("leather",1,16,2,8),supply("bread",1,32,2,6),
            supply("minecart",2,1,2,8),supply("cauldron",2,4,2,9),supply("scaffolding",2,32,2,8),
            supply("chest_minecart",3,1,2,10),supply("spyglass",3,1,2,8),supply("hopper_minecart",4,1,2,15)),
        VillageBondType.APIARY,List.of(
            supply("honey_bottle",1,16,1,8),supply("honeycomb",1,16,2,7),supply("candle",1,16,2,7),
            supply("barrel",1,8,2,7),supply("dandelion",1,32,2,4),supply("copper_ingot",1,16,2,7),
            supply("beehive",2,4,2,10),supply("honey_block",2,8,2,10),supply("honeycomb_block",2,8,2,10),
            supply("tinted_glass",3,16,2,12),supply("sticky_piston",4,8,2,16),
            supply("waxed_cut_copper",4,16,2,12),supply("waxed_cut_copper_stairs",4,16,2,12)),
        VillageBondType.ARCHIVE,List.of(
            supply("paper",1,32,2,4),supply("book",1,8,2,8),supply("barrel",1,8,2,7),
            supply("bookshelf",1,8,2,10),supply("oak_sign",1,8,2,6),supply("item_frame",1,8,2,8),
            supply("scaffolding",2,32,2,8),supply("chiseled_bookshelf",2,8,2,10),supply("lantern",2,8,2,8),
            supply("spyglass",3,1,2,8),supply("glow_item_frame",3,4,2,10),supply("lectern",4,4,2,12)));

    /** Called once for a new saved visit. Date changes variety, never a GUI/relog refresh. */
    static KingdomMarketLedger.TraderPlan regional(KingdomPersonRegistry.View person,VillageBondType type,int level,long date,List<Partner> partners) {
        if(person==null || person.role()!=KingdomPersonRegistry.Role.MARKET_TRADER || type==null || level<0 || level>5
                || date<0 || partners==null || partners.size()>5 || partners.stream().anyMatch(java.util.Objects::isNull))
            throw new IllegalArgumentException("Invalid regional load");
        int tier=Math.max(1,level); long seed=person.appearanceSeed() ^ Long.rotateLeft(date,17);
        int count=tier==1?3+(int)Math.floorMod(seed,2):tier<=3?4+(int)Math.floorMod(seed,3):5+(int)Math.floorMod(seed,4);
        var goods=new java.util.ArrayList<KingdomMarketLedger.Goods>();
        // Each real partner contributes at most one useful specialty, inside the same eight rows.
        for(var partner:partners) {
            if(partner.type()==type || goods.size()>=2) continue;
            var imports=new java.util.ArrayList<>(REGIONAL.get(partner.type()).stream().filter(s -> s.tier<=Math.max(1,partner.level()) && s.tier>=2).toList());
            // Rails and ordinary carts stay useful as a forge's pool grows: three tickets each.
            if(partner.type()==VillageBondType.FORGE) for(var core:List.copyOf(imports))
                if(core.item.equals("rail") || core.item.equals("minecart")) { imports.add(core); imports.add(core); }
            if(imports.isEmpty()) continue;
            var selected=imports.get(new java.util.Random(seed+partner.type().ordinal()).nextInt(imports.size())).goods();
            if(goods.stream().noneMatch(g -> g.sku().equals(selected.sku()))) goods.add(selected);
        }
        var candidates=new java.util.ArrayList<>(REGIONAL.get(type).stream().filter(s -> s.tier<=tier).toList());
        java.util.Collections.shuffle(candidates,new java.util.Random(seed));
        for(var supply:candidates) {
            if(goods.size()>=count) break;
            if(goods.stream().anyMatch(g -> g.sku().equals(supply.item))) continue;
            var selected=supply.goods();
            // A visibly larger, proportionally dearer shipment, still at most 128 items.
            if(tier==5 && largeShipment(supply) && Math.floorMod(seed+supply.item.hashCode(),3)==0)
                selected=extra(supply.item,supply.lot*2,supply.price*2,Math.min(128/(supply.lot*2),supply.stock));
            goods.add(selected);
        }
        return new KingdomMarketLedger.TraderPlan(person.id(),person.village(),goods);
    }
    private static boolean largeShipment(Supply supply) {
        return supply.lot<=32 && java.util.Set.of("rail","scaffolding","hopper","honey_block","waxed_cut_copper",
                "waxed_cut_copper_stairs","iron_bars","stone_bricks","oak_fence","barrel","bookshelf").contains(supply.item);
    }
    public static int priceFactor(KingdomPersonRegistry.View person) {
        if(person==null || person.role()!=KingdomPersonRegistry.Role.MARKET_TRADER) throw new IllegalArgumentException("Not a market person");
        return Math.floorMod(person.appearanceSeed()>>>5,31)-15;
    }
    private static KingdomMarketLedger.Goods core(String item,int lot,int stock,long sale,long buy) {
        return new KingdomMarketLedger.Goods(item,Identifier.withDefaultNamespace(item),lot,stock,128/lot,sale,buy);
    }
    private static KingdomMarketLedger.Goods extra(String item,int lot,long sale) {
        return new KingdomMarketLedger.Goods(item,Identifier.withDefaultNamespace(item),lot,64/lot,0,sale,0);
    }
    private static final Map<VillageBondType,List<KingdomMarketLedger.Goods>> CORE=Map.of(
            VillageBondType.GRANARY,List.of(core("wheat_seeds",64,1,4,1),core("wheat",32,2,4,1),core("bread",32,2,6,2),core("potato",32,2,4,1)),
            VillageBondType.FORGE,List.of(core("coal",32,2,6,2),core("copper_ingot",16,4,7,2),core("iron_ingot",8,8,8,2),core("stone_bricks",32,2,5,1)),
            VillageBondType.PASTURE,List.of(core("hay_block",16,4,8,2),core("white_wool",32,2,6,2),core("leather",16,4,8,2),core("egg",16,1,5,1)),
            VillageBondType.APIARY,List.of(core("dandelion",32,2,4,1),core("honey_bottle",16,1,8,2),core("honeycomb",16,4,7,2),core("candle",16,4,7,2)),
            VillageBondType.ARCHIVE,List.of(core("paper",32,2,4,1),core("book",8,8,8,2),core("ink_sac",16,4,6,2),core("feather",32,2,6,2)));
    private static final Map<VillageBondType,List<KingdomMarketLedger.Goods>> EXTRA=Map.of(
            VillageBondType.GRANARY,List.of(extra("carrot",32,5),extra("beetroot",32,5),extra("pumpkin",16,7),extra("melon",16,7)),
            VillageBondType.FORGE,List.of(extra("charcoal",32,6),extra("gold_ingot",8,12),extra("glass",32,5),extra("redstone",16,7)),
            VillageBondType.PASTURE,List.of(extra("brown_wool",32,6),extra("rabbit_hide",16,7),extra("string",32,5),extra("beef",16,6)),
            VillageBondType.APIARY,List.of(extra("azure_bluet",32,5),extra("oxeye_daisy",32,5),extra("poppy",32,5),extra("allium",32,5)),
            VillageBondType.ARCHIVE,List.of(extra("bookshelf",8,10),extra("black_dye",16,6),extra("glass",32,5),extra("stick",32,3)));

    public static List<KingdomMarketLedger.Goods> offers(VillageBondType type,int personality) {
        if(type==null || personality<0 || personality>3) throw new IllegalArgumentException("Invalid market profile");
        var result=new java.util.ArrayList<KingdomMarketLedger.Goods>(); var core=CORE.get(type);
        for(int i=0;i<core.size();i++) {
            var g=core.get(i); result.add(new KingdomMarketLedger.Goods(g.sku(),g.item(),g.lot(),g.stock()*(i==personality?2:1),g.demand(),g.sell(),g.buy()));
        }
        result.add(EXTRA.get(type).get(personality)); return List.copyOf(result);
    }
    public static KingdomMarketLedger.TraderPlan plan(KingdomPersonRegistry.View person,VillageBondType type) {
        return plan(person,type,List.of());
    }
    /** All imported specialties already belong to the complete acquisition catalog below. */
    static KingdomMarketLedger.TraderPlan plan(KingdomPersonRegistry.View person,VillageBondType type,List<VillageBondType> partners) {
        if(person==null || person.role()!=KingdomPersonRegistry.Role.MARKET_TRADER) throw new IllegalArgumentException("Not a market person");
        if(partners==null || partners.size()>5 || partners.stream().anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("Invalid partner profiles");
        var goods=new java.util.ArrayList<>(offers(type,person.personality()));
        for(var partner:partners) {
            if(goods.size()==8) break;
            var imported=EXTRA.get(partner).get(person.personality());
            if(goods.stream().noneMatch(g -> g.sku().equals(imported.sku()))) goods.add(imported);
        }
        return new KingdomMarketLedger.TraderPlan(person.id(),person.village(),goods);
    }
    /** Includes every attainable specialty, even when its owner is not currently visiting. */
    public static List<MarketAcquisitionBounds.Acquisition> acquisitions() {
        var sources=new java.util.TreeMap<Identifier,MarketAcquisitionBounds.Acquisition>();
        for(var pool:REGIONAL.values()) for(var supply:pool) {
            var g=supply.goods();
            for(int scale=1;scale<=(largeShipment(supply)?2:1);scale++) {
                var source=new MarketAcquisitionBounds.Acquisition(MarketPricePolicy.sell(g.sell()*scale,-15,15),
                        List.of(new MarketAcquisitionBounds.Goods(g.item(),g.lot()*scale)));
                var previous=sources.get(g.item());
                if(previous==null || source.price()*previous.outputs().getFirst().quantity()<previous.price()*g.lot()*scale)
                    sources.put(g.item(),source);
            }
        }
        for(var type:VillageBondType.values()) for(int personality=0;personality<4;personality++) for(var goods:offers(type,personality)) {
            var source=new MarketAcquisitionBounds.Acquisition(MarketPricePolicy.sell(goods.sell(),-15,15),List.of(new MarketAcquisitionBounds.Goods(goods.item(),goods.lot())));
            var previous=sources.get(goods.item());
            if(previous==null || source.price()*previous.outputs().getFirst().quantity()<previous.price()*goods.lot()) sources.put(goods.item(),source);
        }
        return List.copyOf(sources.values());
    }
}
