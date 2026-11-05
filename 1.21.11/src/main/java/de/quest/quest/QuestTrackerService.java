package de.quest.quest;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.network.Payloads;
import de.quest.pilgrim.PilgrimContractService;
import de.quest.quest.daily.DailyQuestService;
import de.quest.quest.special.SpecialQuestService;
import de.quest.quest.special.SpecialQuestStatus;
import de.quest.quest.story.StoryQuestService;
import de.quest.quest.story.StoryQuestStatus;
import de.quest.quest.weekly.WeeklyQuestService;
import de.quest.quest.weekly.WeeklyQuestStatus;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class QuestTrackerService {
    private static final String AUTO_HINT_SHOWN = "quest_tracker_auto_hint_shown";
    private static final String CLIENT_DEFAULT_APPLIED = "config.quest_tracker_default_applied.2_1_1";
    private static final Map<UUID, Long> LAST_TRACKER_REFRESH = new ConcurrentHashMap<>();

    private QuestTrackerService() {}

    private static PlayerQuestData data(ServerLevel world, UUID playerId) {
        return QuestState.get(world.getServer()).getPlayerData(playerId);
    }

    public static boolean isEnabled(ServerLevel world, UUID playerId) {
        return world != null && playerId != null && data(world, playerId).isQuestTrackerEnabled();
    }

    public static boolean setEnabled(ServerLevel world, ServerPlayer player, boolean enabled) {
        if (world == null || player == null) {
            return false;
        }
        PlayerQuestData data = data(world, player.getUUID());
        if (data.isQuestTrackerEnabled() == enabled) {
            refresh(world, player);
            return enabled;
        }
        data.setQuestTrackerEnabled(enabled);
        QuestState.get(world.getServer()).setDirty();
        if (!enabled) {
            LAST_TRACKER_REFRESH.remove(player.getUUID());
        }
        refresh(world, player);
        return enabled;
    }

    public static boolean toggle(ServerLevel world, ServerPlayer player) {
        return setEnabled(world, player, !isEnabled(world, player.getUUID()));
    }

    public static void applyClientDefault(ServerLevel world, ServerPlayer player, boolean enabledByDefault) {
        if (world == null || player == null) return;
        PlayerQuestData data = data(world, player.getUUID());
        if (data.hasMilestoneFlag(CLIENT_DEFAULT_APPLIED)) return;
        data.setMilestoneFlag(CLIENT_DEFAULT_APPLIED, true);
        data.setQuestTrackerEnabled(enabledByDefault);
        QuestState.get(world.getServer()).setDirty();
        refresh(world, player);
    }

    public static void closeTracker(ServerPlayer player) {
        if (player == null) {
            return;
        }
        LAST_TRACKER_REFRESH.remove(player.getUUID());
        if (de.quest.network.ProgressionProtocol.require(player))
            ServerPlayNetworking.send(player, new Payloads.QuestTrackerPayload(false,List.of()));
    }

    public static void resetAllRuntimeState() {
        LAST_TRACKER_REFRESH.clear();
    }

    public static void handleDisconnect(UUID playerId) {
        if (playerId != null) {
            LAST_TRACKER_REFRESH.remove(playerId);
        }
    }

    public static void enableForAcceptedQuest(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return;
        }

        PlayerQuestData data = data(world, player.getUUID());
        boolean changed = false;
        if (!data.isQuestTrackerEnabled()) {
            data.setQuestTrackerEnabled(true);
            changed = true;
        }

        boolean showHint = !data.hasMilestoneFlag(AUTO_HINT_SHOWN);
        if (showHint) {
            data.setMilestoneFlag(AUTO_HINT_SHOWN, true);
            changed = true;
        }

        if (changed) {
            QuestState.get(world.getServer()).setDirty();
        }

        if (showHint) {
            MutableComponent command = Component.literal("/vq questtracker").withStyle(ChatFormatting.AQUA);
            player.sendSystemMessage(Component.translatable("message.village-quest.questtracker.auto_enabled", command).withStyle(ChatFormatting.GRAY), false);
        }
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel world = server.overworld();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isEnabled(world, player.getUUID())) {
                continue;
            }
            long now = world.getGameTime();
            long last = LAST_TRACKER_REFRESH.getOrDefault(player.getUUID(), -100L);
            if ((now - last) >= 20L) {
                refresh(world, player);
            }
        }
    }

    public static void refresh(ServerLevel world, ServerPlayer player) {
        if (world == null || player == null) {
            return;
        }
        if (!de.quest.network.ProgressionProtocol.require(player)) return;
        ServerPlayNetworking.send(player, buildPayload(world, player));
        LAST_TRACKER_REFRESH.put(player.getUUID(), world.getGameTime());
    }

    private static Payloads.QuestTrackerPayload buildPayload(ServerLevel world, ServerPlayer player) {
        boolean enabled = isEnabled(world, player.getUUID());
        if (!enabled) {
            return new Payloads.QuestTrackerPayload(false,List.of());
        }
        return new Payloads.QuestTrackerPayload(true,journalEntries(world,player).stream().map(JournalActiveQuest::entry).toList());
    }

    /** One complete active snapshot for journal identities and HUD focus reconciliation. */
    public static List<JournalActiveQuest> journalEntries(ServerLevel world,ServerPlayer player) {
        var entries=new java.util.ArrayList<JournalActiveQuest>();
        DailyQuestService.QuestStatus daily = DailyQuestService.openQuestStatus(world, player.getUUID());
        boolean dailyActive = daily != null;
        Component dailyTitle = dailyActive ? daily.title() : Component.empty();
        List<Component> dailyLines = dailyActive ? List.of(daily.progressLine()) : List.of();

        WeeklyQuestStatus weekly = WeeklyQuestService.openStatus(world, player.getUUID());
        boolean weeklyActive = weekly != null;
        Component weeklyTitle = weeklyActive ? weekly.title() : Component.empty();
        List<Component> weeklyLines = weeklyActive ? weekly.lines() : List.of();

        if(dailyActive) add(entries,"daily:"+DailyQuestService.activeQuestType(world,player.getUUID()).name().toLowerCase(java.util.Locale.ROOT),
                "daily",dailyTitle,dailyLines,Payloads.JournalActionPayload.ACTION_CANCEL_DAILY);
        if(weeklyActive) add(entries,"weekly:"+WeeklyQuestService.activeQuestType(world,player.getUUID()).name().toLowerCase(java.util.Locale.ROOT),
                "weekly",weeklyTitle,weeklyLines,Payloads.JournalActionPayload.ACTION_CANCEL_WEEKLY);
        for(var lane:de.quest.quest.story.StoryLane.values()) {
            var arc=StoryQuestService.activeArcType(world,player.getUUID(),lane);
            if(arc==null) continue;
            var status=StoryQuestService.openStatus(world,player.getUUID(),lane);
            if(status==null) continue;
            var next=QuestGuidanceService.story(world,player,arc);
            Component objective=lane==de.quest.quest.story.StoryLane.KINGDOM || next.status()!=QuestGuidanceService.Status.ACTIVE
                    ?QuestShortObjective.compactAction(next.action()):StoryQuestService.currentChapter(world,player.getUUID(),arc)
                            .shortObjective(world,player.getUUID(),status.lines());
            if(arc==de.quest.quest.story.StoryArcType.ARTIFACT_JOURNEY && next.status()==QuestGuidanceService.Status.ACTIVE)
                objective=de.quest.kingdom.ArtifactJourneyService.shortObjective(data(world,player.getUUID()));
            var details=new java.util.ArrayList<Component>(); details.add(next.action()); details.addAll(status.lines());
            entries.add(new JournalActiveQuest(new TrackerEntry("story:"+lane.name().toLowerCase(java.util.Locale.ROOT)+":"+arc.id(),
                    "story",status.title(),objective),details,-1));
        }
        // Full instructions accompany the existing short HUD objectives.
        for(int i=0;i<entries.size();i++) {
            var entry=entries.get(i);var details=new java.util.ArrayList<Component>();
            if(entry.entry().category().equals("daily")) {
                var type=DailyQuestService.activeQuestType(world,player.getUUID());
                boolean bonus=data(world,player.getUUID()).hasActiveBonusDaily() && !data(world,player.getUUID()).hasActiveDaily();
                details.addAll(DailyQuestService.descriptionLines(world,player.getUUID(),type,bonus));
            } else if(entry.entry().category().equals("weekly")) {
                details.addAll(WeeklyQuestService.descriptionLines(world,player.getUUID(),WeeklyQuestService.activeQuestType(world,player.getUUID())));
            } else if(entry.entry().category().equals("story")) {
                for(var lane:de.quest.quest.story.StoryLane.values()) {
                    var arc=StoryQuestService.activeArcType(world,player.getUUID(),lane);
                    if(arc!=null && entry.entry().id().endsWith(":"+arc.id())) {
                        var chapter=StoryQuestService.currentChapter(world,player.getUUID(),arc);
                        if(chapter!=null)details.addAll(chapter.descriptionLines(world,player.getUUID()));
                    }
                }
            }
            details.addAll(entry.details());entries.set(i,new JournalActiveQuest(entry.entry(),details,entry.cancelAction()));
        }
        entries.addAll(de.quest.guildtown.GuildTownService.journalEntries(data(world,player.getUUID())));

        PilgrimContractService.PilgrimContractStatus pilgrim = PilgrimContractService.openStatus(world, player.getUUID());
        boolean pilgrimActive = pilgrim != null;
        Component pilgrimTitle = pilgrimActive ? pilgrim.title() : Component.empty();
        List<Component> pilgrimLines = pilgrimActive ? pilgrim.lines() : List.of();

        var pilgrimDetails=new java.util.ArrayList<Component>();
        if(pilgrimActive)pilgrimDetails.addAll(PilgrimContractService.activeDescriptionLines(world,player));
        pilgrimDetails.addAll(pilgrimLines);
        if(pilgrimActive) entries.add(new JournalActiveQuest(new TrackerEntry("pilgrim:"+PilgrimContractService.activeType(data(world,player.getUUID())).name().toLowerCase(java.util.Locale.ROOT),
                "pilgrim",pilgrimTitle,PilgrimContractService.shortObjective(world,player,pilgrimLines)),pilgrimDetails,-1));
        entries.addAll(SpecialQuestService.journalEntries(world,player.getUUID()));
        entries.addAll(de.quest.ambient.EncounterQuestService.journal(player));
        entries.addAll(de.quest.caravan.TradeGuildService.journalEntries(world,player));
        entries.addAll(de.quest.guildtown.GuildTownService.sharedProjectJournalEntries(world,player.getUUID()));
        var afterstories=de.quest.guildtown.GuildTownAfterstoryService.journalEntries(data(world,player.getUUID()),player);
        entries.addAll(afterstories.subList(0,Math.min(afterstories.size(),TrackerEntry.MAX_ENTRIES-entries.size())));
        return JournalActiveQuest.checked(entries);
    }

    public static void add(List<JournalActiveQuest> entries,String id,String category,Component title,List<Component> lines,int cancel) {
        entries.add(new JournalActiveQuest(new TrackerEntry(id,category,title,QuestShortObjective.current(lines)),lines,cancel));
    }
}
