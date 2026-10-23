package de.quest.registry;

import de.quest.VillageQuest;
import de.quest.content.block.GuildWayshrineBlockEntity;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
    public static BlockEntityType<GuildWayshrineBlockEntity> GUILD_WAYSHRINE;
    public static BlockEntityType<de.quest.content.block.KingdomAnchorBlockEntity> KINGDOM_ANCHOR;

    private ModBlockEntities() {}

    public static void register() {
        GUILD_WAYSHRINE = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "guild_wayshrine"),
                new BlockEntityType<>(GuildWayshrineBlockEntity::new, Set.of(ModBlocks.GUILD_WAYSHRINE))
        );
        KINGDOM_ANCHOR = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(VillageQuest.MOD_ID, "kingdom_anchor"),
                new BlockEntityType<>(de.quest.content.block.KingdomAnchorBlockEntity::new,
                        Set.of(ModBlocks.COUNCIL_SEAT, ModBlocks.INN_BARREL, ModBlocks.MARKET_DESK, ModBlocks.STOREHOUSE_ANCHOR)));
        VillageQuest.LOGGER.info("Registered block entities");
    }
}
