package de.quest.quest.special;
import de.quest.data.*;
import de.quest.entity.KingdomNpcEntity;
import de.quest.kingdom.*;
import de.quest.quest.*;
import de.quest.quest.daily.DailyQuestService;
import de.quest.reward.*;
import de.quest.economy.ProsperityService;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real personal ledger, inventory stacks and currency; mocked native boundaries, no game/world execution. */
final class FriendsGuestTurnInTest {
 private final List<MockedStatic<?>> mocks=new ArrayList<>();
 private ServerLevel world;private ServerPlayer player;private KingdomNpcEntity npc;private PlayerQuestData data;private Inventory inventory;private UUID playerId,body;
 @BeforeAll static void bootstrap() {
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  var components=net.minecraft.core.component.DataComponentMap.builder().set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64).build();
  for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM)item.builtInRegistryHolder().bindComponents(components);
 }
 private <T> MockedStatic<T> statics(Class<T> type) {var mock=mockStatic(type);mocks.add(mock);return mock;}
 @BeforeEach void fixture() {
  var server=mock(MinecraftServer.class);world=mock(ServerLevel.class);player=mock(ServerPlayer.class);npc=mock(KingdomNpcEntity.class);inventory=mock(Inventory.class);
  playerId=UUID.randomUUID();body=UUID.randomUUID();var village=UUID.randomUUID();data=new PlayerQuestData();
  when(world.getServer()).thenReturn(server);when(server.overworld()).thenReturn(world);when(world.getEntity(body)).thenReturn(npc);
  when(player.level()).thenReturn(world);when(player.isAlive()).thenReturn(true);when(player.getUUID()).thenReturn(playerId);when(player.hasLineOfSight(npc)).thenReturn(true);when(player.getInventory()).thenReturn(inventory);
  when(npc.isAlive()).thenReturn(true);when(npc.blockPosition()).thenReturn(BlockPos.ZERO);when(inventory.getItem(anyInt())).thenReturn(ItemStack.EMPTY);
  var quest=mock(QuestState.class);when(quest.getPlayerData(playerId)).thenReturn(data);statics(QuestState.class).when(()->QuestState.get(server)).thenReturn(quest);
  var state=mock(LivingKingdomsState.class,RETURNS_DEEP_STUBS);statics(LivingKingdomsState.class).when(()->LivingKingdomsState.get(server)).thenReturn(state);
  var view=mock(KingdomPersonRegistry.View.class);when(view.guest()).thenReturn(1);when(view.village()).thenReturn(village);statics(KingdomNpcService.class).when(()->KingdomNpcService.available(world,npc)).thenReturn(Optional.of(view));
  var anchor=mock(KingdomAnchorRegistry.AnchorView.class);var anchorId=UUID.randomUUID();when(anchor.id()).thenReturn(anchorId);when(anchor.position()).thenReturn(BlockPos.ZERO);
  when(state.anchors().official(village,BuildingScanner.Role.INN)).thenReturn(Optional.of(anchor));when(state.anchors().mayContinue(anchorId,0)).thenReturn(true);
  statics(DailyQuestService.class);statics(QuestBookHelper.class);statics(QuestTrackerService.class);statics(ProsperityService.class);
  var rewards=mockStatic(VanillaRewardService.class,CALLS_REAL_METHODS);mocks.add(rewards);rewards.when(()->VanillaRewardService.deliverItems(world,player)).thenReturn(0);
  statics(RewardItems.class).when(()->RewardItems.encode(eq(world),anyList())).thenAnswer(call -> {var encoded=new ListTag();for(var ignored:(List<?>)call.getArgument(1)){var item=new CompoundTag();item.putString("fixture","native reward boundary");encoded.add(item);}return encoded;});
 }
 @AfterEach void close() {for(int i=mocks.size()-1;i>=0;i--)mocks.get(i).close();mocks.clear();}
 @Test void deliveryConsumesExactlyOnceAndKeepsOverflowRewardsInPersistentEscrow() {
  var p=new FriendsQuestProgress(data);p.accept("simon",0,0,0);
  var dandelion=new ItemStack(Items.DANDELION,9);var poppy=new ItemStack(Items.POPPY,8);var fern=new ItemStack(Items.FERN,8);
  when(inventory.getItem(0)).thenReturn(dandelion);when(inventory.getItem(1)).thenReturn(poppy);when(inventory.getItem(2)).thenReturn(fern);
  assertEquals(1,FriendsQuestService.command(player,body.toString(),"claim"));
  assertEquals(1,dandelion.getCount());assertEquals(0,poppy.getCount());assertEquals(0,fern.getCount());assertEquals(1,p.stage("simon"));assertFalse(p.active("simon"));assertEquals(10,data.getCurrencyBalance());assertEquals(3,data.vanillaRewards().pending().size());
  assertEquals(0,FriendsQuestService.command(player,body.toString(),"claim"));assertEquals(10,data.getCurrencyBalance());assertEquals(3,data.vanillaRewards().pending().size());
 }
 @Test void fullRewardQueueDoesNotConsumePlantsOrCompleteQuest() {
  var p=new FriendsQuestProgress(data);p.accept("simon",0,0,0);var pending=new ListTag();for(int i=0;i<240;i++)pending.add(new CompoundTag());data.vanillaRewards().pending(pending);
  var flowers=List.of(new ItemStack(Items.DANDELION,8),new ItemStack(Items.POPPY,8),new ItemStack(Items.FERN,8));for(int i=0;i<3;i++)when(inventory.getItem(i)).thenReturn(flowers.get(i));
  assertEquals(0,FriendsQuestService.command(player,body.toString(),"claim"));assertTrue(p.active("simon"));assertEquals(0,p.stage("simon"));assertEquals(0,data.getCurrencyBalance());for(var stack:flowers)assertEquals(8,stack.getCount());
 }
 @Test void remoteReplayAfterGuestDepartureCannotTurnIn() {
  var p=new FriendsQuestProgress(data);p.accept("simon",0,0,0);when(player.hasLineOfSight(npc)).thenReturn(false);
  assertEquals(0,FriendsQuestService.command(player,body.toString(),"claim"));assertTrue(p.active("simon"));assertEquals(0,data.getCurrencyBalance());assertTrue(data.vanillaRewards().pending().isEmpty());
 }
 @Test void unrelatedResidentDialogueDoesNotReadBiomeOrChangeQuestProgress() {
  FriendsQuestService.residentContact(world,player,UUID.randomUUID(),BlockPos.ZERO);
  verify(world,never()).getBiome(any());assertEquals(0,new FriendsQuestProgress(data).stage("simon"));
 }

}
