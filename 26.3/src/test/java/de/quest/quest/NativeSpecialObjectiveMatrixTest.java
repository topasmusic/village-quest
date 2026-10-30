package de.quest.quest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.data.*;
import de.quest.quest.special.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;

/** Actual special-quest journal producers over saved data, without ticks or a world. */
final class NativeSpecialObjectiveMatrixTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM)
            item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                    .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64)
                    .set(net.minecraft.core.component.DataComponents.ITEM_NAME,Component.translatable(item.getDescriptionId())).build());
    }
    private final UUID id=UUID.randomUUID();
    private final QuestState state=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
    private final PlayerQuestData data=state.getPlayerData(id);
    private final ServerLevel world=mock(ServerLevel.class);
    private final MinecraftServer server=mock(MinecraftServer.class);
    private final ServerPlayer player=mock(ServerPlayer.class);
    private final net.minecraft.world.entity.player.Inventory inventory=mock(net.minecraft.world.entity.player.Inventory.class);
    private final List<ItemStack> slots=new ArrayList<>(Collections.nCopies(36,ItemStack.EMPTY));
    private org.mockito.MockedStatic<QuestState> states;
    @BeforeEach void setup() {
        when(world.getServer()).thenReturn(server);when(world.getDifficulty()).thenReturn(net.minecraft.world.Difficulty.PEACEFUL);
        var players=mock(net.minecraft.server.players.PlayerList.class);when(server.getPlayerList()).thenReturn(players);
        when(players.getPlayer(id)).thenReturn(player);when(player.getUUID()).thenReturn(id);when(player.getInventory()).thenReturn(inventory);
        when(inventory.getContainerSize()).thenReturn(36);when(inventory.getItem(anyInt())).thenAnswer(c->slots.get(c.getArgument(0)));
        states=mockStatic(QuestState.class,CALLS_REAL_METHODS);states.when(()->QuestState.get(server)).thenReturn(state);
    }
    @AfterEach void cleanup() {states.close();}
    private TranslatableContents objective(String family) {
        var before=QuestState.TYPE.codec().encodeStart(NbtOps.INSTANCE,state).getOrThrow();
        var entry=SpecialQuestService.journalEntries(world,id).stream().filter(e->e.entry().id().equals("special:"+family)).findFirst().orElseThrow();
        assertEquals(before,QuestState.TYPE.codec().encodeStart(NbtOps.INSTANCE,state).getOrThrow(),"Journal reads preserve all saved data");
        var rows=QuestShortObjective.rows(entry.entry().shortObjective());
        return (TranslatableContents)rows.stream().filter(c->!QuestObjectiveProgress.complete(c)).findFirst().orElse(rows.getFirst()).getContents();
    }
    @Test void realSmokerTurnInKeepsAllThreeInventoryRequirementsAndCompletedHoney() {
        data.setApiaristSmokerQuestStage(RelicQuestStage.READY);data.setApiaristSmokerHoneyProgress(30);
        data.setApiaristSmokerCombProgress(10);data.setApiaristSmokerBeeBreedProgress(4);data.setApiaristSmokerHoneyBlockProgress(5);
        slots.set(0,new ItemStack(Items.HONEY_BOTTLE,10));
        var before=QuestState.TYPE.codec().encodeStart(NbtOps.INSTANCE,state).getOrThrow();
        var entry=SpecialQuestService.journalEntries(world,id).stream().filter(e->e.entry().id().equals("special:apiarist_smoker")).findFirst().orElseThrow();
        var rows=QuestShortObjective.rows(entry.entry().shortObjective());assertEquals(3,rows.size());
        assertTrue(QuestObjectiveProgress.complete(rows.get(0)));assertFalse(QuestObjectiveProgress.complete(rows.get(1)));
        assertEquals(5,((TranslatableContents)rows.get(2).getContents()).getArgs()[2]);
        assertEquals(before,QuestState.TYPE.codec().encodeStart(NbtOps.INSTANCE,state).getOrThrow());assertEquals(10,slots.getFirst().getCount());
    }
    private void next(String family,String suffix) {assertEquals("quest.village-quest.special."+suffix,objective(family).getKey());}
    @Test void relicTrialMovesThroughAllFiveActualRequirementsThenTheCache() {
        data.setShardRelicQuestStage(ShardRelicQuestStage.TRIAL_ACTIVE);
        next("relic","shards.progress.amethyst");data.setShardRelicAmethystProgress(16);
        next("relic","shards.progress.potions");data.setShardRelicPotionProgress(3);
        next("relic","shards.progress.enchant");data.setShardRelicEnchantProgress(1);
        next("relic","shards.progress.ender_pearl");data.setShardRelicEnderPearlProgress(1);
        next("relic","shards.progress.blaze_rod");data.setShardRelicBlazeRodProgress(1);
        assertEquals("quest.village-quest.guidance.claim",objective("relic").getKey());
        data.setShardRelicQuestStage(ShardRelicQuestStage.CACHE_HUNT);
        assertEquals("quest.village-quest.tracker.cache",objective("relic").getKey());
    }
    @Test void merchantSealKeepsPurchasesSeparateFromTradesAndEmeralds() {
        data.setMerchantSealQuestStage(RelicQuestStage.ACTIVE);
        next("merchant_seal","merchant.progress.trades");data.setMerchantSealTradeProgress(20);
        next("merchant_seal","merchant.progress.emeralds");data.setMerchantSealEmeraldProgress(128);
        next("merchant_seal","merchant.progress.villager_purchases");data.setMerchantSealVillagerPurchaseProgress(10);
        next("merchant_seal","merchant.progress.pilgrim");data.setMerchantSealPilgrimPurchaseProgress(2);
        assertEquals("quest.village-quest.guidance.claim",objective("merchant_seal").getKey());
    }
    @Test void fluteTracksEachRealTamedAnimalAfterBreeding() {
        data.setShepherdFluteQuestStage(RelicQuestStage.ACTIVE);
        next("shepherd_flute","flute.progress.breed");data.setShepherdFluteBreedProgress(20);
        for(String animal:List.of("wolf","cat","parrot")) {
            next("shepherd_flute","flute.progress."+animal);
            data.setMilestoneFlag("special.shepherd_flute.tamed_"+animal,true);
        }
        assertEquals("quest.village-quest.guidance.claim",objective("shepherd_flute").getKey());
    }
    @Test void compassKeepsMiningCraftingAndEquipmentSeparate() {
        data.setSurveyorCompassQuestStage(RelicQuestStage.ACTIVE);
        next("surveyor_compass","surveyor_compass.progress.redstone");data.setSurveyorCompassRedstoneProgress(64);
        next("surveyor_compass","surveyor_compass.progress.lapis");data.setSurveyorCompassLapisProgress(64);
        next("surveyor_compass","surveyor_compass.progress.crafted");data.setSurveyorCompassCraftedProgress(1);
        next("surveyor_compass","surveyor_compass.progress.pickaxe");data.setSurveyorCompassPickaxeReadyProgress(1);
        assertEquals("quest.village-quest.guidance.claim",objective("surveyor_compass").getKey());
    }
    @Test void smokerWithCompletedWorkSelectsTheNextMissingTurnInMaterialAndNeverConsumesIt() {
        data.setApiaristSmokerQuestStage(RelicQuestStage.READY);data.setApiaristSmokerHoneyProgress(30);
        data.setApiaristSmokerCombProgress(10);data.setApiaristSmokerBeeBreedProgress(4);data.setApiaristSmokerHoneyBlockProgress(5);
        var honey=objective("apiarist_smoker");assertEquals("quest.village-quest.tracker.friend_material",honey.getKey());assertEquals(10,honey.getArgs()[2]);
        slots.set(0,new ItemStack(Items.HONEY_BOTTLE,10));
        var comb=objective("apiarist_smoker");assertEquals("item.minecraft.honeycomb",((TranslatableContents)((Component)comb.getArgs()[0]).getContents()).getKey());
        slots.set(1,new ItemStack(Items.HONEYCOMB,10));
        var blocks=objective("apiarist_smoker");assertEquals(5,blocks.getArgs()[2]);
        slots.set(2,new ItemStack(Items.HONEY_BLOCK,5));
        assertEquals("quest.village-quest.guidance.claim",objective("apiarist_smoker").getKey());assertEquals(10,slots.getFirst().getCount());
    }

    @Test void fiveRealAfterstoriesRetainTheirVillageAndSwitchFromMaterialToDeliveryIndependently() {
        var requests=List.of(de.quest.shrine.VillageRequestType.GRANARY_BREAD,de.quest.shrine.VillageRequestType.FORGE_IRON,
                de.quest.shrine.VillageRequestType.PASTURE_WOOL,de.quest.shrine.VillageRequestType.APIARY_HONEY,de.quest.shrine.VillageRequestType.ARCHIVE_PAPER);
        int[] amounts={10,10,15,5,20};
        data.setTradeRouteInt("bond_village_count",5);
        for(int i=0;i<5;i++) {
            data.setTradeRouteFlag(de.quest.shrine.VillageBondService.villageKey(i,"contact"),true);
            data.setTradeRouteInt(de.quest.shrine.VillageBondService.villageKey(i,"type"),requests.get(i).bondType().id()+1);
            data.setTradeRouteInt(de.quest.shrine.VillageBondService.villageKey(i,"x"),100+i*200);
            data.setTradeRouteInt(de.quest.shrine.VillageBondService.villageKey(i,"z"),50);
            assertTrue(de.quest.guildtown.GuildTownAfterstoryLedger.begin(data,i,requests.get(i)));
        }
        for(int i=0;i<5;i++) {
            var before=Map.copyOf(data.getTradeRouteIntState());
            var entries=de.quest.guildtown.GuildTownAfterstoryService.journalEntries(data,player);assertEquals(5,entries.size());
            var missing=(TranslatableContents)entries.get(i).entry().shortObjective().getContents();
            assertEquals("quest.village-quest.tracker.friend_material",missing.getKey());assertEquals(amounts[i],missing.getArgs()[2]);
            var location=(TranslatableContents)entries.get(i).details().getLast().getContents();assertEquals(100+i*200,location.getArgs()[0]);
            assertEquals(before,data.getTradeRouteIntState());
            slots.set(i,new ItemStack(requests.get(i).item(),amounts[i]));
            var ready=de.quest.guildtown.GuildTownAfterstoryService.journalEntries(data,player).get(i);
            assertEquals(entries.get(i).entry().id(),ready.entry().id());
            assertEquals("quest.village-quest.tracker.afterstory_deliver",((TranslatableContents)ready.entry().shortObjective().getContents()).getKey());
        }
        assertTrue(de.quest.guildtown.GuildTownAfterstoryLedger.complete(data,2));
        assertEquals(4,de.quest.guildtown.GuildTownAfterstoryService.journalEntries(data,player).size());
        assertEquals(15,slots.get(2).getCount(),"Journal presentation never consumes the hand-in");
    }
}
