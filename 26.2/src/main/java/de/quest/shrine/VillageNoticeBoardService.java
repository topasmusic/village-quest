package de.quest.shrine;

import de.quest.config.VillageQuestServerConfig;
import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.economy.CurrencyService;
import de.quest.guild.VillageGuildService;
import de.quest.network.VillageNetworkPayloads;
import de.quest.registry.ModBlocks;
import de.quest.village.LivingVillageNetworkService;
import de.quest.village.LivingVillageNetworkState;
import de.quest.village.NetworkSpecialization;
import de.quest.village.VillageRequestGenerator;
import de.quest.village.VillageRequestOffer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Server-authoritative notice-board offer, validation and delivery flow. */
final class VillageNoticeBoardService {
    private static final int TRUSTED_REQUESTS = 2;
    private static final int ALLIED_REQUESTS = 8;

    private VillageNoticeBoardService() {}

    static InteractionResult use(ServerLevel world, ServerPlayer player, BlockPos pos) {
        if (world == null || player == null || pos == null || !world.getBlockState(pos).is(ModBlocks.GUILD_NOTICE_POST)) {
            return InteractionResult.FAIL;
        }
        VillageBondService.VillageBondView view = VillageBondService.inspectCurrentVillage(world, player, false);
        if (view == null) {
            invalid(player);
            return InteractionResult.FAIL;
        }
        VillageAtmosphereService.showBoardState(world, pos, view);
        send(world, player, pos, view);
        return InteractionResult.SUCCESS;
    }

    static void handleAction(ServerPlayer player, VillageNetworkPayloads.NoticeBoardActionPayload payload) {
        if (player == null || payload == null || payload.action() != VillageNetworkPayloads.NoticeBoardActionPayload.ACTION_DELIVER
                || !(player.level() instanceof ServerLevel world)) {
            return;
        }
        BlockPos pos = new BlockPos(payload.worldX(), payload.worldY(), payload.worldZ());
        if (player.blockPosition().distSqr(pos) > 64.0 || !world.getBlockState(pos).is(ModBlocks.GUILD_NOTICE_POST)) {
            invalid(player);
            return;
        }
        VillageBondService.VillageBondView view = VillageBondService.inspectCurrentVillage(world, player, false);
        if (view == null) {
            invalid(player);
            return;
        }
        fulfill(world, player, view, payload.requestId());
        VillageBondService.VillageBondView refreshed = VillageBondService.inspectCurrentVillage(world, player, false);
        if (refreshed != null) {
            send(world, player, pos, refreshed);
        }
    }

