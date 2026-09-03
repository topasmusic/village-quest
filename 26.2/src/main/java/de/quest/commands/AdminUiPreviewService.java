package de.quest.commands;

import de.quest.config.VillageQuestServerConfig;
import de.quest.economy.CurrencyService;
import de.quest.network.Payloads;
import de.quest.network.VillageNetworkPayloads;
import de.quest.pilgrim.PilgrimService;
import de.quest.questmaster.QuestMasterService;
import de.quest.questmaster.QuestMasterUiService;
import de.quest.shrine.VillageBondLevel;
import de.quest.shrine.VillageBondType;
import de.quest.shrine.VillageRequestType;
import de.quest.village.VillageCondition;
import de.quest.village.VillageNeed;
import de.quest.village.VillageRequestGenerator;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Non-persistent UI fixtures kept out of the command registry and gameplay services. */
final class AdminUiPreviewService {
    private AdminUiPreviewService() {}

    static int openQuestMaster(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return missingPlayer(source);
        ServerLevel world = source.getServer().overworld();
        var questMaster = QuestMasterService.findNearbyQuestMaster(
                world, player.getX(), player.getY(), player.getZ());
        if (questMaster == null) questMaster = QuestMasterService.spawnNearPlayer(world, player);
        if (questMaster == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questmaster.spawn.failed")
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }
        QuestMasterUiService.open(world, player, questMaster);
        return 1;
    }

    static int openPilgrim(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return missingPlayer(source);
        ServerLevel world = source.getServer().overworld();
        var pilgrim = PilgrimService.findActivePilgrim(world);
        if (pilgrim == null) pilgrim = PilgrimService.spawnNearPlayer(world, player, true);
        if (pilgrim != null && player.distanceToSqr(pilgrim) > 16.0d) {
            pilgrim.setPos(player.getX() + 1.5d, player.getY(), player.getZ() + 1.5d);
        }
        if (pilgrim == null) {
            source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.pilgrim.spawn.failed")
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }
        PilgrimService.openTrade(world, player, pilgrim);
        return 1;
    }

    static int openWayshrine(CommandSourceStack source, boolean owner) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return missingPlayer(source);
        int x = player.getBlockX();
        int y = player.getBlockY();
        int z = player.getBlockZ();
        int currentIndex = 10_000;
        int priceMultiplier = owner ? 1 : 2;
        List<Payloads.TradeRouteShrineData> destinations = List.of(
                new Payloads.TradeRouteShrineData(currentIndex, x, y, z,
                        Component.translatable("text.village-quest.wayshrine.homestead", 1),
                        true, 0, VillageBondLevel.TRUSTED.id(), 1, 300),
                new Payloads.TradeRouteShrineData(currentIndex + 1, x + 144, y, z - 48,
                        Component.translatable("text.village-quest.wayshrine.village", 2,
                                VillageBondType.ARCHIVE.label()), false, 4 * priceMultiplier,
                        VillageBondLevel.KNOWN.id(), 2, 600),
                new Payloads.TradeRouteShrineData(currentIndex + 2, x - 128, y, z + 80,
                        Component.translatable("text.village-quest.wayshrine.village", 3,
                                VillageBondType.FORGE.label()), false, 2 * priceMultiplier,
                        VillageBondLevel.TRUSTED.id(), 1, 300),
                new Payloads.TradeRouteShrineData(currentIndex + 3, x + 96, y, z + 144,
                        Component.translatable("text.village-quest.wayshrine.village", 4,
                                VillageBondType.PASTURE.label()), false, 2 * priceMultiplier,
                        VillageBondLevel.ALLIED.id(), 1, 240),
                new Payloads.TradeRouteShrineData(currentIndex + 4, x - 160, y, z - 112,
                        Component.translatable("text.village-quest.wayshrine.village", 5,
                                VillageBondType.APIARY.label()), false, 2 * priceMultiplier,
                        VillageBondLevel.TRUSTED.id(), 1, 300));
        ServerPlayNetworking.send(player, new VillageNetworkPayloads.WayshrinePayload(
                currentIndex, destinations, owner ? player.getGameProfile().name() : "Guild Tester",
                owner, priceMultiplier, 0, 240L, 14, 5, 50));
        return 1;
    }

    static int openNoticeBoard(CommandSourceStack source, VillageBondLevel level) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return missingPlayer(source);
        VillageBondType type;
        VillageRequestType request;
        int inventoryAmount;
        int completions;
        int nextThreshold;
        Component nextLevel;
        Component nextPerk;
        if (level == VillageBondLevel.KNOWN) {
            type = VillageBondType.ARCHIVE;
            request = VillageRequestType.ARCHIVE_BOOKS;
            inventoryAmount = 5;
            completions = 0;
            nextThreshold = 2;
            nextLevel = VillageBondLevel.TRUSTED.label();
            nextPerk = Component.translatable("screen.village-quest.notice_board.perk.trusted");
        } else if (level == VillageBondLevel.TRUSTED) {
            type = VillageBondType.FORGE;
            request = VillageRequestType.FORGE_IRON;
            inventoryAmount = request.amount();
            completions = 2;
            nextThreshold = 8;
            nextLevel = VillageBondLevel.ALLIED.label();
            nextPerk = Component.translatable("screen.village-quest.notice_board.perk.allied");
        } else {
            type = VillageBondType.PASTURE;
            request = VillageRequestType.PASTURE_WOOL;
            inventoryAmount = request.amount();
            completions = 8;
            nextThreshold = 0;
            nextLevel = Component.translatable("screen.village-quest.notice_board.max_level");
            nextPerk = Component.translatable("screen.village-quest.notice_board.perk.complete");
        }

        ServerLevel world = source.getServer().overworld();
        VillageNeed previewNeed = VillageNeed.forVillage(type, 0);
        var previewOffers = VillageRequestGenerator.generate(type, previewNeed, request, null, level,
                VillageCondition.STABLE, VillageQuestServerConfig.AdventureProfile.STANDARD,
                0, 0, 1.0);
        ServerPlayNetworking.send(player, new VillageNetworkPayloads.NoticeBoardPayload(
                player.getBlockX(), player.getBlockY(), player.getBlockZ(),
                type.label(), level.label(), VillageCondition.STABLE.label(), previewNeed.label(), 50,
                request.title(), new ItemStack(request.item()), request.amount(), inventoryAmount, request.reward(),
                CurrencyService.getBalance(world, player.getUUID()), completions, level.id(), nextThreshold,
                nextLevel, nextPerk, true, inventoryAmount >= request.amount(),
                previewOffers.stream().map(offer -> new VillageNetworkPayloads.NoticeBoardOfferData(
                        offer.id(), offer.request().title(), new ItemStack(offer.request().item()),
                        offer.amount(), inventoryAmount, offer.reward(), offer.support(),
                        offer.primaryNeed(), inventoryAmount >= offer.amount())).toList(),
                Component.translatable("text.village-quest.adventure_profile.standard")));
        return 1;
    }

    private static int missingPlayer(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("command.village-quest.questadmin.player_required")
                .withStyle(ChatFormatting.RED), false);
        return 0;
    }
}
