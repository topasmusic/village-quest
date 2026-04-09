package de.quest.registry;

import de.quest.VillageQuest;
import de.quest.content.block.WallPlaqueBlock;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootTable;

public final class ModBlocks {
    public static Block APIARY_CHARTER_PLAQUE;
    public static Block VILLAGE_LEDGER_PLAQUE;
    public static Block FORGE_CHARTER_PLAQUE;
    public static Block MARKET_CHARTER_PLAQUE;
    public static Block PASTURE_CHARTER_PLAQUE;
    public static Block WATCH_BELL_RELIQUARY;

    private ModBlocks() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, path);
    }

    public static void register() {
        APIARY_CHARTER_PLAQUE = registerWallPlaque("apiary_charter_plaque");
        VILLAGE_LEDGER_PLAQUE = registerWallPlaque("village_ledger_plaque");
        FORGE_CHARTER_PLAQUE = registerWallPlaque("forge_charter_plaque");
        MARKET_CHARTER_PLAQUE = registerWallPlaque("market_charter_plaque");
        PASTURE_CHARTER_PLAQUE = registerWallPlaque("pasture_charter_plaque");
        WATCH_BELL_RELIQUARY = registerWallPlaque("watch_bell_reliquary");

        VillageQuest.LOGGER.info("Registered blocks");
    }

    private static Block registerWallPlaque(String path) {
        Identifier blockId = id(path);
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, blockId);
        ResourceKey<LootTable> lootKey = ResourceKey.create(Registries.LOOT_TABLE, blockId.withPath(value -> "blocks/" + value));
        Block block = new WallPlaqueBlock(wallDecorProperties(blockKey, lootKey, MapColor.WOOD, SoundType.WOOD));
        Registry.register(BuiltInRegistries.BLOCK, blockId, block);
        return block;
    }

    private static BlockBehaviour.Properties wallDecorProperties(ResourceKey<Block> blockKey,
                                                                 ResourceKey<LootTable> lootKey,
                                                                 MapColor mapColor,
                                                                 SoundType sound) {
        return BlockBehaviour.Properties.of()
                .setId(blockKey)
                .overrideLootTable(Optional.of(lootKey))
                .mapColor(mapColor)
                .sound(sound)
                .strength(1.0f)
                .noOcclusion()
                .noCollision();
    }
}
