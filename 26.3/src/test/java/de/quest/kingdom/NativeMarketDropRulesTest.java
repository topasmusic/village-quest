package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

/** Pinned 26.3 loot data and adversarial quantities; no loot-context execution or World. */
final class NativeMarketDropRulesTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    @Test void bookshelvesKeepSilkAndThreeBookOutcomesSeparate() throws Exception {
        var reading=NativeMarketDropRules.read(id("bookshelf"),nativeLoot("bookshelf"));
        assertTrue(reading.supported()); assertEquals(2,reading.rules().size());
        assertTrue(reading.rules().stream().anyMatch(r -> r.outputs().equals(List.of(g("bookshelf",1)))));
        assertTrue(reading.rules().stream().anyMatch(r -> r.outputs().equals(List.of(g("book",3)))));
        var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(12,List.of(g("bookshelf",1)))),List.of(),reading.rules());
        assertTrue(bounds.complete()); assertEquals(12,bounds.maximumPayout(id("book"),3));
    }
    @Test void playerHeadCopiesOnlyCosmeticProfileSoundAndNameWithoutImportingStock() throws Exception {
        var data=nativeLoot("player_head");
        var reading=NativeMarketDropRules.read(id("player_head"),data);
        assertTrue(reading.supported());
        assertEquals(List.of(g("player_head",1)),reading.rules().getFirst().outputs());
        for(String dangerous:List.of("container","container_loot","block_entity_data","entity_data","block_state")) {
            var changed=data.deepCopy().getAsJsonObject();
            changed.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0)
                    .getAsJsonObject().getAsJsonObject("modifier").getAsJsonArray("include").add("minecraft:"+dangerous);
            assertFalse(NativeMarketDropRules.read(id("player_head"),changed).supported(),dangerous);
        }
    }
    @Test void nativeCampfiresAndCappedFortuneCountsAreReadWithoutAToolContext() throws Exception {
        var fire=NativeMarketDropRules.read(id("campfire"),nativeLoot("campfire"));
        assertTrue(fire.supported()); assertTrue(fire.rules().stream().anyMatch(r -> r.outputs().equals(List.of(g("charcoal",2)))));
        var melon=NativeMarketDropRules.read(id("melon"),nativeLoot("melon"));
        assertTrue(melon.supported()); assertTrue(melon.rules().stream().anyMatch(r -> r.outputs().equals(List.of(g("melon_slice",9)))));
        var glow=NativeMarketDropRules.read(id("glowstone"),nativeLoot("glowstone"));
        assertTrue(glow.supported()); assertTrue(glow.rules().stream().anyMatch(r -> r.outputs().equals(List.of(g("glowstone_dust",4)))));
        assertFalse(NativeMarketDropRules.read(id("iron_ore"),nativeLoot("iron_ore")).supported(),"Uncapped Fortune needs a separate proven level bound");
    }
    @Test void coProductsAndPoolRollsShareOneConsumedBlockBudget() {
        var reading=read("""
            {"type":"minecraft:block","pools":[{"rolls":2,"entries":[{"type":"minecraft:group","children":[
             {"type":"minecraft:item","name":"minecraft:book"},{"type":"minecraft:item","name":"minecraft:paper"}]}]}]}
            """);
        assertTrue(reading.supported()); assertEquals(List.of(g("book",2),g("paper",2)),reading.rules().getFirst().outputs());
        var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(8,List.of(g("bookshelf",1)))),List.of(),reading.rules());
        assertTrue(bounds.maximumPayout(id("book"),2)+bounds.maximumPayout(id("paper"),2)<=8);
    }
    @Test void modifiersApplyToEveryEmittedStackBeforeDuplicateItemsMerge() {
        var reading=read("""
            {"type":"minecraft:block","pools":[{"rolls":1,"modifier":{"type":"minecraft:set_count","count":3},
             "entries":[{"type":"minecraft:group","children":[{"type":"minecraft:item","name":"minecraft:book"},
             {"type":"minecraft:item","name":"minecraft:book"}]}]}]}
            """);
        assertTrue(reading.supported()); assertEquals(List.of(g("book",6)),reading.rules().getFirst().outputs());
    }
    @Test void conditionalDecreasesCannotUnderstateTheUnmodifiedDrop() {
        var reading=read("""
            {"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:book",
             "modifier":[{"type":"minecraft:set_count","count":5},{"type":"minecraft:set_count","count":1,
             "condition":{"type":"minecraft:random_chance","chance":0.5}}]}]}]}
            """);
        assertTrue(reading.supported()); assertEquals(List.of(g("book",5)),reading.rules().getFirst().outputs());
    }
    @Test void enchantedCountUsesTheEncodedLevelCapAndDoesNotApplyItsLimitAtLevelZero() {
        for(int limit:List.of(0,1,7)) {
            var reading=read("{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:book\",\"modifier\":["
                    +"{\"type\":\"minecraft:set_count\",\"count\":5},"
                    +"{\"type\":\"minecraft:enchanted_count_increase\",\"enchantment\":\"minecraft:looting\",\"count\":{\"type\":\"minecraft:uniform\",\"min\":0,\"max\":1},\"limit\":"+limit+"}]}]}]}");
            assertTrue(reading.supported());
            assertEquals(List.of(g("book",limit==0?260:Math.max(5,limit))),reading.rules().getFirst().outputs());
        }
    }
    @Test void unknownEnchantedCountProvidersAndOrdinarySmeltingRemainHeld() {
        var prefix="{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:book\",\"modifier\":";
        assertFalse(read(prefix+"{\"type\":\"minecraft:enchanted_count_increase\",\"enchantment\":\"minecraft:looting\",\"count\":{\"type\":\"minecraft:score\"}}}]}]}").supported());
        assertFalse(read(prefix+"{\"type\":\"minecraft:furnace_smelt\"}}]}]}").supported(),
                "Only the explicit death-loot reader may rely on the mandatory current recipe graph");
    }
    @Test void unknownOrUnboundedDropsNeverProvidePartialRules() throws Exception {
        for(String entry:List.of("{\"type\":\"minecraft:dynamic\",\"name\":\"minecraft:sherds\"}",
                "{\"type\":\"minecraft:loot_table\",\"value\":\"minecraft:blocks/bookshelf\"}",
                "{\"type\":\"minecraft:item\",\"name\":\"foreign:book\"}",
                "{\"type\":\"minecraft:item\",\"name\":\"minecraft:book\",\"modifier\":{\"type\":\"foreign:increase\"}}")) {
            var reading=read("{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":["+entry+"]}]}");
            assertFalse(reading.supported()); assertTrue(reading.rules().isEmpty());
        }
        assertFalse(NativeMarketDropRules.read(id("decorated_pot"),nativeLoot("decorated_pot")).supported());
        assertFalse(read("{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"bonus_rolls\":1,\"entries\":[]}]}" ).supported());
    }
    @Test void nativeLootTableCodecRoundTripPreservesBookshelfOutcomes() throws Exception {
        var lookup=net.minecraft.data.registries.VanillaRegistries.createReloadableLookup(
                net.minecraft.data.registries.VanillaRegistries.createWorldLookup());
        var ops=net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,lookup);
        var table=net.minecraft.world.level.storage.loot.LootTable.DIRECT_CODEC.parse(ops,nativeLoot("bookshelf")).getOrThrow();
        var typed=NativeMarketDropRules.read(id("bookshelf"),table,lookup);
        var raw=NativeMarketDropRules.read(id("bookshelf"),nativeLoot("bookshelf"));
        assertTrue(typed.supported()); assertEquals(raw.rules(),typed.rules());
    }
    @Test void unknownModifiersOnEmptyOutcomesStillHoldTheCompleteSchema() {
        var entry=read("""
            {"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:empty",
             "modifier":{"type":"foreign:future_effect"}}]}]}
            """);
        assertFalse(entry.supported()); assertTrue(entry.rules().isEmpty());
        assertFalse(read("{\"type\":\"minecraft:block\",\"pools\":[],\"modifier\":{\"type\":\"foreign:future_effect\"}}").supported());
    }
    @Test void copiedPlacementPartCannotBypassTheItemOnlyQuantityProof() {
        var reading=NativeMarketDropRules.read(id("black_bed"),JsonParser.parseString("""
            {"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:black_bed",
             "modifier":{"type":"minecraft:copy_state","block":"minecraft:black_bed","properties":["part"]}}]}]}
            """));
        assertFalse(reading.supported(),"Copied HEAD placement could change the two-cell output count; it needs its own state closure");
        assertTrue(reading.rules().isEmpty());
    }
    @Test void conditionalFortuneCapCannotProveAFiniteMaximum() throws Exception {
        var melon=nativeLoot("melon").getAsJsonObject();
        var modifiers=melon.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0)
                .getAsJsonObject().getAsJsonArray("children").get(1).getAsJsonObject().getAsJsonArray("modifier");
        modifiers.get(2).getAsJsonObject().add("condition",JsonParser.parseString("{\"type\":\"minecraft:random_chance\",\"chance\":0.5}"));
        assertFalse(NativeMarketDropRules.read(id("melon"),melon).supported());
    }
    @Test void deeplyNestedCountProvidersCannotBypassTheTreeBudget() {
        com.google.gson.JsonElement count=new com.google.gson.JsonPrimitive(1);
        for(int i=0;i<64;i++) {
            var nested=new com.google.gson.JsonObject(); nested.addProperty("type","minecraft:constant"); nested.add("value",count); count=nested;
        }
        var data=JsonParser.parseString("{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:book\",\"modifier\":{\"type\":\"minecraft:set_count\",\"count\":1}}]}]}").getAsJsonObject();
        data.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject()
                .getAsJsonObject("modifier").add("count",count);
        var reading=NativeMarketDropRules.read(id("bookshelf"),data);
        assertFalse(reading.supported()); assertTrue(reading.rules().isEmpty());
    }
    @Test void freshCropLootExcludesOnlyTheActuallyFalseMatureBranches() throws Exception {
        for(String[] crop:List.of(new String[]{"wheat_seeds","wheat"},new String[]{"carrot","carrots"},new String[]{"potato","potatoes"})) {
            var block=((net.minecraft.world.item.BlockItem)net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id(crop[0]))).getBlock();
            var data=nativeLoot(crop[1]);
            var fresh=NativeMarketDropRules.readAtState(id(crop[0]),data,block.defaultBlockState());
            assertTrue(fresh.supported(),crop[0]+" age0 cannot reach mature Fortune pool");
            assertEquals(1,fresh.rules().size());
            assertEquals(List.of(g(crop[0],1)),fresh.rules().getFirst().outputs());
            assertFalse(NativeMarketDropRules.read(id(crop[0]),data).supported(),"No state proof must retain mature Fortune");
            var mature=block.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE,7);
            assertFalse(NativeMarketDropRules.readAtState(id(crop[0]),data,mature).supported(),"Mature uncapped Fortune remains held");
        }
    }
    @Test void emptyComposterBreakLootCannotBorrowTheConsumedFillFromAReadyComposter() throws Exception {
        var data=nativeLoot("composter");
        var state=net.minecraft.world.level.block.Blocks.COMPOSTER.defaultBlockState();
        var empty=NativeMarketDropRules.readAtState(id("composter"),data,state);
        assertTrue(empty.supported()); assertEquals(1,empty.rules().size());
        assertEquals(List.of(g("composter",1)),empty.rules().getFirst().outputs());
        var full=NativeMarketDropRules.readAtState(id("composter"),data,state.setValue(net.minecraft.world.level.block.ComposterBlock.LEVEL,8));
        assertTrue(full.supported()); assertTrue(full.rules().stream().anyMatch(r -> r.outputs().contains(g("bone_meal",1))));
        assertTrue(NativeMarketDropRules.read(id("composter"),data).rules().stream().anyMatch(r -> r.outputs().contains(g("bone_meal",1))),"Without state evidence the filled break outcome cannot disappear");
        var patched=data.getAsJsonObject().deepCopy();
        patched.getAsJsonArray("pools").get(1).getAsJsonObject().getAsJsonObject("condition").getAsJsonObject("state").addProperty("level","0");
        assertTrue(NativeMarketDropRules.readAtState(id("composter"),patched,state).rules().stream().anyMatch(r -> r.outputs().contains(g("bone_meal",1))),"Current changed level0 output must remain an immediate conversion");
    }
    @Test void freshStemStillIncludesItsImmediateThreeSeedMaximum() throws Exception {
        var state=net.minecraft.world.level.block.Blocks.PUMPKIN_STEM.defaultBlockState();
        var reading=NativeMarketDropRules.readAtState(id("pumpkin_seeds"),nativeLoot("pumpkin_stem"),state);
        assertTrue(reading.supported()); assertEquals(List.of(g("pumpkin_seeds",3)),reading.rules().getFirst().outputs());
        var bounds=MarketAcquisitionBounds.solve(List.of(new MarketAcquisitionBounds.Acquisition(6,List.of(g("pumpkin_seeds",1)))),List.of(),reading.rules());
        assertEquals(0,bounds.maximumPayout(id("pumpkin_seeds"),1),"Immediate seed multiplication cannot earn positive buyback credit");
    }
    @Test void changedOrUnknownCropConditionsCannotHideReachableFortune() throws Exception {
        var fresh=net.minecraft.world.level.block.Blocks.CARROTS.defaultBlockState();
        for(String condition:List.of(
                "{\"type\":\"minecraft:match_block\",\"blocks\":\"minecraft:carrots\",\"state\":{\"age\":\"0\"}}",
                "{\"type\":\"foreign:match_block\",\"blocks\":\"minecraft:carrots\",\"state\":{\"age\":\"7\"}}",
                "{\"type\":\"minecraft:match_block\",\"blocks\":\"#minecraft:crops\",\"state\":{\"age\":\"7\"}}",
                "{\"type\":\"minecraft:match_block\",\"blocks\":\"minecraft:carrots\",\"state\":{\"age\":{\"min\":\"7\"}}}",
                "{\"type\":\"minecraft:match_block\",\"blocks\":\"minecraft:carrots\",\"state\":{\"age\":\"7\"},\"future\":true}")) {
            var data=nativeLoot("carrots").getAsJsonObject();
            data.getAsJsonArray("pools").get(1).getAsJsonObject().add("condition",JsonParser.parseString(condition));
            assertFalse(NativeMarketDropRules.readAtState(id("carrot"),data,fresh).supported(),condition);
        }
        assertFalse(NativeMarketDropRules.readAtState(id("carrot"),nativeLoot("carrots"),net.minecraft.world.level.block.Blocks.WHEAT.defaultBlockState()).supported());
        assertFalse(NativeMarketDropRules.readAtState(id("carrot"),nativeLoot("carrots"),null).supported());
    }
    @Test void falseModifierIsRemovedButUnknownModifierConditionStillHolds() {
        var data=JsonParser.parseString("""
            {"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:carrot",
            "modifier":{"type":"minecraft:apply_bonus","enchantment":"minecraft:fortune","formula":"minecraft:ore_drops",
            "condition":{"type":"minecraft:match_block","blocks":"minecraft:carrots","state":{"age":"7"}}}}]}]}
            """).getAsJsonObject();
        var state=net.minecraft.world.level.block.Blocks.CARROTS.defaultBlockState();
        var reading=NativeMarketDropRules.readAtState(id("carrot"),data,state);
        assertTrue(reading.supported()); assertEquals(List.of(g("carrot",1)),reading.rules().getFirst().outputs());
        // Reading must leave the current definition untouched for mature/other callers.
        assertFalse(NativeMarketDropRules.read(id("carrot"),data).supported());
        var modifier=data.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject().getAsJsonObject("modifier");
        modifier.add("condition",JsonParser.parseString("{\"type\":\"minecraft:random_chance\",\"chance\":0.5}"));
        assertFalse(NativeMarketDropRules.readAtState(id("carrot"),data,state).supported());
    }
    @Test void statueCopiedPoseDoesNotEraseCurrentLootCountsOrAuthorizeOtherPlacementStates() throws Exception {
        for(String name:NativeMarketGolemStatueRules.NAMES) {
            var data=nativeLoot(name).getAsJsonObject();
            var reading=NativeMarketDropRules.read(id(name),data);
            assertTrue(reading.supported(),name); assertEquals(List.of(g(name,1)),reading.rules().getFirst().outputs());
            var entry=data.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
            var count=new com.google.gson.JsonObject(); count.addProperty("type","minecraft:set_count"); count.addProperty("count",3);
            entry.getAsJsonArray("modifier").add(count);
            assertEquals(List.of(g(name,3)),NativeMarketDropRules.read(id(name),data).rules().getFirst().outputs());
            var copied=entry.getAsJsonArray("modifier").get(1).getAsJsonObject();
            copied.add("properties",JsonParser.parseString("[\"copper_golem_pose\",\"waterlogged\"]"));
            assertFalse(NativeMarketDropRules.read(id(name),data).supported());
            copied.add("properties",JsonParser.parseString("[\"copper_golem_pose\"]"));
            copied.addProperty("block","minecraft:copper_door");
            assertFalse(NativeMarketDropRules.read(id(name),data).supported());
        }
    }
    private static com.google.gson.JsonElement nativeLoot(String name) throws Exception {
        try(var in=NativeMarketDropRulesTest.class.getResourceAsStream("/data/minecraft/loot_table/blocks/"+name+".json")) {
            assertNotNull(in); return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8));
        }
    }
    private static NativeMarketRecipeRules.Reading read(String json) { return NativeMarketDropRules.read(id("bookshelf"),JsonParser.parseString(json)); }
    private static Identifier id(String path) { return Identifier.withDefaultNamespace(path); }
    private static MarketAcquisitionBounds.Goods g(String path,int count) { return new MarketAcquisitionBounds.Goods(id(path),count); }
}
