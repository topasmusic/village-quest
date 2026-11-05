package de.quest.shrine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import de.quest.data.QuestState;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Native intro attachment/save bridge used by validated, versioned notice-board actions. */
public final class VillageIntroRequestService {
    // Preserve unknown attachment shapes rather than silently interpreting them as missing BEFORE.
    static final Codec<Tag> PROOF_CODEC = Codec.PASSTHROUGH.xmap(
            value -> value.convert(NbtOps.INSTANCE).getValue().copy(), value -> new Dynamic<>(NbtOps.INSTANCE, value.copy()));
    private static final class Attachment {
        static final AttachmentType<Tag> TYPE = AttachmentRegistry.create(Identifier.parse(VillageIntroSaveBarrier.PLAYER_PROOF),
                builder -> builder.persistent(PROOF_CODEC).copyOnDeath());
    }
    private VillageIntroRequestService() {}
    /** Freeze automatic finances and claims until the terminal disk acknowledgement, including after reload. */
    public static boolean blocksActions(net.minecraft.server.level.ServerLevel world, UUID owner) {
        if (world == null || owner == null) return true;
        if (de.quest.data.PersonalResetService.blocksActions(world,owner)) return true;
        if (!QuestState.get(world.getServer()).storyLanesWritable()) return true;
        var raw = QuestState.get(world.getServer()).introTransaction(owner);
        if (raw == null) return false;
        var tx = VillageIntroTransaction.fromNbt(raw, world.registryAccess()).orElse(null);
        return tx == null || !owner.equals(tx.actor()) || !tx.acknowledged();
    }
    /** Must run before join rewards, market reconciliation or other player mutations. */
    public static boolean recoverBeforeActions(ServerPlayer player) {
        if (!thread(player)) return false;
        if (!de.quest.data.PersonalResetService.recoverBeforeActions(player)) return false;
        if (!QuestState.get(player.level().getServer()).storyLanesWritable()) {
            if (player.connection != null) player.connection.disconnect(
                    net.minecraft.network.chat.Component.translatable("message.village-quest.story.data_unsupported"));
            return false;
        }
        try {
            if (recover(player) != VillageIntroDeliveryCoordinator.Result.HELD) return true;
        } catch (RuntimeException unknown) {
            // Preserve both documents and stop this player's mutations. Never guess a compensation.
        }
        hold(player); return false;
    }
    private static void hold(ServerPlayer player) {
        if (player.connection != null) player.connection.disconnect(
                net.minecraft.network.chat.Component.translatable("message.village-quest.intro.recovery_held"));
    }
    private static void acknowledge(QuestState state, ServerPlayer player) {
        var raw = state.introTransaction(player.getUUID());
        var tx = VillageIntroTransaction.fromNbt(raw, player.registryAccess()).orElseThrow();
        var ack = tx.acknowledgedImage();
        if (!state.compareIntroTransaction(player.getUUID(), raw, ack) || !state.confirmIntroTerminal(player.getUUID(), ack))
            throw new IllegalStateException("Intro acknowledgement changed");
    }
    private static boolean otherFinancesReady(QuestState state, ServerPlayer player) {
        var data = state.getPlayerData(player.getUUID());
        return !data.marketWallet().readOnly() && data.vanillaRewards().writable()
                && data.marketWallet().marker().filter(m -> m.stage() == de.quest.kingdom.MarketTransferMarker.Stage.BEFORE
                    || m.stage() == de.quest.kingdom.MarketTransferMarker.Stage.AFTER).isEmpty()
                && !de.quest.kingdom.LivingKingdomsState.get(player.level().getServer()).readOnly()
                && !de.quest.kingdom.LivingKingdomsState.get(player.level().getServer()).markets().readOnly()
                && de.quest.kingdom.LivingKingdomsState.get(player.level().getServer()).markets().pending(player.getUUID()).isEmpty();
    }
    /** Captured before a harvest. Delayed verification cannot credit a newly accepted episode. */
    public record HarvestTicket(UUID actor, int generation, UUID episode, UUID village, UUID receipt) {}

