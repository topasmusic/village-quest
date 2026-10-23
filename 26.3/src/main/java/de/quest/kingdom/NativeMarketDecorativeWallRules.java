package de.quest.kingdom;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PlayerHeadItem;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PlayerHeadBlock;
import net.minecraft.world.level.block.PlayerWallHeadBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.storage.loot.LootTable;

/** Two alternative one-item placements, not a two-cell structure. Exact native decorative callbacks only. */
final class NativeMarketDecorativeWallRules {
    private static final Field WALL=wallField();
    private NativeMarketDecorativeWallRules() {}
    static Optional<Block> wall(Item source) {
        try {
            if(source==null || WALL==null || source.getClass()!=StandingAndWallBlockItem.class
                    && source.getClass()!=PlayerHeadItem.class && source.getClass()!=net.minecraft.world.item.HangingSignItem.class)
                return Optional.empty();
            var standing=((StandingAndWallBlockItem)source).getBlock();
            var wall=(Block)WALL.get(source);
            if(wall==null || standing.asItem()!=source || wall.asItem()!=source
                    || !BuiltInRegistries.BLOCK.getKey(standing).getNamespace().equals("minecraft")
                    || !BuiltInRegistries.BLOCK.getKey(wall).getNamespace().equals("minecraft")) return Optional.empty();
            boolean torch=standing.getClass()==TorchBlock.class && wall.getClass()==WallTorchBlock.class
                    || standing.getClass()==RedstoneTorchBlock.class && wall.getClass()==RedstoneWallTorchBlock.class;
            boolean head=standing.getClass()==SkullBlock.class && wall.getClass()==WallSkullBlock.class
                    || standing.getClass()==SkullBlock.class && wall.getClass()==net.minecraft.world.level.block.PiglinWallSkullBlock.class
                    || standing.getClass()==PlayerHeadBlock.class && wall.getClass()==PlayerWallHeadBlock.class;
            boolean wither=source==net.minecraft.world.item.Items.WITHER_SKELETON_SKULL
                    && standing==net.minecraft.world.level.block.Blocks.WITHER_SKELETON_SKULL
                    && wall==net.minecraft.world.level.block.Blocks.WITHER_SKELETON_WALL_SKULL
                    && standing.getClass()==net.minecraft.world.level.block.WitherSkullBlock.class
                    && wall.getClass()==net.minecraft.world.level.block.WitherWallSkullBlock.class;
            if(head && ((AbstractSkullBlock)standing).getType()!=((AbstractSkullBlock)wall).getType()) return Optional.empty();
            boolean sign=standing.getClass()==net.minecraft.world.level.block.StandingSignBlock.class
                    && wall.getClass()==net.minecraft.world.level.block.WallSignBlock.class
                    && ((net.minecraft.world.level.block.SignBlock)standing).type()==((net.minecraft.world.level.block.SignBlock)wall).type();
            boolean hanging=source.getClass()==net.minecraft.world.item.HangingSignItem.class
                    && standing.getClass()==net.minecraft.world.level.block.CeilingHangingSignBlock.class
                    && wall.getClass()==net.minecraft.world.level.block.WallHangingSignBlock.class
                    && ((net.minecraft.world.level.block.SignBlock)standing).type()==((net.minecraft.world.level.block.SignBlock)wall).type();
            return torch || head || wither || sign || hanging?Optional.of(wall):Optional.empty();
        } catch(IllegalAccessException | RuntimeException invalid) { return Optional.empty(); }
    }
    static NativeMarketRecipeRules.Reading read(Identifier item,Item source,HolderLookup.Provider lookup,
            NativeMarketTransformerCatalog.Reading catalog) {
        try {
            var wall=wall(source);
            if(wall.isEmpty() || lookup==null || catalog==null || !catalog.supported()) return unknown();
            // Floor loot is independently mandatory in the builder. Never guess a wall alias
            // or skip its current transforms/loot merely because vanilla shares the floor table.
            var transforms=NativeMarketBlockTransformRules.readVariant(item,wall.get(),catalog.definitions(),lookup);
            if(!transforms.supported()) return unknown();
            var rules=new ArrayList<>(transforms.rules());
            var key=wall.get().getLootTable();
            if(key.isPresent()) {
                var table=lookup.lookupOrThrow(Registries.LOOT_TABLE).get(key.get());
                if(table.isEmpty()) return unknown();
                if(table.get().value()!=LootTable.EMPTY) {
                    var loot=NativeMarketDropRules.read(item,table.get().value(),lookup);
                    if(!loot.supported()) return unknown();
                    rules.addAll(loot.rules());
                }
            }
            // Native support loss uses ordinary destruction. Torches only emit particles and
            // redstone state; skulls only expose name/profile/sound and client animation. No
            // inventory or extra-item callback. Plain and exact hanging signs initialize both
            // sides to SignText.EMPTY; HangingSignBlockEntity only overrides text size and sound.
            // native editing uses literal text and only retains its existing blank style. Ink/dye/
            // waxing consumes one external applicator without producing goods. The builder holds
            // both imported text components and recipe patches, so click-command authority cannot
            // be inherited from stored text. HangingSignItem only adds placement viability;
            // attaching another sign consumes that external sign, never emits one from this sign.
            // Both ceiling/wall support loss use the same ordinary current destruction boundary.
            // Both exact wither variants additionally invoke the same native spawn checker;
            // their ordinary floor/wall loot/transforms above do not prove that callback.
            if(source==net.minecraft.world.item.Items.WITHER_SKELETON_SKULL) {
                var wither=readWither(item,lookup);
                if(!wither.supported()) return unknown();
                rules.addAll(wither.rules());
            }
            return new NativeMarketRecipeRules.Reading(true,rules.stream().distinct().toList());
        } catch(RuntimeException unproved) { return unknown(); }
    }
    private static NativeMarketRecipeRules.Reading readWither(Identifier item,HolderLookup.Provider lookup) {
        try {
            var body=lookup.lookupOrThrow(Registries.BLOCK).get(net.minecraft.tags.BlockTags.WITHER_SUMMON_BASE_BLOCKS).orElseThrow();
            if(!body.stream().limit(257).map(h -> h.value()).collect(java.util.stream.Collectors.toSet()).equals(java.util.Set.of(
                    net.minecraft.world.level.block.Blocks.SOUL_SAND,net.minecraft.world.level.block.Blocks.SOUL_SOIL))) return unknown();
            var key=net.minecraft.world.entity.EntityTypes.WITHER.getDefaultLootTable().orElseThrow();
            var death=NativeMarketDropRules.readEntityDeath(item,lookup.lookupOrThrow(Registries.LOOT_TABLE).get(key).orElseThrow().value(),lookup);
            if(!death.supported()) return unknown();
            var rules=new ArrayList<MarketAcquisitionBounds.RecipeConversion>();
            var input=List.of(new MarketAcquisitionBounds.InputChoice(List.of(new MarketAcquisitionBounds.Goods(item,1))));
            var star=new MarketAcquisitionBounds.Goods(BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.NETHER_STAR),1);
            // Native ^^^/###/~#~ consumes three skulls and four soul blocks through
            // clearPatternBlocks, with no ordinary pattern drops. Ignore the other inputs'
            // costs conservatively and charge the full result to ONE skull, regardless of
            // floor/wall form or successful-spawn conditions. WitherBoss.dropCustomDeathLoot
            // adds one HARD-CODED nether star in addition to current entity loot, even when
            // that table is EMPTY. Extended item lifetime does not change its quantity.
            if(death.rules().isEmpty()) rules.add(new MarketAcquisitionBounds.RecipeConversion(input,List.of(star)));
            else for(var outcome:death.rules()) {
                var outputs=new ArrayList<>(outcome.outputs()); outputs.add(star);
                rules.add(new MarketAcquisitionBounds.RecipeConversion(input,outputs));
            }
            // Initial invulnerability/explosion and later external-mob/block destruction need
            // later AI ticks; external resources are not generated by the consumed skull.
            return new NativeMarketRecipeRules.Reading(true,rules);
        } catch(RuntimeException invalid) { return unknown(); }
    }
    private static Field wallField() {
        try {
            var field=StandingAndWallBlockItem.class.getDeclaredField("wallBlock");
            return field.getType()==Block.class && field.trySetAccessible()?field:null;
        } catch(ReflectiveOperationException | RuntimeException unavailable) { return null; }
    }
    private static NativeMarketRecipeRules.Reading unknown() { return new NativeMarketRecipeRules.Reading(false,List.of()); }
}
