package de.quest.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import de.quest.guild.VillageGuildState;
import de.quest.guildtown.GuildTownSharedState;
import de.quest.kingdom.LivingKingdomsState;
import de.quest.party.QuestPartyService;
import de.quest.village.LivingVillageNetworkState;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Native personal reset orchestration. Unknown or interrupted documents freeze actions until recovered. */
public final class PersonalResetService {
    private static final class Attachment {
        static final AttachmentType<Tag> TYPE=AttachmentRegistry.create(Identifier.parse(PersonalResetSaveBarrier.PLAYER_PROOF),
                builder -> builder.persistent(Codec.PASSTHROUGH.xmap(value -> value.convert(NbtOps.INSTANCE).getValue().copy(),
                        value -> new Dynamic<>(NbtOps.INSTANCE,value.copy()))).copyOnDeath());
    }
    private PersonalResetService() {}
    public static void initializeAttachment(){var type=Attachment.TYPE;}
    private static PersonalResetJournalPort.PlayerProof proof(ServerPlayer player){
        var target=(AttachmentTarget)player;
        return new PersonalResetJournalPort.PlayerProof(){
            public Tag get(){var raw=target.getAttached(Attachment.TYPE);return raw==null?null:raw.copy();}
            public void set(CompoundTag tag){target.setAttached(Attachment.TYPE,tag.copy());}
        };
    }
    public static boolean blocksActions(ServerLevel world,UUID actor){
        if(world==null || actor==null)return true;
        var state=QuestState.get(world.getServer());var raw=state.personalResetTransaction(actor);
        if(raw==null)return false;
        var tx=PersonalResetTransaction.fromNbt(raw,world.registryAccess()).orElse(null);
        return tx==null || !tx.actor().equals(actor) || !tx.acknowledged() || !state.personalResetUnlocked(actor);
    }
    private static boolean thread(ServerPlayer player){
        return player!=null && !player.isRemoved() && player.level().getServer().isSameThread()
                && !player.level().getServer().isStopped() && !player.level().getServer().isShutdown();
    }
    public static boolean recoverBeforeActions(ServerPlayer player){
        if(!thread(player))return false;
        try {
            var state=QuestState.get(player.level().getServer());var proof=proof(player);
            var raw=state.personalResetTransaction(player.getUUID());
            if(raw==null && proof.get()==null)return true;
            var q=PersonalResetTransaction.fromNbt(raw,player.registryAccess()).orElse(null);
            var p=PersonalResetTransaction.fromNbt(proof.get(),player.registryAccess()).orElse(null);
            if(q!=null && q.actor().equals(player.getUUID()) && q.sameTransaction(p)
                    && q.acknowledged() && p.acknowledged() && q.stage()==p.stage() && state.personalResetUnlocked(player.getUUID()))return true;
            var port=port(state,player,proof,null);
            var result=new PersonalResetCoordinator(port).recover();
            if(result==PersonalResetCoordinator.Result.COMMITTED || result==PersonalResetCoordinator.Result.CANCELLED)
                if(port.acknowledge() && state.confirmPersonalResetTerminal(player.getUUID(),(CompoundTag)state.personalResetTransaction(player.getUUID())))return true;
        } catch(RuntimeException uncertain){/* Preserve every document; no compensation guesses. */}
        hold(player);return false;
    }
    public static boolean restart(ServerPlayer player){
        if(!thread(player) || !player.isAlive() || player.gameMode==null
                || player.gameMode.getGameModeForPlayer()!=net.minecraft.world.level.GameType.SURVIVAL)return false;
        if(!recoverBeforeActions(player) || !de.quest.shrine.VillageIntroRequestService.recoverBeforeActions(player))return false;
        var state=QuestState.get(player.level().getServer());var proof=proof(player);
        try {
            var shared=shared(player);
            if (unfinishedLastGuildProject(player)) {
                player.sendSystemMessage(Component.translatable("command.village-quest.personal_reset.guild_project"), false);
                return false;
            }
            if(!state.storyLanesWritable() || !shared.ready() || de.quest.shrine.VillageIntroRequestService.blocksActions(player.level(),player.getUUID()))return false;
            // Financial and generation preflight must precede even the non-destructive normalization.
            state.getPlayerData(player.getUUID()).personalRestart();PersonalResetItems.plan(player);
            PersonalResetItems.normalizeTransient(player);
            if(!PersonalResetSaveBarrier.persist(player,false))return false;
            var tx=PersonalResetTransaction.prepare(UUID.randomUUID(),player.getUUID(),state,PersonalResetItems.plan(player),player.registryAccess()).orElseThrow();
            var port=port(state,player,proof,tx);var result=new PersonalResetCoordinator(port).execute(tx.receipt());
            if(result==PersonalResetCoordinator.Result.COMMITTED && port.acknowledge()
                    && state.confirmPersonalResetTerminal(player.getUUID(),(CompoundTag)state.personalResetTransaction(player.getUUID()))) {
                de.quest.quest.QuestTrackerService.closeTracker(player);
                de.quest.quest.QuestBookHelper.refreshQuestBook(player.level(),player);return true;
            }
            hold(player);return false;
        } catch(RuntimeException blocked){
            if(blocksActions(player.level(),player.getUUID()))hold(player);
            return false;
        }
    }
    static boolean unfinishedLastGuildProject(ServerPlayer player) {
        var server=player.level().getServer();
        var guild=VillageGuildState.get(server).guildFor(player.getUUID()).orElse(null);
        return guild!=null && guild.members().size()==1 && GuildTownSharedState.get(server).project(guild.id()).isPresent();
    }
    private static void hold(ServerPlayer player){
        de.quest.VillageQuest.LOGGER.warn("Personal reset held for {}; journal and player proof retained",player.getUUID());
        if(player.connection!=null)player.connection.disconnect(Component.translatable("message.village-quest.personal_reset.recovery_held"));
    }
    private static PersonalResetJournalPort port(QuestState state,ServerPlayer player,PersonalResetJournalPort.PlayerProof proof,PersonalResetTransaction proposed){
        return new PersonalResetJournalPort(state,player,proof,shared(player),()->PersonalResetSaveBarrier.persist(player,true),proposed);
    }
    private static PersonalResetJournalPort.Shared shared(ServerPlayer player){
        var server=player.level().getServer();var world=server.overworld();var actor=player.getUUID();
        return new PersonalResetJournalPort.Shared(){
            public boolean ready(){
                var kingdoms=LivingKingdomsState.get(server);var data=QuestState.get(server).getPlayerData(actor);
                return !unfinishedLastGuildProject(player) && !kingdoms.readOnly() && !kingdoms.markets().readOnly() && kingdoms.markets().pending(actor).isEmpty()
                        && !data.marketWallet().readOnly() && data.marketWallet().marker().filter(m ->
                            m.stage()==de.quest.kingdom.MarketTransferMarker.Stage.BEFORE || m.stage()==de.quest.kingdom.MarketTransferMarker.Stage.AFTER).isEmpty()
                        && !VillageGuildState.get(server).readOnly() && !GuildTownSharedState.get(server).readOnly()
                        && !LivingVillageNetworkState.get(server).readOnly();
            }
            public boolean detach(){
                if(!ready())return false;
                QuestPartyService.detachForPersonalRestart(world,actor);
                VillageGuildState.get(server).detachForPersonalRestart(actor);
                // Earned project entitlements and in-progress contributions deliberately survive.
                LivingVillageNetworkState.get(server).removeOwner(actor);
                de.quest.caravan.TradeRouteService.handleDisconnect(actor);
                de.quest.caravan.TradeRouteService.removeOwnerRuntimes(world,actor);
                de.quest.content.story.ShadowsTradeRoadEncounterService.adminResetPlayer(world,actor);
                de.quest.content.story.EmptyCaravanStoryService.cleanupPlayer(world,actor);
                de.quest.quest.special.LutteCoastalQuest.finished(world,actor);
                de.quest.quest.special.FriendsQuestUiService.disconnected(actor);
                de.quest.questmaster.QuestMasterUiService.close(player);
                de.quest.reward.VanillaRewardService.clearPersonalSession(actor);
                de.quest.quest.QuestDropTracker.clearPlayer(actor);de.quest.quest.QuestHarvestTracker.clearPlayer(actor);
                return detached();
            }
            public boolean detached(){return ready() && QuestPartyService.personalRestartDetached(world,actor)
                    && VillageGuildState.get(server).guildFor(actor).isEmpty() && VillageGuildState.get(server).invitationFor(actor).isEmpty()
                    && !LivingVillageNetworkState.get(server).hasOwner(actor);}
        };
    }
}