    public static HarvestTicket harvestTicket(ServerPlayer player) {
        if (!thread(player) || !player.isAlive() || player.isCreative() || player.isSpectator()) return null;
        var state = QuestState.get(player.level().getServer());
        if (blocksActions(player.level(),player.getUUID())) return null;
        var data = state.getPlayerData(player.getUUID());
        UUID village;
        try { village = UUID.fromString(data.getTradeRouteString(VillageIntroRequestProgress.PREFIX + "village")); }
        catch (IllegalArgumentException absent) { return null; }
        int index = index(state, player.getUUID(), village);
        if (index < 0) return null;
        var bond = VillageBondProgression.snapshot(data, index);
        var progress = VillageIntroRequestProgress.open(data, village, bond.actualCompletions(), bond.level() != VillageBondLevel.KNOWN);
        if (!progress.writable() || progress.active() != VillageIntroRequestType.HARVEST_HELP
                || !progress.offers(bond.actualCompletions(), bond.level() != VillageBondLevel.KNOWN).contains(VillageIntroRequestType.HARVEST_HELP)) return null;
        return new HarvestTicket(player.getUUID(), data.progressGeneration(), progress.episode(), village, UUID.randomUUID());
    }

    /** Only the server harvest tracker calls this after a confirmed mature-crop transition. */
    public static boolean onVerifiedHarvest(net.minecraft.server.level.ServerLevel world, ServerPlayer player,
            BlockPos pos, net.minecraft.world.level.block.state.BlockState original, HarvestTicket ticket) {
        if (ticket == null || world == null || pos == null || player == null || player.level() != world
                || !thread(player) || !player.isAlive() || player.isCreative() || player.isSpectator()
                || !ticket.actor().equals(player.getUUID()) || !world.hasChunkAt(pos)
                || player.blockPosition().distSqr(pos) > 64 || original == null
                || !(original.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop) || !crop.isMaxAge(original)) return false;
        var block = original.getBlock();
        if (block != net.minecraft.world.level.block.Blocks.WHEAT && block != net.minecraft.world.level.block.Blocks.CARROTS
                && block != net.minecraft.world.level.block.Blocks.POTATOES && block != net.minecraft.world.level.block.Blocks.BEETROOTS) return false;
        var current = world.getBlockState(pos);
        if (current.getBlock() == block && crop.getAge(current) >= crop.getAge(original)) return false;
        var field = de.quest.content.story.ShadowsTradeRoadEncounterService.currentVillage(world, pos);
        var visitor = de.quest.content.story.ShadowsTradeRoadEncounterService.currentVillage(world, player.blockPosition());
        if (field == null || visitor == null || pos.getX() < field.minX() || pos.getX() > field.maxX()
                || pos.getZ() < field.minZ() || pos.getZ() > field.maxZ()) return false;
        String dimension = VillageBondService.dimensionKey(world);
        var kingdoms = de.quest.kingdom.LivingKingdomsState.get(world.getServer());
        var key = new de.quest.village.VillageLifeState.VillageKey(dimension, field.centerX(), field.centerZ());
        if (kingdoms.readOnly() || !kingdoms.byLegacy(key).map(v -> v.id().equals(ticket.village())).orElse(false)
                || !de.quest.kingdom.KingdomVillageService.sameVillage(kingdoms, dimension,
                    field.centerX(), field.centerZ(), visitor.centerX(), visitor.centerZ())
                || !de.quest.kingdom.KingdomVillageService.withinConfirmedArea(de.quest.kingdom.KingdomVillageService.confirmedZones(world, field), pos)
                || de.quest.village.VillageLifeState.get(world.getServer()).status(key) != de.quest.village.VillageLifeState.Status.ACTIVE) return false;
        var state = QuestState.get(world.getServer()); var data = state.getPlayerData(player.getUUID());
        if (!de.quest.reputation.SocialReputationRules.decision(de.quest.reputation.SocialReputationRules.ServiceKind.NOTICE_ACCEPT,
                data.socialReputation(), key).allowed()) return false;
        var now = harvestTicket(player);
        if (now == null || now.generation() != ticket.generation() || !now.episode().equals(ticket.episode())
                || !now.village().equals(ticket.village())) return false;
        int index = index(state, player.getUUID(), ticket.village());
        var bond = VillageBondProgression.snapshot(data, index);
        var progress = VillageIntroRequestProgress.open(data, ticket.village(), bond.actualCompletions(), bond.level() != VillageBondLevel.KNOWN);
        if (!progress.harvest(ticket.receipt())) return false;
        state.setDirty(); return true;
    }
    private static VillageIntroJournalPort.PlayerProof proof(ServerPlayer actor) {
        var target = (AttachmentTarget) actor;
        return new VillageIntroJournalPort.PlayerProof() {
            public Tag get() { var value = target.getAttached(Attachment.TYPE); return value == null ? null : value.copy(); }
            public void set(CompoundTag tag) { target.setAttached(Attachment.TYPE, tag.copy()); }
        };
    }
    /** Call at initialization before any Player NBT is loaded, when the complete integration is enabled. */
    public static void initializeAttachment() { var type = Attachment.TYPE; }
    private static boolean thread(ServerPlayer player) {
        return player != null && !player.isRemoved() && player.level().getServer().isSameThread()
                && !player.level().getServer().isStopped() && !player.level().getServer().isShutdown();
    }
    private static int index(QuestState state, UUID actor, UUID village) {
        var data = state.getPlayerData(actor);
        for (int i = 0; i < VillageBondService.historicalVillageCount(data); i++)
            if (village.toString().equals(data.getTradeRouteString(VillageBondService.villageKey(i, "canonical_id")))) return i;
        return -1;
    }

