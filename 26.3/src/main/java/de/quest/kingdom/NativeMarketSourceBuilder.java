package de.quest.kingdom;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.Bees;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.storage.loot.LootTable;

/** Current native data only. Direct interaction proofs remain mandatory; no missing-data EMPTY fallback. */
public final class NativeMarketSourceBuilder {
    private static final Set<String> PAYLOADS=Set.of("use_remainder","consumable","container","container_loot",
            "bundle_contents","pot_decorations","block_entity_data","entity_data","bucket_entity_data","block_transformer","compostable","bees","block_state","cushion/color","charged_projectiles","enchantments","writable_book_content","sign_text_front","sign_text_back");
    private NativeMarketSourceBuilder() {}
    /** Independent finite-material buyback scopes. Unrelated growth graphs do not gate these proofs. */
    public static MarketSourceClosure.Snapshot buildRegional(MinecraftServer server) {
        if(server==null || !server.isSameThread()) return held();
        try {
            if(!de.quest.shop.ShopSupplementalCatalog.coversCurrentOffers()) return held();
            var lookup=server.reloadableRegistries().lookup();
            Function<Identifier,DataComponentMap> prototypes=item -> BuiltInRegistries.ITEM.get(item).orElseThrow().components();
            return buildRegional(MarketSourceClosure.catalogAcquisitions(),server.getRecipeManager().getRecipes().stream()
                    .map(holder -> holder.value()).toList(),lookup,prototypes);
        } catch(RuntimeException unavailable) { return held(); }
    }
    static MarketSourceClosure.Snapshot buildRegional(List<MarketAcquisitionBounds.Acquisition> purchases,
            Collection<? extends Recipe<?>> recipes,HolderLookup.Provider lookup,Function<Identifier,DataComponentMap> prototypes) {
        try {
            var paidLoot=new java.util.HashMap<Identifier,NativeMarketRecipeRules.Reading>();
            var freshGrainLoot=new java.util.HashMap<Identifier,NativeMarketRecipeRules.Reading>();
            var paidConsumption=new ArrayList<MarketAcquisitionBounds.RecipeConversion>();
            for(var item:purchases.stream().flatMap(a -> a.outputs().stream()).map(MarketAcquisitionBounds.Goods::item).distinct().toList()) {
                var consumed=NativeMarketConsumptionRules.read(item,prototypes.apply(item));
                if(!consumed.supported()) return held(item);
                paidConsumption.addAll(consumed.rules());
                var nativeItem=BuiltInRegistries.ITEM.get(item).orElseThrow().value();
                if(nativeItem instanceof BlockItem placed && placed.getBlock().getLootTable().isPresent()) {
                    var table=lookup.lookupOrThrow(Registries.LOOT_TABLE).get(placed.getBlock().getLootTable().orElseThrow());
                    if(table.isEmpty()) return held(item);
                    var data=LootTable.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE,lookup),table.get().value()).getOrThrow();
                    NativeMarketBoneMealRules.countOnlyBonuses(data,0,new int[]{4096});
                    var loot=NativeMarketDropRules.read(item,data);
                    if(!loot.supported()) return held(item);
                    paidLoot.put(item,loot);
                    // A bought seed places the existing exact native age-0 crop, not a ripe harvest.
                    // Later world growth is ordinary production; the unchanged shared home-cycle quota
                    // bounds its payment. Patched ripe placement and foreign fresh loot stay held.
                    var block=placed.getBlock(); var state=prototypes.apply(item).get(DataComponents.BLOCK_STATE);
                    if((state==null || state==BlockItemStateProperties.EMPTY) && Set.of(
                            net.minecraft.world.level.block.CropBlock.class,net.minecraft.world.level.block.CarrotBlock.class,
                            net.minecraft.world.level.block.PotatoBlock.class,net.minecraft.world.level.block.StemBlock.class).contains(block.getClass())) {
                        freshGrainLoot.put(item,NativeMarketDropRules.readAtState(item,table.get().value(),lookup,block.defaultBlockState()));
                    }
                }
            }
            var transformers=NativeMarketTransformerCatalog.read(lookup,BuiltInRegistries.ITEM.keySet(),prototypes);
            if(!transformers.supported()) return held();
            var reading=readRecipes(recipes,lookup); if(!reading.supported()) return held();
            boolean emptyHives=true;
            for(var hive:purchases.stream().flatMap(a -> a.outputs().stream()).map(MarketAcquisitionBounds.Goods::item).distinct()
                    .filter(item -> item.getNamespace().equals("minecraft") && Set.of("beehive","bee_nest").contains(item.getPath())).toList()) {
                // Reuse the existing empty hive / live harvest-table boundary. Bought stored
                // honey or bees must not inherit the ordinary external bee-production proof.
                var proof=additional(hive,lookup,prototypes.apply(hive),
                        root -> NativeMarketDirectUseRules.read(root,prototypes.apply(root),lookup,transformers));
                if(!proof.supported()) emptyHives=false;
            }
            var credits=new java.util.TreeMap<Identifier,Long>();
            for(String target:NativeRegionalBuybackSources.TARGETS) {
                if((target.equals("honey_bottle") || target.equals("honeycomb")) && !emptyHives) continue;
                var family=NativeRegionalBuybackSources.family(target);
                // Foreign paid-block loot into this family holds only this SKU.
                boolean incoming=paidLoot.entrySet().stream().anyMatch(e -> {
                    if(family.containsKey(e.getKey())) return false;
                    var actual=NativeRegionalBuybackSources.grain(target)?freshGrainLoot.getOrDefault(e.getKey(),e.getValue()):e.getValue();
                    return !actual.supported() || actual.rules().stream().flatMap(r -> r.outputs().stream()).anyMatch(g -> family.containsKey(g.item()));
                });
                incoming|=paidConsumption.stream().anyMatch(r -> !NativeRegionalBuybackSources.conserves(r,family));
                if(incoming) continue;
                var credit=NativeRegionalBuybackSources.credit(target,purchases,reading,item -> additional(item,lookup,prototypes.apply(item),
                        root -> NativeMarketDirectUseRules.read(root,prototypes.apply(root),lookup,transformers)));
                if(credit.isPresent()) credits.put(Identifier.withDefaultNamespace(target),credit.getAsLong());
            }
            return MarketSourceClosure.reviewed(credits);
        } catch(RuntimeException missing) { return held(); }
    }
    /** A narrow, reviewable coal/coal-block loop. Foreign coal-producing recipes hold this SKU. */
    static MarketSourceClosure.Snapshot regional(List<MarketAcquisitionBounds.Acquisition> acquisitions,
            NativeMarketRecipeRules.Reading reading,Function<Identifier,NativeMarketRecipeRules.Reading> uses) {
        if(acquisitions==null || reading==null || !reading.supported() || uses==null) return held();
        var coal=Identifier.withDefaultNamespace("coal"); var block=Identifier.withDefaultNamespace("coal_block");
        var ore=Identifier.withDefaultNamespace("coal_ore"); var deep=Identifier.withDefaultNamespace("deepslate_coal_ore");
        var scope=Set.of(coal,block); var inputs=Set.of(coal,block,ore,deep);
        // Natural mined ore is legitimate income. A newly sold ore would need its own Fortune/loot bound first.
        if(acquisitions.stream().flatMap(a -> a.outputs().stream()).anyMatch(g -> g.item().equals(ore) || g.item().equals(deep))) return held();
        var recipes=new ArrayList<MarketAcquisitionBounds.RecipeConversion>();
        for(var recipe:reading.rules()) if(recipe.outputs().stream().anyMatch(g -> scope.contains(g.item()))) {
            if(recipe.outputs().stream().anyMatch(g -> !scope.contains(g.item()))
                    || recipe.inputs().stream().flatMap(c -> c.choices().stream()).anyMatch(g -> !inputs.contains(g.item()))) return held();
            long output=recipe.outputs().stream().mapToLong(g -> g.quantity()*(g.item().equals(block)?9L:1L)).sum();
            long input=recipe.inputs().stream().mapToLong(c -> c.choices().stream()
                    .mapToLong(g -> g.quantity()*(g.item().equals(block)?9L:1L)).min().orElse(0)).sum();
            if(output>input) return held();
            recipes.add(recipe);
        }
        var sources=acquisitions.stream().filter(a -> a.outputs().stream().anyMatch(g -> scope.contains(g.item())))
                .map(a -> new MarketAcquisitionBounds.Acquisition(a.price(),a.outputs().stream().filter(g -> scope.contains(g.item())).toList())).toList();
        return MarketSourceClosure.build(sources,new NativeMarketRecipeRules.Reading(true,recipes),
                item -> scope.contains(item)?uses.apply(item):unknown());
    }
    /** Reads the currently loaded manager/registry/prototypes on their owning server thread only. */
    public static MarketSourceClosure.Snapshot build(MinecraftServer server) {
        if(server==null || !server.isSameThread()) return held();
        try {
            if(!de.quest.shop.ShopSupplementalCatalog.coversCurrentOffers()) return held();
            var lookup=server.reloadableRegistries().lookup();
            var recipes=server.getRecipeManager().getRecipes().stream().map(holder -> holder.value()).toList();
            Function<Identifier,DataComponentMap> prototypes=item -> BuiltInRegistries.ITEM.get(item).orElseThrow().components();
            var transformers=NativeMarketTransformerCatalog.read(lookup,BuiltInRegistries.ITEM.keySet(),prototypes);
            if(!transformers.supported()) return held();
            return build(MarketSourceClosure.catalogAcquisitions(),recipes,lookup,prototypes,
                    item -> NativeMarketDirectUseRules.read(item,prototypes.apply(item),lookup,transformers));
        } catch(RuntimeException missing) { return held(); }
    }
    public static MarketSourceClosure.Snapshot build(List<MarketAcquisitionBounds.Acquisition> acquisitions,
            Collection<? extends Recipe<?>> recipes,HolderLookup.Provider lookup,
            Function<Identifier,DataComponentMap> prototypes,Function<Identifier,NativeMarketRecipeRules.Reading> directUses) {
        var reading=prototypes==null || directUses==null ? unknown() : readRecipes(recipes,lookup);
        return MarketSourceClosure.build(acquisitions,reading,item -> additional(item,lookup,prototypes.apply(item),directUses));
    }
    /** Same bounded recipe gate as the live build, exposed within the package for targeted source audits. */
    static NativeMarketRecipeRules.Reading readRecipes(Collection<? extends Recipe<?>> recipes,HolderLookup.Provider lookup) {
        var reading=unknown();
        try {
            if(recipes!=null && recipes.size()<=4096 && lookup!=null) {
                var frozen=List.copyOf(recipes); var ops=RegistryOps.create(JsonOps.INSTANCE,lookup); var budget=new Budget();
                reading=NativeMarketRecipeRules.readAll(frozen,lookup);
                // Never call a foreign recipe serializer just to decide whether its output payload is safe.
                if(reading.supported() && !frozen.stream().allMatch(recipe -> safePayloads(Recipe.DIRECT_CODEC.encodeStart(ops,recipe).getOrThrow(),0,budget)))
                    reading=unknown();
            }
        } catch(RuntimeException invalid) { reading=unknown(); }
        return reading;
    }
    private static NativeMarketRecipeRules.Reading additional(Identifier item,HolderLookup.Provider lookup,
            DataComponentMap prototype,Function<Identifier,NativeMarketRecipeRules.Reading> directUses) {
        // These live payloads can release goods unrelated to the item's ordinary prototype credit.
        // Native container prototypes (including BARREL) carry the immutable EMPTY singleton.
        // It copies no slots and destruction emits no contents; this is a payload proof only,
        // never permission for a container callback class. Its exact direct-use scope is still required.
        if(prototype==null || (prototype.has(DataComponents.SIGN_TEXT_FRONT)
                && prototype.get(DataComponents.SIGN_TEXT_FRONT)!=net.minecraft.world.level.block.entity.SignText.EMPTY)
                || (prototype.has(DataComponents.SIGN_TEXT_BACK)
                && prototype.get(DataComponents.SIGN_TEXT_BACK)!=net.minecraft.world.level.block.entity.SignText.EMPTY)
                || (prototype.has(DataComponents.CHARGED_PROJECTILES)
                && !prototype.get(DataComponents.CHARGED_PROJECTILES).isEmpty()) || (prototype.has(DataComponents.CONTAINER)
                && prototype.get(DataComponents.CONTAINER)!=ItemContainerContents.EMPTY) || prototype.has(DataComponents.CONTAINER_LOOT)
                || (prototype.has(DataComponents.BEES) && prototype.get(DataComponents.BEES)!=Bees.EMPTY)
                || (prototype.has(DataComponents.BLOCK_STATE) && prototype.get(DataComponents.BLOCK_STATE)!=BlockItemStateProperties.EMPTY
                    && !NativeMarketBeehiveRules.safePlacement(item,prototype.get(DataComponents.BLOCK_STATE))
                    && !NativeMarketGolemStatueRules.safePlacement(item,prototype.get(DataComponents.BLOCK_STATE)))
                || (prototype.has(DataComponents.BUNDLE_CONTENTS) && prototype.get(DataComponents.BUNDLE_CONTENTS)!=BundleContents.EMPTY)
                || prototype.has(DataComponents.POT_DECORATIONS)
                || prototype.has(DataComponents.BLOCK_ENTITY_DATA) || prototype.has(DataComponents.ENTITY_DATA)
                || prototype.has(DataComponents.BUCKET_ENTITY_DATA)) return unknown();
        var rules=new ArrayList<MarketAcquisitionBounds.RecipeConversion>();
        // The native edit/sign packet checks the component, not the item class or whether a
        // normal GUI was opened. Cover changed live prototypes on ANY reachable item. Recipe
        // patches introducing that behavior are separately held until a typed payload proof.
        if(prototype.has(DataComponents.WRITABLE_BOOK_CONTENT)) rules.add(new MarketAcquisitionBounds.RecipeConversion(
                List.of(new MarketAcquisitionBounds.InputChoice(List.of(new MarketAcquisitionBounds.Goods(item,1)))),
                List.of(new MarketAcquisitionBounds.Goods(BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.WRITTEN_BOOK),1))));
        var consumption=NativeMarketConsumptionRules.read(item,prototype);
        if(!append(rules,consumption)) return unknown();
        var nativeItem=BuiltInRegistries.ITEM.get(item).orElseThrow().value();
        if(nativeItem instanceof BlockItem block) {
            var key=block.getBlock().getLootTable();
            if(key.isPresent()) {
                // getLootTable(key) silently defaults to EMPTY; existence must be checked first.
                var table=lookup.lookupOrThrow(Registries.LOOT_TABLE).get(key.get());
                if(table.isEmpty()) return unknown();
                if(table.get().value()!=LootTable.EMPTY) {
                    var placed=block.getBlock();
                    // Proved fresh crops/stems and the empty composter use fresh-state loot.
                    // Imported BLOCK_STATE remains rejected above, crop/stem targets remain
                    // held, and the independent growth/fertilizer direct-use proof is mandatory.
                    // No callback approval follows from distinguishing fresh loot from production.
                    boolean freshProduction=Set.of(net.minecraft.world.level.block.CropBlock.class,
                            net.minecraft.world.level.block.CarrotBlock.class,net.minecraft.world.level.block.PotatoBlock.class,
                            net.minecraft.world.level.block.StemBlock.class,
                            net.minecraft.world.level.block.ComposterBlock.class).contains(placed.getClass());
                    var loot=placed.getClass()==BedBlock.class || placed.getClass()==net.minecraft.world.level.block.StrawBedBlock.class
                            ?NativeMarketPairedLootRules.readBed(item,table.get().value(),lookup)
                            :NativeMarketSlabRules.member(placed)?NativeMarketSlabRules.read(item,table.get().value(),lookup)
                            :freshProduction?NativeMarketDropRules.readAtState(item,table.get().value(),lookup,placed.defaultBlockState())
                            :NativeMarketDropRules.read(item,table.get().value(),lookup);
                    if(!append(rules,loot)) return unknown();
                }
            }
        }
        if(!append(rules,directUses.apply(item))) return unknown();
        return new NativeMarketRecipeRules.Reading(true,rules);
    }
    private static boolean append(List<MarketAcquisitionBounds.RecipeConversion> rules,NativeMarketRecipeRules.Reading reading) {
        if(reading==null || !reading.supported() || rules.size()+reading.rules().size()>8192) return false;
        rules.addAll(reading.rules()); return true;
    }
    private static boolean safePayloads(JsonElement value,int depth,Budget budget) {
        if(value==null || depth>32 || --budget.remaining<0) return false;
        if(value.isJsonArray()) { for(var child:value.getAsJsonArray()) if(!safePayloads(child,depth+1,budget)) return false; }
        else if(value.isJsonObject()) for(var field:value.getAsJsonObject().entrySet()) {
            if(field.getKey().equals("components") && field.getValue().isJsonObject())
                for(var key:field.getValue().getAsJsonObject().keySet()) {
                    var normalized=key.startsWith("!") ? key.substring(1) : key;
                    var id=Identifier.tryParse(normalized);
                    if(id==null || !id.getNamespace().equals("minecraft") || PAYLOADS.contains(id.getPath())) return false;
                }
            if(!safePayloads(field.getValue(),depth+1,budget)) return false;
        }
        return true;
    }
    private static NativeMarketRecipeRules.Reading unknown() { return new NativeMarketRecipeRules.Reading(false,List.of()); }
    private static MarketSourceClosure.Snapshot held() { return MarketSourceClosure.build(List.of(),unknown(),item -> unknown()); }
    private static MarketSourceClosure.Snapshot held(Identifier item) {
        return MarketSourceClosure.build(List.of(new MarketAcquisitionBounds.Acquisition(1,List.of(new MarketAcquisitionBounds.Goods(item,1)))),
                new NativeMarketRecipeRules.Reading(true,List.of()),root -> unknown());
    }
    private static final class Budget { int remaining=500_000; }
}
