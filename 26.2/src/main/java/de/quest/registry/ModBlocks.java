package de.quest.registry;

import de.quest.VillageQuest;
import de.quest.content.block.WallPlaqueBlock;
import de.quest.content.block.EmberglassLanternBlock;
import de.quest.content.block.GuildMilestoneBlock;
import de.quest.content.block.GuildNoticePostBlock;
import de.quest.content.block.GuildWayshrineBlock;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootTable;

public final class ModBlocks {
    public static Block APIARY_CHARTER_PLAQUE;
    public static Block VILLAGE_LEDGER_PLAQUE;
    public static Block FORGE_CHARTER_PLAQUE;
    public static Block MARKET_CHARTER_PLAQUE;
    public static Block PASTURE_CHARTER_PLAQUE;
    public static Block WATCH_BELL_RELIQUARY;
    public static Block GUILD_WAYSHRINE;
    public static Block GUILD_NOTICE_POST;
    public static Block EMBERGLASS_LANTERN;
    public static Block GUILD_MILESTONE;

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
        // Obsidian uses hardness 50; 25 keeps the shrine durable but mines in roughly half the time.
        GUILD_WAYSHRINE = registerFunctional("guild_wayshrine", GuildWayshrineBlock::new,
                MapColor.STONE, SoundType.STONE, 25.0f, 1200.0f, state ->
                        state.getValue(GuildWayshrineBlock.ACTIVE) ? 12 : 0, PushReaction.BLOCK);
        GUILD_NOTICE_POST = registerFunctional("guild_notice_post", GuildNoticePostBlock::new,
                MapColor.WOOD, SoundType.WOOD, 2.0f, state -> 0);
        EMBERGLASS_LANTERN = registerFunctional("emberglass_lantern", EmberglassLanternBlock::new,
                MapColor.METAL, SoundType.LANTERN, 2.5f, state -> 13);
        GUILD_MILESTONE = registerFunctional("guild_milestone", GuildMilestoneBlock::new,
                MapColor.STONE, SoundType.STONE, 0.8f, 1200.0f, state -> 0, PushReaction.BLOCK);

        VillageQuest.LOGGER.info("Registered blocks");
    }

    private static Block registerFunctional(String path,
                                            java.util.function.Function<BlockBehaviour.Properties, Block> factory,
                                            MapColor mapColor,
                                            SoundType sound,
                                            float strength,
                                            java.util.function.ToIntFunction<net.minecraft.world.level.block.state.BlockState> lightLevel) {
        return registerFunctional(path, factory, mapColor, sound, strength, strength, lightLevel);
    }

    private static Block registerFunctional(String path,
                                            java.util.function.Function<BlockBehaviour.Properties, Block> factory,
                                            MapColor mapColor,
                                            SoundType sound,
                                            float destroyTime,
                                            float explosionResistance,
                                            java.util.function.ToIntFunction<net.minecraft.world.level.block.state.BlockState> lightLevel) {
        return registerFunctional(path, factory, mapColor, sound, destroyTime, explosionResistance,
                lightLevel, PushReaction.NORMAL);
    }

    private static Block registerFunctional(String path,
                                            java.util.function.Function<BlockBehaviour.Properties, Block> factory,
                                            MapColor mapColor,
                                            SoundType sound,
                                            float destroyTime,
                                            float explosionResistance,
                                            java.util.function.ToIntFunction<net.minecraft.world.level.block.state.BlockState> lightLevel,
                                            PushReaction pushReaction) {
        Identifier blockId = id(path);
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, blockId);
        ResourceKey<LootTable> lootKey = ResourceKey.create(Registries.LOOT_TABLE, blockId.withPath(value -> "blocks/" + value));
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .setId(blockKey)
                .overrideLootTable(Optional.of(lootKey))
                .mapColor(mapColor)
                .sound(sound)
                .strength(destroyTime, explosionResistance)
                .pushReaction(pushReaction)
                .noOcclusion()
                .lightLevel(lightLevel);
        Block block = factory.apply(properties);
        Registry.register(BuiltInRegistries.BLOCK, blockId, block);
        return block;
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