    /** Physical, persisted population evidence only. Unloaded or socially locked villages are not lost. */
    public static boolean lostContact(de.quest.data.PlayerQuestData data, int index,
            de.quest.kingdom.LivingKingdomsState kingdoms, de.quest.village.VillageLifeState life) {
        if (data == null || kingdoms == null || life == null || kingdoms.readOnly() || index < 0
                || index >= VillageBondService.historicalVillageCount(data)) return false;
        try {
            UUID id = UUID.fromString(data.getTradeRouteString(VillageBondService.villageKey(index, "canonical_id")));
            var village = kingdoms.village(id).orElse(null);
            if (village == null) return false;
            String dimension = data.getTradeRouteString(VillageBondService.villageKey(index, "dimension"));
            if (dimension.isEmpty()) dimension = "minecraft:overworld";
            var stored = new de.quest.village.VillageLifeState.VillageKey(dimension,
                    data.getTradeRouteInt(VillageBondService.villageKey(index, "x")),
                    data.getTradeRouteInt(VillageBondService.villageKey(index, "z")));
            if (!kingdoms.byLegacy(stored).map(v -> v.id().equals(id)).orElse(false)) return false;
            var key = village.legacyKey();
            return life.status(key) == de.quest.village.VillageLifeState.Status.ABANDONED && life.abandonmentCycle(key) > 0;
        } catch (IllegalArgumentException unknown) { return false; }
    }

    /** Preview token for an explicit replacement action, never an automatic village switch. */
    static UUID replacementEpisode(ServerPlayer player, BlockPos board) {
        if (!thread(player) || !recoverBeforeActions(player)) return null;
        var context = VillageNoticeBoardService.replacementContext(player.level(), player, board).orElse(null);
        if (context == null) return null;
        var state = QuestState.get(player.level().getServer()); var data = state.getPlayerData(player.getUUID());
        int old = data.getTradeRouteInt("guild_intro.welcome_village") - 1;
        if (!data.validProgressGeneration() || old == context.index()
                || !lostContact(data, old, de.quest.kingdom.LivingKingdomsState.get(player.level().getServer()),
                        de.quest.village.VillageLifeState.get(player.level().getServer()))) return null;
        UUID village = UUID.fromString(data.getTradeRouteString(VillageBondService.villageKey(old, "canonical_id")));
        var bond = VillageBondProgression.snapshot(data, old);
        var progress = VillageIntroRequestProgress.open(data, village, bond.actualCompletions(), bond.level() != VillageBondLevel.KNOWN);
        if (!progress.writable() || !village.equals(progress.village())) return null;
        state.setDirty(); return progress.episode();
    }

