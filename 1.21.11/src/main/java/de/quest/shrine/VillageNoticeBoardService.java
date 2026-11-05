package de.quest.shrine;

import de.quest.caravan.TradeRouteService;
import de.quest.caravan.RegionalDispatchService;
import de.quest.caravan.VillageLifeService;
import de.quest.content.story.ShadowsTradeRoadEncounterService;
import de.quest.config.VillageQuestServerConfig;
import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.economy.CurrencyService;
import de.quest.guild.VillageGuildService;
import de.quest.guildtown.GuildTownService;
import de.quest.guildtown.GuildTownProgress;
import de.quest.guildtown.GuildTownAfterstoryService;
import de.quest.network.VillageNetworkPayloads;
import de.quest.quest.daily.FirstDailyChoiceService;
import de.quest.registry.ModBlocks;
import de.quest.village.LivingVillageNetworkService;
import de.quest.village.LivingVillageNetworkState;
import de.quest.village.NetworkSpecialization;
import de.quest.village.VillageRequestGenerator;
import de.quest.village.VillageRequestOffer;
import de.quest.village.VillageLifeState;
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
public final class VillageNoticeBoardService {
    private static final int TRUSTED_REQUESTS = VillageBondProgression.TRUSTED_REQUESTS;
    private static final int ALLIED_REQUESTS = VillageBondProgression.ALLIED_REQUESTS;
    private static final java.util.Map<ServerPlayer, Long> LAST_REFRESH = new java.util.WeakHashMap<>();

    private VillageNoticeBoardService() {}

    private static boolean validBoard(ServerLevel world, ServerPlayer player, BlockPos pos) {
        return world != null && player != null && pos != null && player.level() == world
                && player.isAlive() && !player.isSpectator() && !player.isCreative()
                && player.blockPosition().distSqr(pos) <= 64
                && world.hasChunkAt(pos) && world.getBlockState(pos).is(ModBlocks.GUILD_NOTICE_POST);
    }

    public static java.util.Optional<VillageNoticeContext> localContext(
            ServerLevel world, ServerPlayer player, BlockPos pos) {
        return localContext(world, player, pos, false);
    }

    static java.util.Optional<VillageNoticeContext> replacementContext(ServerLevel world, ServerPlayer player, BlockPos pos) {
        if (world == null || player == null) return java.util.Optional.empty();
        var data = VillageBondService.data(world, player.getUUID());
        if (!FirstDailyChoiceService.isCompleted(data) || !VillageWelcomeService.isActive(data) && !VillageWelcomeService.isCompleted(data))
            return java.util.Optional.empty();
        return localContext(world, player, pos, true);
    }

    private static java.util.Optional<VillageNoticeContext> localContext(
            ServerLevel world, ServerPlayer player, BlockPos pos, boolean replacement) {
        if (!validBoard(world, player, pos)) return java.util.Optional.empty();
        var board = ShadowsTradeRoadEncounterService.currentVillage(world, pos);
        var visitor = ShadowsTradeRoadEncounterService.currentVillage(world, player.blockPosition());
        if (board == null || visitor == null) return java.util.Optional.empty();
        String dimension = VillageBondService.dimensionKey(world);
        var kingdoms = de.quest.kingdom.LivingKingdomsState.get(world.getServer());
        if (!de.quest.kingdom.KingdomVillageService.sameVillage(kingdoms, dimension,
                board.centerX(), board.centerZ(), visitor.centerX(), visitor.centerZ())
                || lifeStatus(world, board) != VillageLifeState.Status.ACTIVE) return java.util.Optional.empty();
        var key = new VillageLifeState.VillageKey(dimension, board.centerX(), board.centerZ());
        PlayerQuestData data = VillageBondService.data(world, player.getUUID());
        if (!de.quest.reputation.SocialReputationRules.decision(
                de.quest.reputation.SocialReputationRules.ServiceKind.NOTICE_ACCEPT,
                data.socialReputation(), key).allowed()) return java.util.Optional.empty();
        boolean connected = TradeRouteService.isRegisteredDestination(
                world, player.getUUID(), board.centerX(), board.centerZ());
        if (!connected && !VillageWelcomeService.isCompleted(data) && !replacement) return java.util.Optional.empty();
        if (replacement && !VillageWelcomeService.isCompleted(data) && !VillageWelcomeService.hasEnoughVillagers(world, board))
            return java.util.Optional.empty();
        var contact = VillageContactService.resolve(world, player.getUUID(), board);
        if (!contact.accepted()) return java.util.Optional.empty();
        var canonical = kingdoms.byLegacy(key);
        if (canonical.isEmpty()) return java.util.Optional.empty();
        int index = contact.contact().villageIndex();
        if (!canonical.orElseThrow().id().toString().equals(data.getTradeRouteString(
                VillageBondService.villageKey(index, "canonical_id")))) return java.util.Optional.empty();
        var bond = VillageBondService.noticeView(world, player.getUUID(), index, connected);
        if (bond == null) return java.util.Optional.empty();
        return java.util.Optional.of(new VillageNoticeContext(canonical.orElseThrow().id(), index,
                dimension, bond.type(), pos.immutable(), connected, bond));
    }

