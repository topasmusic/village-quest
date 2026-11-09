package de.quest.network;

import de.quest.data.QuestState;
import de.quest.guildtown.*;
import de.quest.quest.special.*;
import de.quest.quest.story.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** Read-only snapshot requested only after client consent; no journal UI or progression mutation. */
public final class GameplayStatsSnapshot {
    private static final java.util.Map<ServerPlayer,Long> LAST = new java.util.WeakHashMap<>();
    private GameplayStatsSnapshot() {}
    public static void send(ServerPlayer player) {
        if (player.isRemoved() || player.connection == null || !ServerPlayNetworking.canSend(player, GameplayStatsEvent.ID)) return;
        // At most once per second per connection; malformed clients cannot cause unbounded reads.
        long tick = System.nanoTime();
        Long last = LAST.get(player);
        if (last != null && tick-last < 1_000_000_000L) return;
        LAST.put(player,tick);
        var world = player.level(); var id = player.getUUID();
        var data = QuestState.get(world.getServer()).getPlayerData(id);
        frame(player,"snapshot.begin");
        if (data.hasActiveDaily()) active(player,"quest","daily_"+data.getDailyChoice().name().toLowerCase(java.util.Locale.ROOT));
        if (data.hasActiveBonusDaily()) active(player,"quest","bonus_"+data.getBonusChoice().name().toLowerCase(java.util.Locale.ROOT));
        if (data.hasActiveWeekly()) active(player,"quest","weekly_"+data.getWeeklyChoice().name().toLowerCase(java.util.Locale.ROOT));
        for (var lane : StoryLane.values()) {
            var arc = data.getActiveStoryArc(lane);
            if (arc != null) active(player,"story",arc.id()+"_"+(StoryQuestService.chapterIndex(world,id,arc)+1));
        }
        if (ShardRelicQuestService.isActive(world,id)) active(player,"quest","special_relic");
        if (MerchantSealQuestService.isActive(world,id)) active(player,"quest","special_merchant_seal");
        if (ShepherdFluteQuestService.isActive(world,id)) active(player,"quest","special_shepherd_flute");
        if (ApiaristSmokerQuestService.isActive(world,id)) active(player,"quest","special_apiarist_smoker");
        if (SurveyorCompassQuestService.isActive(world,id)) active(player,"quest","special_surveyor_compass");
        var friends = FriendsQuestService.progress(world,id);
        if (friends.number("simon.herbs")==1) active(player,"quest","special_simon_herbs");
        for (String who : new String[]{"simon","lutte","marius"}) if (friends.active(who))
            active(player,"quest","special_friend_"+who+"_"+(friends.stage(who)+1));
        for (var story : GuildTownStory.values()) if (GuildTownProgress.activeStoryId(data)==story.id()) {
            active(player,"quest","town_"+story.key());
            if (GuildTownProgress.storyState(data,story)==GuildTownProgress.PAUSED) frame(player,"quest.paused.town_"+story.key());
        }
        var commission = GuildTownProgress.activeCommission(data);
        if (commission != null) {
            active(player,"quest","commission_"+commission.key());
            if (GuildTownProgress.commissionState(data,commission)==GuildTownProgress.PAUSED) frame(player,"quest.paused.commission_"+commission.key());
        }
        var freight = de.quest.caravan.TradeGuildService.activeContract(world,id);
        if (freight != null) active(player,"quest","freight_"+freight.key());
        String encounter = de.quest.ambient.EncounterQuestService.statsActiveKey(player);
        if (encounter != null) active(player,"quest",encounter);
        var guild = de.quest.guild.VillageGuildService.guild(world,id);
        var project = guild == null ? null : GuildTownSharedState.get(world.getServer()).project(guild.id()).orElse(null);
        if (project != null && !project.completed()) active(player,"quest","shared_"+project.type().key());
        for (int i=0; i<de.quest.shrine.VillageBondService.historicalVillageCount(data); i++) {
            if (GuildTownAfterstoryLedger.state(data,i)!=GuildTownAfterstoryLedger.State.ACTIVE) continue;
            var request = GuildTownAfterstoryLedger.request(data,i);
            if (request != null) active(player,"quest","afterstory_"+request.bondType().key());
        }
        frame(player,"snapshot.end");
        frame(player,"profile.current."+de.quest.config.VillageQuestServerConfig.get().adventureProfile().name().toLowerCase(java.util.Locale.ROOT));
    }
    private static void active(ServerPlayer player,String category,String key) {
        if (GameplayStatsMetrics.content(category+"."+key)) frame(player,category+".active."+key);
    }
    private static void frame(ServerPlayer player,String metric) { ServerPlayNetworking.send(player,new GameplayStatsEvent(metric)); }
}