    static boolean replaceContact(ServerPlayer player, BlockPos board, int generation, UUID episode) {
        if (!thread(player) || episode == null) return false;
        var state = QuestState.get(player.level().getServer());
        if (generation != state.getPlayerData(player.getUUID()).progressGeneration()
                || !episode.equals(replacementEpisode(player, board))) return false;
        var context = VillageNoticeBoardService.replacementContext(player.level(), player, board).orElse(null);
        if (context == null) return false;
        var current = state.getPlayerData(player.getUUID()); int old = current.getTradeRouteInt("guild_intro.welcome_village") - 1;
        var next = state.copyPersonalProgress(player.getUUID());
        UUID previous = UUID.fromString(next.getTradeRouteString(VillageIntroRequestProgress.PREFIX + "village"));
        var source = VillageBondProgression.snapshot(next, old); var destination = VillageBondProgression.snapshot(next, context.index());
        var progress = VillageIntroRequestProgress.open(next, previous, source.actualCompletions(), source.level() != VillageBondLevel.KNOWN);
        if (!episode.equals(progress.episode()) || !progress.rebind(context.villageId(), destination.actualCompletions(), destination.level() != VillageBondLevel.KNOWN)
                || !VillageWelcomeService.rebind(next, old, context.index())) return false;
        state.replacePersonalProgress(player.getUUID(), next);
        de.quest.quest.QuestHarvestTracker.clearPlayer(player.getUUID());
        return true;
    }
    static VillageIntroDeliveryCoordinator.Result recover(ServerPlayer player) {
        if (!thread(player)) return VillageIntroDeliveryCoordinator.Result.HELD;
        var state = QuestState.get(player.level().getServer()); var raw = state.introTransaction(player.getUUID());
        var attachment = proof(player);
        if (raw == null && attachment.get() == null) return VillageIntroDeliveryCoordinator.Result.CANCELLED;
        var tx = VillageIntroTransaction.fromNbt(raw, player.registryAccess()).orElse(null);
        if (tx == null || !tx.actor().equals(player.getUUID())) return VillageIntroDeliveryCoordinator.Result.HELD;
        if (tx.stage() != VillageIntroDeliveryCoordinator.Stage.COMMITTED && tx.stage() != VillageIntroDeliveryCoordinator.Stage.CANCELLED
                && !otherFinancesReady(state,player)) return VillageIntroDeliveryCoordinator.Result.HELD;
        var port = new VillageIntroJournalPort(state, player.getUUID(), index(state, player.getUUID(), tx.village()),
                player.getInventory(), player.registryAccess(), attachment, () -> VillageIntroSaveBarrier.persist(player, state), null);
        var result = new VillageIntroDeliveryCoordinator(port).recover();
        if (result != VillageIntroDeliveryCoordinator.Result.HELD && result != VillageIntroDeliveryCoordinator.Result.REJECTED)
            acknowledge(state, player);
        return result;
    }
    static List<VillageIntroRequestType> offers(ServerPlayer player, VillageNoticeContext context) {
        if (!thread(player) || context == null) return List.of();
        var state = QuestState.get(player.level().getServer()); var data = state.getPlayerData(player.getUUID());
        if (!VillageWelcomeService.isCompleted(data) || data.getTradeRouteInt("guild_intro.welcome_village") - 1 != context.index()) return List.of();
        var bond = VillageBondProgression.snapshot(data, context.index());
        var progress = VillageIntroRequestProgress.open(data, context.villageId(), bond.actualCompletions(), bond.level() != VillageBondLevel.KNOWN);
        state.setDirty();
        if (!context.villageId().equals(progress.village())) return List.of();
        return progress.offers(bond.actualCompletions(), bond.level() != VillageBondLevel.KNOWN);
    }
    static de.quest.network.VillageNetworkPayloads.IntroBoardData boardData(ServerPlayer player, BlockPos board,
            VillageNoticeContext context) {
        var state = QuestState.get(player.level().getServer()); var data = state.getPlayerData(player.getUUID());
        UUID replacement = replacementEpisode(player, board);
        if (replacement != null) {
            UUID previous = UUID.fromString(data.getTradeRouteString(VillageIntroRequestProgress.PREFIX + "village"));
            return new de.quest.network.VillageNetworkPayloads.IntroBoardData(
                    new de.quest.network.VillageNetworkPayloads.IntroToken(data.progressGeneration(), replacement, previous,
                            VillageIntroRequestType.CATALOG_REVISION), -1, true,
                    net.minecraft.network.chat.Component.translatable("screen.village-quest.intro.replace_detail"));
        }
        if (context == null || offers(player, context).isEmpty()) return null;
        var bond = VillageBondProgression.snapshot(data, context.index());
        var progress = VillageIntroRequestProgress.open(data,context.villageId(),bond.actualCompletions(),bond.level()!=VillageBondLevel.KNOWN);
        return new de.quest.network.VillageNetworkPayloads.IntroBoardData(
                new de.quest.network.VillageNetworkPayloads.IntroToken(data.progressGeneration(), progress.episode(), context.villageId(),
                        VillageIntroRequestType.CATALOG_REVISION), progress.active() == null ? -1 : progress.active().ordinal(), false,
                net.minecraft.network.chat.Component.translatable("screen.village-quest.intro.choose_detail"));
    }
    static List<de.quest.network.VillageNetworkPayloads.NoticeBoardOfferData> boardOffers(ServerPlayer player, VillageNoticeContext context) {
        var data = QuestState.get(player.level().getServer()).getPlayerData(player.getUUID());
        var bond = VillageBondProgression.snapshot(data,context.index());
        var progress = VillageIntroRequestProgress.open(data,context.villageId(),bond.actualCompletions(),bond.level()!=VillageBondLevel.KNOWN);
        return offers(player,context).stream().map(type -> {
            var plan = VillageIntroInventoryPlan.plan(player.getInventory(),type).orElse(null);
            int amount = 0;
            for (int i=0;i<36;i++) {
                var stack=player.getInventory().getItem(i);
                if (VillageIntroInventoryPlan.accepts(type,stack)) amount+=stack.getCount();
            }
            if (type==VillageIntroRequestType.HARVEST_HELP) amount=progress.harvestCount();
            boolean paid = progress.paidCount()<2 && !de.quest.data.PersonalResetReceipts.has(data,"village_intro."+type.id());
            long reward = !paid ? 0 : plan == null ? -1 : plan.paidReward();
            return new de.quest.network.VillageNetworkPayloads.NoticeBoardOfferData(type.ordinal(),
                    net.minecraft.network.chat.Component.translatable("screen.village-quest.intro."+type.id()),
                    new net.minecraft.world.item.ItemStack(type==VillageIntroRequestType.REPAIR_TIMBER ? net.minecraft.world.item.Items.OAK_LOG
                            : type==VillageIntroRequestType.PATH_STONE ? net.minecraft.world.item.Items.COBBLESTONE : net.minecraft.world.item.Items.WHEAT),
                    type.target(),amount,reward,0,false,progress.active()==type && plan!=null && amount>=type.target());
        }).toList();
    }
    static boolean handleBoardAction(ServerPlayer player, BlockPos board, de.quest.network.VillageNetworkPayloads.NoticeBoardActionPayload payload) {
        var token=payload.token();
        if (!thread(player) || token==null || token.revision()!=VillageIntroRequestType.CATALOG_REVISION || !recoverBeforeActions(player)) return false;
        var data=QuestState.get(player.level().getServer()).getPlayerData(player.getUUID());
        if (token.generation()!=data.progressGeneration()
                || !token.village().toString().equals(data.getTradeRouteString(VillageIntroRequestProgress.PREFIX+"village"))
                || !token.episode().toString().equals(data.getTradeRouteString(VillageIntroRequestProgress.PREFIX+"episode"))) return false;
        if (payload.action()==de.quest.network.VillageNetworkPayloads.NoticeBoardActionPayload.INTRO_REPLACE)
            return replaceContact(player,board,token.generation(),token.episode());
        var context=VillageNoticeBoardService.localContext(player.level(),player,board).orElse(null);
        if (context==null || !context.villageId().equals(token.village()) || payload.requestId()<0
                || payload.requestId()>=VillageIntroRequestType.values().length) return false;
        var type=VillageIntroRequestType.values()[payload.requestId()];
        if (payload.action()==de.quest.network.VillageNetworkPayloads.NoticeBoardActionPayload.INTRO_ACCEPT)
            return accept(player,board,type.id(),token.revision(),token.generation(),token.episode());
        if (payload.action()==de.quest.network.VillageNetworkPayloads.NoticeBoardActionPayload.INTRO_DELIVER)
            return fulfill(player,board,type.id(),token.revision(),token.generation(),token.episode())==VillageIntroDeliveryCoordinator.Result.COMMITTED;
        if (payload.action()!=de.quest.network.VillageNetworkPayloads.NoticeBoardActionPayload.INTRO_CANCEL) return false;
        var bond=VillageBondProgression.snapshot(data,context.index());
        var progress=VillageIntroRequestProgress.open(data,context.villageId(),bond.actualCompletions(),bond.level()!=VillageBondLevel.KNOWN);
        if (!progress.cancel(type)) return false;
        de.quest.quest.QuestHarvestTracker.clearPlayer(player.getUUID());
        QuestState.get(player.level().getServer()).setDirty(); return true;
    }
    static boolean accept(ServerPlayer player, BlockPos board, String offer, int revision, int generation, UUID episode) {
        if (!thread(player) || !recoverBeforeActions(player)) return false;
        var context = VillageNoticeBoardService.localContext(player.level(), player, board).orElse(null);
        var type = VillageIntroRequestType.fromId(offer);
        if (context == null || type == null || !offers(player, context).contains(type)) return false;
        var state = QuestState.get(player.level().getServer()); var data = state.getPlayerData(player.getUUID());
        var bond = VillageBondProgression.snapshot(data, context.index());
        var progress = VillageIntroRequestProgress.open(data, context.villageId(), bond.actualCompletions(), bond.level() != VillageBondLevel.KNOWN);
        if (generation != data.progressGeneration() || !progress.episode().equals(episode)
                || !progress.accept(type, revision, bond.actualCompletions(), bond.level() != VillageBondLevel.KNOWN)) return false;
        de.quest.quest.QuestHarvestTracker.clearPlayer(player.getUUID());
        state.setDirty(); return true;
    }
    static VillageIntroDeliveryCoordinator.Result fulfill(ServerPlayer player, BlockPos board, String offer,
                                                          int revision, int generation, UUID episode) {
        if (!thread(player)) return VillageIntroDeliveryCoordinator.Result.REJECTED;
        if (!recoverBeforeActions(player)) return VillageIntroDeliveryCoordinator.Result.HELD;
        var context = VillageNoticeBoardService.localContext(player.level(), player, board).orElse(null);
        var type = VillageIntroRequestType.fromId(offer);
        if (context == null || type == null || !offers(player, context).contains(type)) return VillageIntroDeliveryCoordinator.Result.REJECTED;
        var state = QuestState.get(player.level().getServer()); var data = state.getPlayerData(player.getUUID());
        if (!otherFinancesReady(state, player)) return VillageIntroDeliveryCoordinator.Result.REJECTED;
        var bond = VillageBondProgression.snapshot(data, context.index());
        var progress = VillageIntroRequestProgress.open(data, context.villageId(), bond.actualCompletions(), bond.level() != VillageBondLevel.KNOWN);
        if (generation != data.progressGeneration() || !progress.episode().equals(episode) || progress.active() != type
                || revision != VillageIntroRequestType.CATALOG_REVISION
                || type == VillageIntroRequestType.HARVEST_HELP && progress.harvestCount() != 8) return VillageIntroDeliveryCoordinator.Result.REJECTED;
        var plan = VillageIntroInventoryPlan.plan(player.getInventory(), type).orElse(null);
        int paid = 0;
        for (var candidate : VillageIntroRequestType.values()) if (de.quest.data.PersonalResetReceipts.has(data, "village_intro." + candidate.id())) paid |= candidate.bit();
        var tx = VillageIntroTransaction.prepare(UUID.randomUUID(), player.getUUID(), generation, episode, context.villageId(), plan,
                bond.actualCompletions(), data.getCurrencyBalance(), data.getPilgrimInt("prosperity.stat.earned"),
                data.getTradeRouteInt(VillageIntroRequestProgress.PREFIX + "completed"), paid, player.registryAccess()).orElse(null);
        if (tx == null) return VillageIntroDeliveryCoordinator.Result.REJECTED;
        var port = new VillageIntroJournalPort(state, player.getUUID(), context.index(), player.getInventory(), player.registryAccess(),
                proof(player), () -> VillageIntroSaveBarrier.persist(player, state), tx);
        var result = new VillageIntroDeliveryCoordinator(port).execute(tx.receipt());
        if (result == VillageIntroDeliveryCoordinator.Result.COMMITTED || result == VillageIntroDeliveryCoordinator.Result.CANCELLED)
            acknowledge(state, player);
        else if (result == VillageIntroDeliveryCoordinator.Result.HELD) hold(player);
        return result;
    }
}