    private static void invalid(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable("message.village-quest.village_bond.notice_invalid")
                .withStyle(ChatFormatting.RED), false);
    }

    private static void send(ServerLevel world, ServerPlayer player, BlockPos pos,
                             VillageBondService.VillageBondView view) {
        VillageRequestType request = view.request();
        boolean availableToday = VillageBondService.requestAvailable(world, player.getUUID(), view.index());
        List<VillageRequestOffer> offers = offers(world, player.getUUID(), view);
        VillageRequestOffer primary = offers.isEmpty()
                ? new VillageRequestOffer(request.id(), request, request.amount(), request.reward(),
                LivingVillageNetworkService.deliverySupport(view.network().need(), request),
                view.network().need().matches(request))
                : offers.getFirst();
        int carried = VillageBondService.count(player, primary.request().item());
        Component nextLevel;
        Component nextPerk;
        int nextThreshold;
        if (view.level() == VillageBondLevel.KNOWN) {
            nextLevel = VillageBondLevel.TRUSTED.label();
            nextPerk = Component.translatable("screen.village-quest.notice_board.perk.trusted");
            nextThreshold = TRUSTED_REQUESTS;
        } else if (view.level() == VillageBondLevel.TRUSTED) {
            nextLevel = VillageBondLevel.ALLIED.label();
            nextPerk = Component.translatable("screen.village-quest.notice_board.perk.allied");
            nextThreshold = ALLIED_REQUESTS;
        } else {
            nextLevel = Component.translatable("screen.village-quest.notice_board.max_level");
            nextPerk = Component.translatable("screen.village-quest.notice_board.perk.complete");
            nextThreshold = 0;
        }
        ServerPlayNetworking.send(player, new VillageNetworkPayloads.NoticeBoardPayload(
                pos.getX(), pos.getY(), pos.getZ(), view.type().label(), view.level().label(),
                view.network().condition().label(), view.network().need().label(), view.network().support(),
                primary.request().title(), new ItemStack(primary.request().item()), primary.amount(), carried,
                primary.reward(), CurrencyService.getBalance(world, player.getUUID()), view.completions(),
                view.level().id(), nextThreshold, nextLevel, nextPerk, availableToday,
                availableToday && carried >= primary.amount(), offers.stream().map(offer -> {
                    int offerCarried = VillageBondService.count(player, offer.request().item());
                    return new VillageNetworkPayloads.NoticeBoardOfferData(offer.id(), offer.request().title(),
                            new ItemStack(offer.request().item()), offer.amount(), offerCarried, offer.reward(),
                            offer.support(), offer.primaryNeed(), availableToday && offerCarried >= offer.amount());
                }).toList(), Component.translatable("text.village-quest.adventure_profile."
                        + VillageQuestServerConfig.get().adventureProfile().name().toLowerCase(Locale.ROOT))));
    }

    private static List<VillageRequestOffer> offers(ServerLevel world, UUID playerId,
                                                     VillageBondService.VillageBondView view) {
        PlayerQuestData playerData = VillageBondService.data(world, playerId);
        int lastId = playerData.getTradeRouteInt(VillageBondService.villageKey(view.index(), "last_request")) - 1;
        VillageRequestType last = lastId < 0 ? null : VillageRequestType.byId(lastId, view.type(), view.index());
        NetworkSpecialization specialization = LivingVillageNetworkState.get(world.getServer())
                .network(playerId).specialization();
        int supportBonus = VillageGuildService.noticeSupportBonus(world, playerId)
                + (specialization == NetworkSpecialization.STEWARD ? 4 : 0);
        return VillageRequestGenerator.generate(view.type(), view.network().need(), view.request(), last,
                view.level(), view.network().condition(), VillageQuestServerConfig.get().adventureProfile(),
                view.index() * 31 + view.completions() * 17 + view.network().revision(), supportBonus,
                VillageGuildService.noticeRewardBonus(world, playerId));
    }

    private static boolean fulfill(ServerLevel world, ServerPlayer player,
                                   VillageBondService.VillageBondView view, int requestId) {
        VillageRequestOffer offer = offers(world, player.getUUID(), view).stream()
                .filter(candidate -> candidate.id() == requestId).findFirst().orElse(null);
        if (offer == null) {
            player.sendSystemMessage(Component.translatable("message.village-quest.village_bond.request_changed")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        VillageRequestType request = offer.request();
        PlayerQuestData playerData = VillageBondService.data(world, player.getUUID());
        if (!VillageBondService.requestAvailable(world, player.getUUID(), view.index())) {
            player.sendSystemMessage(Component.translatable("message.village-quest.village_bond.request_daily_locked")
                    .withStyle(ChatFormatting.GRAY), false);
            return false;
        }
        if (!VillageBondService.consume(player, request.item(), offer.amount())) {
            player.sendSystemMessage(Component.translatable("message.village-quest.village_bond.request_missing",
                    offer.amount(), new ItemStack(request.item()).getHoverName()).withStyle(ChatFormatting.RED), false);
            return false;
        }
        int completions = playerData.getTradeRouteInt(VillageBondService.villageKey(view.index(), "completions")) + 1;
        VillageBondLevel level = VillageBondService.levelForCompletions(completions);
        playerData.setTradeRouteInt(VillageBondService.villageKey(view.index(), "completions"), completions);
        playerData.setTradeRouteInt(VillageBondService.villageKey(view.index(), "level"), level.id() + 1);
        playerData.setTradeRouteInt(VillageBondService.villageKey(view.index(), "request_day"),
                VillageBondService.currentRequestDay());
        playerData.setTradeRouteInt(VillageBondService.villageKey(view.index(), "last_request"), request.id() + 1);
        playerData.setTradeRouteInt(VillageBondService.villageKey(view.index(), "request"),
                VillageRequestType.nextAfter(view.type(), request).id() + 1);
        CurrencyService.addBalance(world, player.getUUID(), offer.reward());
        LivingVillageNetworkState.SupportResult result = LivingVillageNetworkService.recordNoticeDelivery(
                world, player.getUUID(), view.index(), view.x(), view.z(), view.type(), request, offer.support());
        VillageGuildService.recordDelivery(world, player.getUUID(), offer.primaryNeed());
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.village_bond.request_complete",
                request.title(), level.label(), CurrencyService.formatDelta(offer.reward()))
                .withStyle(ChatFormatting.GREEN), false);
        if (result.village() != null) {
            String key = result.needAdvanced()
                    ? "message.village-quest.village_network.need_advanced"
                    : "message.village-quest.village_network.supported";
            player.sendSystemMessage(Component.translatable(key, result.village().condition().label(),
                    result.village().need().label(), result.village().support(), 100)
                    .withStyle(ChatFormatting.AQUA), false);
            if (result.needAdvanced()) {
                VillageAtmosphereService.celebrateRecovery(world, VillageBondService.posFor(view));
                int charged = VillageBondService.addWayshrineChargesForVillage(
                        world, player.getUUID(), view.index(), 1);
                if (charged > 0) {
                    player.sendSystemMessage(Component.translatable(
                            "message.village-quest.village_network.project_energy", charged)
                            .withStyle(ChatFormatting.LIGHT_PURPLE), false);
                }
            }
        }
        world.playSound(null, VillageBondService.posFor(view), SoundEvents.VILLAGER_YES,
                SoundSource.BLOCKS, 0.8f, 1.15f);
        VillageBondService.updateStoryBondProgress(world, player);
        return true;
    }
}