    static InteractionResult use(ServerLevel world, ServerPlayer player, BlockPos pos) {
        if (!validBoard(world, player, pos)) {
            return InteractionResult.FAIL;
        }
        if (!compatible(player) || !VillageIntroRequestService.recoverBeforeActions(player)) return InteractionResult.CONSUME;
        var context = localContext(world, player, pos).orElse(null);
        if (context != null && context.bond().network() != null && context.connected())
            VillageAtmosphereService.showBoardState(world, pos, context.bond());
        world.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.65f, 1.05f);
        sendJourney(world, player, pos);
        return InteractionResult.CONSUME;
    }

    private static Component deniedRelationship(PlayerQuestData data, ServerPlayer player, VillageLifeState.VillageKey key, String reason) {
        int index=de.quest.reputation.VillageRelationship.exactIndex(data,key);
        Component bond=index<0 ? de.quest.reputation.VillageRelationship.text("bond_unavailable")
                : de.quest.reputation.VillageRelationship.text("bond",VillageBondProgression.snapshot(data,index).level().label());
        var hints=de.quest.reputation.VillageRelationship.project(data,index,key,player.getUUID(),
                de.quest.kingdom.LivingKingdomsState.get(player.level().getServer()),de.quest.reputation.SocialReputationService.enabled());
        Component standing = !hints.enabled() ? de.quest.reputation.VillageRelationship.text("disabled")
                : !hints.available() ? de.quest.reputation.VillageRelationship.text("unavailable")
                : Component.translatable("screen.village-quest.reputation.local_standing",
                    Component.translatable("screen.village-quest.reputation.rank."+de.quest.reputation.SocialReputationRules.rankFor(data.socialReputation().localTrust(key)).name().toLowerCase(Locale.ROOT)),data.socialReputation().localTrust(key));
        return Component.translatable(reason).append("\n").append(bond).append(" · ").append(standing).append("\n")
                .append(de.quest.reputation.VillageRelationship.text("blocked_details"))
                .append(hints.conflict()==2 ? Component.literal("\n").append(de.quest.reputation.VillageRelationship.text("conflict")) : Component.empty());
    }
    private static void sendJourney(ServerLevel world, ServerPlayer player, BlockPos pos) {
        if (!compatible(player) || !VillageIntroRequestService.recoverBeforeActions(player)) return;
        PlayerQuestData data = QuestState.get(world.getServer()).getPlayerData(player.getUUID());
        ShadowsTradeRoadEncounterService.VillageMarker marker =
                ShadowsTradeRoadEncounterService.currentVillage(world, pos);
        if (marker != null) {
            VillageLifeService.observe(world, marker);
            VillageLifeState.Status life = lifeStatus(world, marker);
            if (life != VillageLifeState.Status.ACTIVE) {
                VillageLifeService.ResettlementProgress progress =
                        VillageLifeService.resettlementProgress(world, marker);
                int stage = life == VillageLifeState.Status.ABANDONED
                        ? VillageNetworkPayloads.NoticeJourneyPayload.RESETTLEMENT
                        : VillageNetworkPayloads.NoticeJourneyPayload.RECOVERING;
                int populationTarget = life == VillageLifeState.Status.ABANDONED ? 2 : 3;
                Component detail = Component.translatable(!progress.fullyLoaded()
                        ? "screen.village-quest.village_life.load_village"
                        : life == VillageLifeState.Status.ABANDONED
                        ? "screen.village-quest.village_life.resettlement_detail"
                        : "screen.village-quest.village_life.recovering_detail");
                ServerPlayNetworking.send(player, new VillageNetworkPayloads.NoticeJourneyPayload(
                        pos.getX(), pos.getY(), pos.getZ(), stage,
                        de.quest.kingdom.KingdomVillageService.name(world, marker.centerX(), marker.centerZ(),
                                Component.translatable("text.village-quest.village_life.settlement", marker.centerX(), marker.centerZ())),
                        Component.translatable(life == VillageLifeState.Status.ABANDONED
                                ? "screen.village-quest.village_life.resettlement_title"
                                : "screen.village-quest.village_life.recovering_title"),
                        detail, Math.min(populationTarget, progress.adults()), populationTarget,
                        Math.min(populationTarget, progress.beds()), populationTarget, false, ItemStack.EMPTY, 0, null, false));
                return;
            }
        }
        if (marker != null) {
            var access = de.quest.reputation.SocialReputationRules.decision(
                    de.quest.reputation.SocialReputationRules.ServiceKind.NOTICE_ACCEPT,
                    data.socialReputation(), new VillageLifeState.VillageKey(VillageBondService.dimensionKey(world),
                            marker.centerX(), marker.centerZ()));
            if (!access.allowed()) {
                ServerPlayNetworking.send(player, new VillageNetworkPayloads.NoticeJourneyPayload(
                        pos.getX(), pos.getY(), pos.getZ(), VillageNetworkPayloads.NoticeJourneyPayload.INTRO,
                        Component.translatable("screen.village-quest.notice_journey.village"),
                        Component.translatable("screen.village-quest.notice_board.title"),
                        deniedRelationship(data,player,new VillageLifeState.VillageKey(VillageBondService.dimensionKey(world),marker.centerX(),marker.centerZ()),access.reasonKey()), 0, 0, 0, 0, false, ItemStack.EMPTY, 0, null, false));
                return;
            }
        }
        var context = localContext(world, player, pos).orElse(null);
        VillageBondService.VillageBondView view = context == null ? null : context.bond();
        VillageNetworkPayloads.NoticeBoardPayload requests = context == null ? null : build(world, player, pos, context);
        if (requests == null) {
            var replacement = replacementContext(world,player,pos).orElse(null);
            if (replacement != null && VillageIntroRequestService.replacementEpisode(player,pos) != null)
                requests = build(world,player,pos,replacement);
        }
        GuildTownService.NoticePostStoryContext interaction = GuildTownService.createNoticePostStoryContext(world, player);
        GuildTownService.NoticePostStoryResolution resolution = use(interaction);
        int stage = VillageNetworkPayloads.NoticeJourneyPayload.INTRO;
        Component village = view == null ? Component.translatable("screen.village-quest.notice_journey.village")
                : de.quest.kingdom.KingdomVillageService.identityLabel(world, view.x(), view.z(), view.type().label());
        if (view == null && resolution != null) village = marker == null ? resolution.story().villageType().label()
                : de.quest.kingdom.KingdomVillageService.identityLabel(world, marker.centerX(), marker.centerZ(), resolution.story().villageType().label());
        Component title = Component.translatable("screen.village-quest.notice_journey.welcome");
        Component detail;
        int first = 0, firstTarget = 0, second = 0, secondTarget = 0, deliveryCount = 0;
        boolean sharedTable = false;
        ItemStack delivery = ItemStack.EMPTY;
        if (!VillageWelcomeService.isCompleted(data) || resolution == null) {
            GuildNoticePostTeaser.Stage teaser = GuildNoticePostTeaser.classify(
                    marker != null, TradeRouteService.isNearPlayerYard(world, player.getUUID(), pos, 16),
                    FirstDailyChoiceService.canChoose(data), FirstDailyChoiceService.isActive(data),
                    FirstDailyChoiceService.isCompleted(data), VillageWelcomeService.isActive(data),
                    VillageWelcomeService.isCompleted(data), VillageWelcomeService.isCurrentWelcomeVillage(data, marker));
            if (teaser == GuildNoticePostTeaser.Stage.FIRST_FAVOR_AVAILABLE
                    || teaser == GuildNoticePostTeaser.Stage.FIRST_FAVOR_ACTIVE) {
                stage = VillageNetworkPayloads.NoticeJourneyPayload.QUESTMASTER;
            }
            String key = switch (teaser) {
                case FIRST_FAVOR_AVAILABLE -> "first_favor";
                case FIRST_FAVOR_ACTIVE -> "first_favor_active";
                case MEET_VILLAGERS -> "meet_villagers";
                case GREETING_PROGRESS -> "greeting_progress";
                case WELCOME_ELSEWHERE -> "welcome_elsewhere";
                case CONNECT_VILLAGE -> "connect_village";
                case HOMESTEAD -> "homestead";
                case UNKNOWN -> "unknown";
            };
            detail = switch (teaser) {
                case MEET_VILLAGERS -> Component.translatable("message.village-quest.guild_notice_post.teaser." + key,
                        VillageWelcomeService.GREETING_TARGET);
                case GREETING_PROGRESS -> Component.translatable("message.village-quest.guild_notice_post.teaser." + key,
                        VillageWelcomeService.greetingCount(data), VillageWelcomeService.GREETING_TARGET);
                default -> Component.translatable("message.village-quest.guild_notice_post.teaser." + key);
            };
            if (teaser == GuildNoticePostTeaser.Stage.GREETING_PROGRESS
                    || teaser == GuildNoticePostTeaser.Stage.MEET_VILLAGERS) {
                first = VillageWelcomeService.greetingCount(data);
                firstTarget = VillageWelcomeService.GREETING_TARGET;
            }
        } else {
            GuildTownService.NoticePostUiState story = GuildTownService.noticePostUiState(data, resolution);
            title = story.title();
            detail = story.detail();
            first = story.first(); firstTarget = story.firstTarget();
            second = story.second(); secondTarget = story.secondTarget();
            delivery = story.delivery(); deliveryCount = story.deliveryCount();
            sharedTable = story.sharedTable();
            stage = switch (story.state()) {
                case "AVAILABLE" -> VillageNetworkPayloads.NoticeJourneyPayload.AVAILABLE;
                case "REMEMBERED" -> VillageNetworkPayloads.NoticeJourneyPayload.REMEMBERED;
                case "READY" -> VillageNetworkPayloads.NoticeJourneyPayload.READY;
                case "PAUSED" -> VillageNetworkPayloads.NoticeJourneyPayload.PAUSED;
                default -> sharedTable && first >= firstTarget && second == 0
                        ? VillageNetworkPayloads.NoticeJourneyPayload.CHOOSE
                        : VillageNetworkPayloads.NoticeJourneyPayload.ACTIVE;
            };
            if ((stage == VillageNetworkPayloads.NoticeJourneyPayload.ACTIVE
                    || stage == VillageNetworkPayloads.NoticeJourneyPayload.CHOOSE
                    || stage == VillageNetworkPayloads.NoticeJourneyPayload.READY)
                    && GuildTownProgress.activeStoryVillage(data) != resolution.villageIndex()) {
                stage = VillageNetworkPayloads.NoticeJourneyPayload.AWAY;
                detail = Component.translatable("message.village-quest.guild_town.story.return_to_village");
            } else if (stage == VillageNetworkPayloads.NoticeJourneyPayload.CHOOSE) {
                detail = Component.translatable("screen.village-quest.notice_journey.choose");
            } else if (stage == VillageNetworkPayloads.NoticeJourneyPayload.READY) {
                detail = Component.translatable("screen.village-quest.notice_journey.ready");
            } else if (stage == VillageNetworkPayloads.NoticeJourneyPayload.AVAILABLE) {
                detail = Component.translatable("screen.village-quest.notice_journey.available");
            } else if (stage == VillageNetworkPayloads.NoticeJourneyPayload.REMEMBERED) {
                detail = Component.translatable("message.village-quest.guild_town.story.remembered", title);
            }
            if (stage == VillageNetworkPayloads.NoticeJourneyPayload.REMEMBERED) {
                GuildTownAfterstoryService.View followUp =
                        GuildTownAfterstoryService.view(world, player, resolution,
                                context != null && context.connected() ? view : null);
                if (followUp != null) {
                    title = followUp.title();
                    detail = followUp.detail();
                    stage = switch (followUp.state()) {
                        case NOT_STARTED -> VillageNetworkPayloads.NoticeJourneyPayload.AFTERSTORY_AVAILABLE;
                        case ACTIVE -> followUp.ready()
                                ? VillageNetworkPayloads.NoticeJourneyPayload.AFTERSTORY_READY
                                : VillageNetworkPayloads.NoticeJourneyPayload.AFTERSTORY_ACTIVE;
                        case COMPLETE -> VillageNetworkPayloads.NoticeJourneyPayload.AFTERSTORY_COMPLETE;
                    };
                    if (followUp.state() == de.quest.guildtown.GuildTownAfterstoryLedger.State.ACTIVE) {
                        first = followUp.inventory();
                        firstTarget = followUp.amount();
                    }
                    delivery = followUp.delivery();
                    deliveryCount = followUp.amount();
                }
            }
        }
        ServerPlayNetworking.send(player, new VillageNetworkPayloads.NoticeJourneyPayload(
                pos.getX(), pos.getY(), pos.getZ(), stage, village, title, detail,
                first, firstTarget, second, secondTarget, sharedTable, delivery, deliveryCount, requests, false));
    }

    static void handleJourneyAction(ServerPlayer player, VillageNetworkPayloads.NoticeJourneyActionPayload payload) {
        if (player == null || payload == null || !(player.level() instanceof ServerLevel world)) return;
        BlockPos pos = new BlockPos(payload.worldX(), payload.worldY(), payload.worldZ());
        if (!validBoard(world, player, pos)) {
            invalid(player);
            return;
        }
        if (!compatible(player) || !VillageIntroRequestService.recoverBeforeActions(player)) return;
        ShadowsTradeRoadEncounterService.VillageMarker marker =
                ShadowsTradeRoadEncounterService.currentVillage(world, pos);
        if (marker != null && lifeStatus(world, marker) != VillageLifeState.Status.ACTIVE) {
            if (payload.action() == VillageNetworkPayloads.NoticeJourneyActionPayload.DISPATCH) {
                RegionalDispatchService.open(world, player, pos);
                return;
            }
            if (payload.action() != VillageNetworkPayloads.NoticeJourneyActionPayload.REFRESH) {
                player.sendSystemMessage(Component.translatable(
                        "message.village-quest.village_life.board_suspended")
                        .withStyle(ChatFormatting.GOLD), false);
            }
            sendJourney(world, player, pos);
            return;
        }
        switch (payload.action()) {
            case VillageNetworkPayloads.NoticeJourneyActionPayload.ACCEPT -> GuildTownService.acceptCurrentStory(world, player);
            case VillageNetworkPayloads.NoticeJourneyActionPayload.RESERVE -> GuildTownService.chooseSharedTable(world, player, "reserve");
            case VillageNetworkPayloads.NoticeJourneyActionPayload.SHARE -> GuildTownService.chooseSharedTable(world, player, "share");
            case VillageNetworkPayloads.NoticeJourneyActionPayload.DELIVER -> GuildTownService.deliverStory(world, player);
            case VillageNetworkPayloads.NoticeJourneyActionPayload.RESUME -> GuildTownService.pauseStory(world, player, false);
            case VillageNetworkPayloads.NoticeJourneyActionPayload.ACCEPT_AFTERSTORY ->
                    GuildTownAfterstoryService.handle(world, player, false);
            case VillageNetworkPayloads.NoticeJourneyActionPayload.DELIVER_AFTERSTORY ->
                    GuildTownAfterstoryService.handle(world, player, true);
            case VillageNetworkPayloads.NoticeJourneyActionPayload.DISPATCH -> {
                RegionalDispatchService.open(world, player, pos);
                return;
            }
            case VillageNetworkPayloads.NoticeJourneyActionPayload.REFRESH -> { }
            default -> { return; }
        }
        sendJourney(world, player, pos);
    }

    static GuildTownService.NoticePostStoryResolution use(
            GuildTownService.NoticePostStoryContext interaction) {
        return GuildTownService.resolveNoticePostStory(interaction);
    }

    static void handleAction(ServerPlayer player, VillageNetworkPayloads.NoticeBoardActionPayload payload) {
        if (player == null || payload == null || (payload.action() < 1 || payload.action() > 6)
                || !(player.level() instanceof ServerLevel world)) {
            return;
        }
        BlockPos pos = new BlockPos(payload.worldX(), payload.worldY(), payload.worldZ());
        if (!validBoard(world, player, pos)) {
            invalid(player);
            return;
        }
        if (!VillageIntroRequestService.recoverBeforeActions(player)) return;
        if (payload.action() >= VillageNetworkPayloads.NoticeBoardActionPayload.INTRO_ACCEPT) {
            if (!VillageIntroRequestService.handleBoardAction(player,pos,payload)) invalid(player);
            if (!VillageIntroRequestService.blocksActions(world,player.getUUID())) {
                var refreshed=localContext(world,player,pos).orElse(null);
                if (payload.action()!=VillageNetworkPayloads.NoticeBoardActionPayload.INTRO_REPLACE && refreshed!=null)
                    send(world,player,pos,refreshed);
                else sendJourney(world,player,pos);
            }
            return;
        }
        var context = localContext(world, player, pos).orElse(null);
        if (context == null && payload.action() == VillageNetworkPayloads.NoticeBoardActionPayload.ACTION_REFRESH) {
            sendJourney(world,player,pos); return;
        }
        if (context == null) {
            var marker = ShadowsTradeRoadEncounterService.currentVillage(world, pos);
            if (marker != null) de.quest.reputation.ReputationAccessService.require(world, player,
                    de.quest.reputation.SocialReputationRules.ServiceKind.NOTICE_ACCEPT,
                    new VillageLifeState.VillageKey(VillageBondService.dimensionKey(world), marker.centerX(), marker.centerZ()));
            invalid(player);
            sendJourney(world, player, pos);
            return;
        }
        var view = context.bond();
        if (lifeStatus(world, view.x(), view.z()) != VillageLifeState.Status.ACTIVE) {
            player.sendSystemMessage(Component.translatable(
                    "message.village-quest.village_life.board_suspended")
                    .withStyle(ChatFormatting.GOLD), false);
            sendJourney(world, player, pos);
            return;
        }
        if (payload.action() == VillageNetworkPayloads.NoticeBoardActionPayload.ACTION_REFRESH) {
            long now = world.getGameTime();
            long last = LAST_REFRESH.getOrDefault(player, now - 20);
            if (now >= last && now - last < 20) return;
            LAST_REFRESH.put(player, now);
            send(world, player, pos, context);
            return;
        }
        // Starter cards use their explicit acceptance/transaction path, never a forged ordinary delivery.
        if (VillageIntroRequestService.boardData(player,pos,context) != null) { invalid(player); return; }
        fulfill(world, player, context, payload.requestId());
        var refreshed = localContext(world, player, pos).orElse(null);
        if (refreshed != null) {
            send(world, player, pos, refreshed);
        }
    }

    private static void invalid(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable("message.village-quest.village_bond.notice_invalid")
                .withStyle(ChatFormatting.RED), false);
    }

    private static VillageLifeState.Status lifeStatus(ServerLevel world,
            ShadowsTradeRoadEncounterService.VillageMarker marker) {
        return lifeStatus(world, marker.centerX(), marker.centerZ());
    }

    private static VillageLifeState.Status lifeStatus(ServerLevel world, int x, int z) {
        return VillageLifeState.get(world.getServer()).status(new VillageLifeState.VillageKey(
                world.dimension().identifier().toString(), x, z));
    }

    private static void send(ServerLevel world, ServerPlayer player, BlockPos pos,
                             VillageNoticeContext context) {
        if (!compatible(player)) return;
        ServerPlayNetworking.send(player, build(world, player, pos, context));
    }
    static boolean compatible(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player,VillageNetworkPayloads.NoticeBoardPayload.ID)
                && ServerPlayNetworking.canSend(player,VillageNetworkPayloads.NoticeJourneyPayload.ID)) return true;
        player.sendSystemMessage(Component.translatableWithFallback("message.village-quest.intro.version_mismatch",
                "Village Quest versions differ. Install the same version on the client and server."),false);
        return false;
    }

    private static VillageNetworkPayloads.NoticeBoardPayload build(ServerLevel world, ServerPlayer player, BlockPos pos,
                             VillageNoticeContext context) {
        var data = VillageBondService.data(world,player.getUUID());
        var view = context.bond();
        VillageRequestType request = view.request();
        boolean availableToday = VillageBondService.requestAvailable(world, player.getUUID(), view.index());
        List<VillageRequestOffer> offers = offers(world, player.getUUID(), context);
        VillageRequestOffer primary = offers.isEmpty()
                ? new VillageRequestOffer(request.id(), request, request.amount(), request.reward(),
                context.connected() ? LivingVillageNetworkService.deliverySupport(view.network().need(), request) : 0,
                context.connected() && view.network().need().matches(request))
                : offers.getFirst();
        int carried = VillageBondService.count(player, primary.request().item());
        var intro = VillageIntroRequestService.boardData(player,pos,context);
        var introOffers = intro == null || intro.replacement() ? List.<VillageNetworkPayloads.NoticeBoardOfferData>of()
                : VillageIntroRequestService.boardOffers(player,context);
        boolean introMode = intro != null;
        var introPrimary = intro != null && intro.replacement() ? new VillageNetworkPayloads.NoticeBoardOfferData(-1,
                Component.translatable("screen.village-quest.intro.replace"),ItemStack.EMPTY,0,0,0,0,false,false)
                : introOffers.isEmpty() ? null : introOffers.getFirst();
        Component nextLevel;
        Component nextPerk;
        int nextThreshold;
        if (view.level() == VillageBondLevel.KNOWN) {
            nextLevel = VillageBondLevel.TRUSTED.label();
            nextPerk = Component.translatable("screen.village-quest.notice_board.perk.trusted",
                    VillageBondProgression.snapshot(VillageBondService.data(world,player.getUUID()),view.index()).qualifiedTrades());
            nextThreshold = TRUSTED_REQUESTS;
            nextPerk=de.quest.quest.QuestGuidanceService.localTrust(VillageBondService.data(world,player.getUUID()),
                    view.index(),de.quest.kingdom.LivingKingdomsState.get(world.getServer())).action();
        } else if (view.level() == VillageBondLevel.TRUSTED) {
            nextLevel = VillageBondLevel.ALLIED.label();
            nextPerk = Component.translatable("screen.village-quest.notice_board.perk.allied");
            nextThreshold = ALLIED_REQUESTS;
        } else {
            nextLevel = Component.translatable("screen.village-quest.notice_board.max_level");
            nextPerk = Component.translatable("screen.village-quest.notice_board.perk.complete");
            nextThreshold = 0;
        }
        return new VillageNetworkPayloads.NoticeBoardPayload(
                pos.getX(), pos.getY(), pos.getZ(),
                de.quest.kingdom.KingdomVillageService.identityLabel(world, view.x(), view.z(), view.type().label()), view.level().label(),
                context.connected() ? view.network().condition().label() : Component.empty(),
                context.connected() ? view.network().need().label() : Component.empty(),
                context.connected() ? view.network().support() : -1,
                introPrimary != null ? introPrimary.title() : primary.request().title(),
                introPrimary != null ? introPrimary.stack() : new ItemStack(primary.request().item()),
                introPrimary != null ? introPrimary.requiredAmount() : primary.amount(),
                introPrimary != null ? introPrimary.inventoryAmount() : carried,
                introPrimary != null ? introPrimary.reward() : primary.reward(), CurrencyService.getBalance(world, player.getUUID()), view.completions(),
                view.level().id(), nextThreshold, nextLevel, nextPerk, introMode || availableToday,
                !introMode && availableToday && carried >= primary.amount(), introMode ? introOffers : offers.stream().map(offer -> {
                    int offerCarried = VillageBondService.count(player, offer.request().item());
                    return new VillageNetworkPayloads.NoticeBoardOfferData(offer.id(), offer.request().title(),
                            new ItemStack(offer.request().item()), offer.amount(), offerCarried, offer.reward(),
                            offer.support(), offer.primaryNeed(), availableToday && offerCarried >= offer.amount());
                }).toList(), Component.translatable("text.village-quest.adventure_profile."
                        + VillageQuestServerConfig.get().adventureProfile().name().toLowerCase(Locale.ROOT)),
                availableToday ? Component.empty() : Component.translatable("screen.village-quest.notice_board.next_request",
                        de.quest.util.QuestCalendarService.remaining(false)), intro,
                data.socialReputation().localTrust(new VillageLifeState.VillageKey(context.dimension(),view.x(),view.z())),
                de.quest.reputation.SocialReputationService.enabled() && data.socialReputation().activeCase()!=null
                        && data.socialReputation().activeCase().affected().contains(new VillageLifeState.VillageKey(context.dimension(),view.x(),view.z())),
                de.quest.reputation.VillageRelationship.project(data,view.index(),new VillageLifeState.VillageKey(context.dimension(),view.x(),view.z()),player.getUUID(),
                        de.quest.kingdom.LivingKingdomsState.get(world.getServer()),de.quest.reputation.SocialReputationService.enabled()));
    }

    private static List<VillageRequestOffer> offers(ServerLevel world, UUID playerId,
                                                     VillageNoticeContext context) {
        var view = context.bond();
        PlayerQuestData playerData = VillageBondService.data(world, playerId);
        int lastId = playerData.getTradeRouteInt(VillageBondService.villageKey(view.index(), "last_request")) - 1;
        VillageRequestType last = lastId < 0 ? null : VillageRequestType.byId(lastId, view.type(), view.index());
        if (!context.connected()) {
            return VillageRequestGenerator.generate(view.type(), view.network() == null ? null : view.network().need(),
                    view.request(), last, view.level(), view.network() == null ? null : view.network().condition(),
                    VillageQuestServerConfig.get().adventureProfile(), view.index() * 31 + view.completions() * 17,
                    0, 1.0).stream().map(offer -> new VillageRequestOffer(offer.id(), offer.request(),
                            offer.amount(), offer.reward(), 0, false)).toList();
        }
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
                                   VillageNoticeContext context, int requestId) {
        if (VillageIntroRequestService.blocksActions(world,player.getUUID())) return false;
        var view = context.bond();
        if (!de.quest.reputation.ReputationAccessService.require(world, player,
                de.quest.reputation.SocialReputationRules.ServiceKind.NOTICE_ACCEPT,
                new VillageLifeState.VillageKey(world.dimension().identifier().toString(), view.x(), view.z()))) return false;
        VillageRequestOffer offer = offers(world, player.getUUID(), context).stream()
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
        playerData.setTradeRouteInt(VillageBondService.villageKey(view.index(), "completions"), completions);
        VillageBondLevel level = VillageBondProgression.snapshot(playerData,view.index()).level();
        playerData.setTradeRouteInt(VillageBondService.villageKey(view.index(), "level"), level.id() + 1);
        playerData.setTradeRouteInt(VillageBondService.villageKey(view.index(), "request_day"),
                VillageBondService.currentRequestDay());
        playerData.setTradeRouteInt(VillageBondService.villageKey(view.index(), "last_request"), request.id() + 1);
        playerData.setTradeRouteInt(VillageBondService.villageKey(view.index(), "request"),
                VillageRequestType.nextAfter(view.type(), request).id() + 1);
        CurrencyService.addBalance(world, player.getUUID(), offer.reward());
        LivingVillageNetworkState.SupportResult result = context.connected()
                ? LivingVillageNetworkService.recordNoticeDelivery(world, player.getUUID(), view.index(),
                    view.x(), view.z(), view.type(), request, offer.support()) : null;
        if (context.connected()) VillageGuildService.recordDelivery(world, player.getUUID(), offer.primaryNeed());
        var socialVillage = new de.quest.village.VillageLifeState.VillageKey(world.dimension().identifier().toString(), view.x(), view.z());
        de.quest.reputation.SocialReputationService.recordNamedBenefit(world.getServer(), player.getUUID(),
                de.quest.reputation.SocialReputationRules.BenefitKind.NOTICE,
                socialVillage + ":" + completions, java.util.List.of(socialVillage));
        QuestState.get(world.getServer()).setDirty();
        player.sendSystemMessage(Component.translatable("message.village-quest.village_bond.request_complete",
                request.title(), level.label(), CurrencyService.formatDelta(offer.reward()))
                .withStyle(ChatFormatting.GREEN), false);
        if (result != null && result.village() != null) {
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
