package de.quest.content.story;

import de.quest.data.PlayerQuestData;
import de.quest.data.QuestState;
import de.quest.kingdom.BuildingScanner;
import de.quest.kingdom.KingdomBuildingService;
import de.quest.kingdom.KingdomStoryProgress;
import de.quest.kingdom.LivingKingdomsState;
import de.quest.quest.story.StoryArcDefinition;
import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryChapterCompletion;
import de.quest.quest.story.StoryChapterDefinition;
import de.quest.questmaster.QuestMasterProgressionService;
import de.quest.recipe.VillageQuestRecipeBookService;
import de.quest.reputation.ReputationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Five chapters use confirmed shared buildings and real local service events, never inventory ownership. */
public final class LivingKingdomsStoryArc implements StoryArcDefinition {
    private static final String TEXT="quest.village-quest.story.living_kingdoms.";
    private final List<StoryChapterDefinition> chapters=List.of(new Chapter(0),new Chapter(1),new Chapter(2),new Chapter(3),new Chapter(4));
    @Override public StoryArcType type() { return StoryArcType.LIVING_KINGDOMS; }
    @Override public Component title() { return Component.translatable(TEXT+"title"); }
    @Override public int chapterCount() { return chapters.size(); }
    @Override public StoryChapterDefinition chapter(int index) { return index>=0 && index<chapters.size()?chapters.get(index):null; }
    @Override public boolean isUnlocked(ServerLevel world, UUID player) { return QuestMasterProgressionService.isStoryCategoryUnlocked(world,player); }
    private static PlayerQuestData data(ServerLevel world,UUID player) { return QuestState.get(world.getServer()).getPlayerData(player); }
    private static Component part(int index,String suffix) { return Component.translatable(TEXT+"chapter_"+(index+1)+"."+suffix); }
    private static final class Chapter implements StoryChapterDefinition {
        private final int index;
        Chapter(int index) { this.index=index; }
        @Override public Component title() { return part(index,"title"); }
        @Override public Component offerParagraph1() { return part(index,"offer.1"); }
        @Override public Component offerParagraph2() { return part(index,"offer.2"); }
        @Override public List<Component> progressLines(ServerLevel world,UUID player) {
            var data=data(world,player); var state=LivingKingdomsState.get(world.getServer());
            UUID target=KingdomStoryProgress.target(data);
            List<Component> lines=new ArrayList<>();
            lines.add(Component.translatable(TEXT+"target",target==null?Component.translatable(TEXT+"unbound")
                    :state.village(target).map(v -> Component.literal(v.name())).orElse(Component.translatable(TEXT+"missing_target"))));
            if (index<2) {
                for (var role:BuildingScanner.Role.values()) if (index==0?role==BuildingScanner.Role.COUNCIL:role!=BuildingScanner.Role.COUNCIL)
                    lines.add(Component.translatable(TEXT+"progress",Component.translatable("screen.village-quest.building.role."+role.name().toLowerCase(Locale.ROOT)),
                            Component.translatable(TEXT+(KingdomStoryProgress.earned(data,state,role)?"done":"needed"))));
                if(index==1) lines.add(Component.translatable(TEXT+"progress",Component.translatable("block.village-quest.market_stall"),
                        Component.translatable(TEXT+(KingdomStoryProgress.hasMarketPlace(data,state)?"done":"needed"))));
            } else for (var deed:required(index)) lines.add(Component.translatable(TEXT+"progress",Component.translatable(TEXT+"deed."+deed.name().toLowerCase(Locale.ROOT)),
                    Component.translatable(TEXT+(KingdomStoryProgress.has(data,deed)?"done":"needed"))));
            return List.copyOf(lines);
        }
        @Override public boolean isComplete(ServerLevel world,ServerPlayer player) {
            return KingdomStoryProgress.complete(data(world,player.getUUID()),LivingKingdomsState.get(world.getServer()),index);
        }
        @Override public Component claimBlockedMessage(ServerLevel world,ServerPlayer player) { return part(index,"offer.2"); }
        @Override public void onAccepted(ServerLevel world,ServerPlayer player) {
            var data=data(world,player.getUUID()); data.setMilestoneFlag(KingdomBuildingService.COUNCIL_UNLOCK,true);
            if (index>=1) data.setMilestoneFlag(KingdomBuildingService.BUILDINGS_UNLOCK,true);
            QuestState.get(world.getServer()).setDirty(); VillageQuestRecipeBookService.unlockEligibleRecipes(player);
        }
        @Override public void onClaimed(ServerLevel world,ServerPlayer player) {
            var data=data(world,player.getUUID());
            if (index==0) data.setMilestoneFlag(KingdomBuildingService.BUILDINGS_UNLOCK,true);
            if (index==1) {
                data.setMilestoneFlag("kingdoms.route_permit.earned",true);
                if (player.level().dimension().equals(net.minecraft.world.level.Level.OVERWORLD))
                    de.quest.caravan.TradeRouteService.initializeProvisionalNetwork((ServerLevel)player.level(),player);
            }
            if (index==4 && !data.hasMilestoneFlag(KingdomStoryProgress.CHECK_EARNED)) {
                data.setMilestoneFlag(KingdomStoryProgress.CHECK_EARNED,true);
                var check=de.quest.archive.GuildArchiveService.issueInitial(world,player,
                        de.quest.archive.GuildArchiveService.ArchiveItem.VILLAGE_CHECK,
                        new net.minecraft.world.item.ItemStack(de.quest.registry.ModItems.VILLAGE_CHECK));
                if (!player.getInventory().add(check)) player.drop(check,false,net.minecraft.util.Prediction.SERVER_ONLY);
                player.inventoryMenu.broadcastChanges();
            }
            QuestState.get(world.getServer()).setDirty(); VillageQuestRecipeBookService.unlockEligibleRecipes(player);
        }
        @Override public StoryChapterCompletion buildCompletion() {
            return new StoryChapterCompletion(title(),part(index,"complete.1"),part(index,"complete.2"),part(index,"complete.3"),
                    index==4?40:16+index*4,index==4?2:1,ReputationService.ReputationTrack.CRAFTING,index==4?12:5,null);
        }
    }
    private static List<KingdomStoryProgress.Deed> required(int chapter) {
        return switch(chapter) {
            case 2 -> List.of(KingdomStoryProgress.Deed.MEET_LEADER,KingdomStoryProgress.Deed.MEET_INNKEEPER,KingdomStoryProgress.Deed.MEET_STOREKEEPER);
            case 3 -> List.of(KingdomStoryProgress.Deed.MARKET_TRADE,KingdomStoryProgress.Deed.STORE_DELIVERY);
            case 4 -> List.of(KingdomStoryProgress.Deed.FIRST_PROJECT);
            default -> List.of();
        };
    }
}
