package de.quest.content.story;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.kingdom.ArtifactJourneyProgress;
import de.quest.kingdom.ArtifactJourneyService;
import de.quest.quest.story.*;
import de.quest.reputation.ReputationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Five causally connected scenes use real earned tools and confirmed service effects. */
public final class ArtifactJourneyStoryArc implements StoryArcDefinition {
    private static final String TEXT="quest.village-quest.story.artifact_journey.";
    private static PlayerQuestData data(ServerLevel world,UUID id) { return QuestState.get(world.getServer()).getPlayerData(id); }
    @Override public StoryArcType type() { return StoryArcType.ARTIFACT_JOURNEY; }
    @Override public Component title() { return Component.translatable(TEXT+"title"); }
    @Override public int chapterCount() { return 5; }
    @Override public boolean isUnlocked(ServerLevel world,UUID id) { return ArtifactJourneyService.unlocked(world,id); }
    @Override public boolean shouldShowLockedEntry(ServerLevel world,UUID id) { return data(world,id).hasStoryCompleted("living_kingdoms"); }
    @Override public Component lockedEntryBody(ServerLevel world,UUID id) { return Component.translatable(TEXT+"locked"); }
    @Override public StoryChapterDefinition chapter(int index) { return index>=0&&index<5?new Chapter(index):null; }
    private static Component part(int index,String suffix) { return de.quest.quest.QuestRuleText.translate(TEXT+"chapter_"+(index+1)+"."+suffix); }
    private static Component command(String key,String command) {
        return Component.translatable(TEXT+key).withStyle(s -> s.withColor(net.minecraft.ChatFormatting.AQUA).withClickEvent(new ClickEvent.RunCommand(command)));
    }
    private record Chapter(int index) implements StoryChapterDefinition {
        @Override public Component title() { return part(index,"title"); }
        @Override public Component offerParagraph1() { return part(index,"offer.1"); }
        @Override public Component offerParagraph2() { return part(index,"offer.2"); }
        @Override public List<Component> descriptionLines() { return List.of(offerParagraph1(),offerParagraph2(),part(index,"instructions")); }
        @Override public boolean canAccept(ServerLevel world,ServerPlayer player) {
            return ArtifactJourneyService.unlocked(world,player.getUUID()) && ArtifactJourneyService.target(world,data(world,player.getUUID())).isPresent();
        }
        @Override public Component acceptBlockedMessage(ServerLevel world,ServerPlayer player) { return Component.translatable(TEXT+"target_required"); }
        @Override public void onAccepted(ServerLevel world,ServerPlayer player) {
            ArtifactJourneyService.accepted(world,player);
            player.sendSystemMessage(part(index,"instructions"),false);
            player.sendSystemMessage(command("rebind","/vq artifacts target"),false);
        }
        @Override public List<Component> progressLines(ServerLevel world,UUID id) {
            var data=data(world,id); var lines=new ArrayList<Component>();
            lines.add(Component.translatable(TEXT+"target",ArtifactJourneyService.target(world,data).map(v -> v.name()).orElse("?")));
            lines.add(part(index,"instructions"));
            for(var deed:ArtifactJourneyProgress.required(index))
                lines.add(Component.translatable(TEXT+"progress",Component.translatable(TEXT+"deed."+deed.name().toLowerCase(Locale.ROOT)),
                        Component.translatable(TEXT+(ArtifactJourneyProgress.has(data,deed)?"done":"needed"))));
            if(index==4) {
                lines.add(Component.translatable(TEXT+"choice_preview"));
                lines.add(command("choice_reserve_button","/vq artifacts reserve"));
                lines.add(command("choice_road_button","/vq artifacts road"));
                if(ArtifactJourneyProgress.choice(data)>0) lines.add(Component.translatable(TEXT+"choice_"+(ArtifactJourneyProgress.choice(data)==1?"reserve":"road")));
            }
            return List.copyOf(lines);
        }
        @Override public boolean isComplete(ServerLevel world,ServerPlayer player) {
            var data=data(world,player.getUUID());
            return ArtifactJourneyProgress.ready(data) && ArtifactJourneyService.target(world,data).isPresent();
        }
        @Override public Component claimBlockedMessage(ServerLevel world,ServerPlayer player) { return part(index,"instructions"); }
        @Override public void onServerTick(ServerLevel world,ServerPlayer player) { ArtifactJourneyService.tick(world,player); }
        @Override public void onClaimed(ServerLevel world,ServerPlayer player) {
            if(index==4) {
                var data=data(world,player.getUUID());
                if(!data.hasMilestoneFlag(ArtifactJourneyProgress.PREFIX+"finale")) {
                    data.setMilestoneFlag(ArtifactJourneyProgress.PREFIX+"finale",true);
                    data.setMilestoneFlag(ArtifactJourneyProgress.PREFIX+"seal_comfort",true);
                    data.vanillaRewards().preference(ArtifactJourneyProgress.choice(data)==1?"supplies":"roads");
                    ArtifactJourneyService.target(world,data).ifPresent(v -> de.quest.guildtown.GuildTownProgress.addPersonalChronicle(data,
                            new de.quest.guildtown.GuildTownProgress.VillageIdentity(player.getUUID(),v.legacyKey().anchorX(),v.legacyKey().anchorZ()),
                            ArtifactJourneyProgress.choice(data)==1?"artifact_reserve":"artifact_road",true,world.getGameTime()));
                    QuestState.get(world.getServer()).setDirty();
                    player.sendSystemMessage(Component.translatable(TEXT+"epilogue_"+(ArtifactJourneyProgress.choice(data)==1?"reserve":"road")),false);
                    player.sendSystemMessage(Component.translatable(TEXT+"title_earned"),false);
                }
            }
        }
        @Override public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(title(),part(index,"complete.1"),part(index,"complete.2"),part(index,"complete.3"),
                    index==4?96:24+index*8,index==4?3:1,ReputationService.ReputationTrack.TRADE,index==4?20:8,null);
        }
    }
}
