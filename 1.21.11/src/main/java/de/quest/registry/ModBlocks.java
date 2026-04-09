package de.quest.registry;

import de.quest.VillageQuest;
import de.quest.content.block.WallPlaqueBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModBlocks {
    public static Block APIARY_CHARTER_PLAQUE;
    public static Block VILLAGE_LEDGER_PLAQUE;
    public static Block FORGE_CHARTER_PLAQUE;
    public static Block MARKET_CHARTER_PLAQUE;
    public static Block PASTURE_CHARTER_PLAQUE;
    public static Block WATCH_BELL_RELIQUARY;

    private ModBlocks() {}

    private static Identifier id(String path) {
        return Identifier.of(VillageQuest.MOD_ID, path);
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
        RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, blockId);
        Block block = new WallPlaqueBlock(AbstractBlock.Settings.create()
                .registryKey(blockKey)
                .strength(1.0f)
                .nonOpaque()
                .noCollision());
        Registry.register(Registries.BLOCK, blockId, block);
        return block;
    }
}
