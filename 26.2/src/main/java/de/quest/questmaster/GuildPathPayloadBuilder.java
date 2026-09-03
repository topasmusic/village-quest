package de.quest.questmaster;

import de.quest.caravan.TradeRouteService;
import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.network.Payloads;
import de.quest.quest.special.RelicQuestStage;
import de.quest.quest.special.ShardRelicQuestStage;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryQuestService;
import de.quest.registry.ModItems;
import de.quest.shrine.VillageBondService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/** Builds the journal's long-term Guild Path independently from questmaster sessions. */
final class GuildPathPayloadBuilder {
    private GuildPathPayloadBuilder() {}

    static List<Payloads.GuildPathNodeData> build(ServerLevel world, ServerPlayer player) {
        UUID playerId = player.getUUID();
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(playerId);
        boolean emptyCaravan = StoryQuestService.isCompleted(world, playerId, StoryArcType.THE_EMPTY_CARAVAN);
        boolean shrineStoryComplete = StoryQuestService.isCompleted(world, playerId, StoryArcType.SHRINES_BETWEEN_ROADS);
        boolean shrineStoryActive = StoryQuestService.isActive(world, playerId, StoryArcType.SHRINES_BETWEEN_ROADS);
        int shrineChapter = shrineStoryActive
                ? StoryQuestService.chapterIndex(world, playerId, StoryArcType.SHRINES_BETWEEN_ROADS)
                : data.getStoryChapterProgress(StoryArcType.SHRINES_BETWEEN_ROADS.id());
        boolean lensInstalled = shrineStoryComplete || shrineChapter > 0;
        boolean sigil = VillageBondService.hasSigil(world, playerId);
        boolean shrine = VillageBondService.shrineCount(world, playerId) > 0;
        boolean board = VillageBondService.villages(world, playerId).stream()
                .anyMatch(village -> village.completions() > 0);

        return List.of(
                node("ledger", ModItems.CARAVAN_LEDGER,
                        emptyCaravan || TradeRouteService.routeCount(world, playerId) > 0
                                || hasItem(player, ModItems.CARAVAN_LEDGER), true),
                node("surveyor_compass", ModItems.SURVEYORS_COMPASS,
                        data.getSurveyorCompassQuestStage() == RelicQuestStage.COMPLETED, emptyCaravan),
                node("starreach_ring", ModItems.STARREACH_RING,
                        data.getShardRelicQuestStage() == ShardRelicQuestStage.COMPLETED, emptyCaravan),
                node("merchant_seal", ModItems.MERCHANT_SEAL,
                        data.getMerchantSealQuestStage() == RelicQuestStage.COMPLETED, emptyCaravan),
                node("shepherd_flute", ModItems.SHEPHERD_FLUTE,
                        data.getShepherdFluteQuestStage() == RelicQuestStage.COMPLETED, emptyCaravan),
                node("apiarist_smoker", ModItems.APIARISTS_SMOKER,
                        data.getApiaristSmokerQuestStage() == RelicQuestStage.COMPLETED, emptyCaravan),
                node("lens", ModItems.CARTOGRAPHERS_LENS, lensInstalled,
                        emptyCaravan && TradeRouteService.routeCount(world, playerId) >= 2),
                node("sigil", ModItems.WAYFARERS_SIGIL, sigil, lensInstalled),
                node("wayshrine", ModItems.GUILD_WAYSHRINE, shrine, sigil),
                node("notice_board", ModItems.GUILD_NOTICE_POST, board, shrine),
                node("courier_satchel", ModItems.GUILD_COURIERS_SATCHEL,
                        shrineStoryComplete, shrineStoryActive && shrineChapter >= 5));
    }

    private static Payloads.GuildPathNodeData node(String id, Item item, boolean complete, boolean unlocked) {
        ItemStack stack = new ItemStack(item);
        return new Payloads.GuildPathNodeData(id, stack, stack.getHoverName(),
                Component.translatable("screen.village-quest.guild_path.node." + id + ".ability"),
                Component.translatable("screen.village-quest.guild_path.node." + id + ".requirement"),
                complete ? 2 : unlocked ? 1 : 0);
    }

    private static boolean hasItem(ServerPlayer player, Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(item)) {
                return true;
            }
        }
        return false;
    }
}
