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
 private UUID villageId;private LivingKingdomsState kingdom;private de.quest.network.FriendsQuestPayloads.View shown;
 @BeforeAll static void bootstrap() {
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  var components=net.minecraft.core.component.DataComponentMap.builder().set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64).build();
  for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM)item.builtInRegistryHolder().bindComponents(components);
 }
 private <T> MockedStatic<T> statics(Class<T> type) {var mock=mockStatic(type);mocks.add(mock);return mock;}
 @BeforeEach void fixture() {
  var server=mock(MinecraftServer.class);world=mock(ServerLevel.class);player=mock(ServerPlayer.class);npc=mock(KingdomNpcEntity.class);inventory=mock(Inventory.class);
  playerId=UUID.randomUUID();body=UUID.randomUUID();var village=UUID.randomUUID();villageId=village;data=new PlayerQuestData();
  when(world.registryAccess()).thenReturn(net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));when(world.getServer()).thenReturn(server);when(server.overworld()).thenReturn(world);when(world.getEntity(body)).thenReturn(npc);
  when(player.level()).thenReturn(world);when(player.isAlive()).thenReturn(true);when(player.getUUID()).thenReturn(playerId);when(player.hasLineOfSight(npc)).thenReturn(true);when(player.getInventory()).thenReturn(inventory);
  when(npc.isAlive()).thenReturn(true);when(npc.blockPosition()).thenReturn(BlockPos.ZERO);when(inventory.getItem(anyInt())).thenReturn(ItemStack.EMPTY);
  var quest=mock(QuestState.class);when(quest.getPlayerData(playerId)).thenReturn(data);statics(QuestState.class).when(()->QuestState.get(server)).thenReturn(quest);
  var state=mock(LivingKingdomsState.class,RETURNS_DEEP_STUBS);kingdom=state;statics(LivingKingdomsState.class).when(()->LivingKingdomsState.get(server)).thenReturn(state);
  var view=mock(KingdomPersonRegistry.View.class);when(view.guest()).thenReturn(1);when(view.village()).thenReturn(village);statics(KingdomNpcService.class).when(()->KingdomNpcService.available(world,npc)).thenReturn(Optional.of(view));
  var anchor=mock(KingdomAnchorRegistry.AnchorView.class);var anchorId=UUID.randomUUID();when(anchor.id()).thenReturn(anchorId);when(anchor.position()).thenReturn(BlockPos.ZERO);
  when(state.anchors().official(village,BuildingScanner.Role.INN)).thenReturn(Optional.of(anchor));when(state.anchors().mayContinue(anchorId,0)).thenReturn(true);
  statics(DailyQuestService.class);statics(QuestBookHelper.class);statics(QuestTrackerService.class);statics(ProsperityService.class);
  var rewards=mockStatic(VanillaRewardService.class,CALLS_REAL_METHODS);mocks.add(rewards);rewards.when(()->VanillaRewardService.deliverItems(world,player)).thenReturn(0);
  statics(RewardItems.class).when(()->RewardItems.encode(eq(world),anyList())).thenAnswer(call -> {var encoded=new ListTag();for(var ignored:(List<?>)call.getArgument(1)){var item=new CompoundTag();item.putString("fixture","native reward boundary");encoded.add(item);}return encoded;});
 }
 @AfterEach void close() {for(int i=mocks.size()-1;i>=0;i--)mocks.get(i).close();mocks.clear();}
 private void conversation() { conversation(null); }
 private void conversation(net.minecraft.network.chat.Component lore) {
  when(world.dimension()).thenReturn(net.minecraft.world.level.Level.OVERWORLD);when(npc.getUUID()).thenReturn(body);
  var view=KingdomNpcService.available(world,npc).orElseThrow();when(view.displayName()).thenReturn(net.minecraft.network.chat.Component.literal("Simon"));
  var network=statics(net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.class);
  network.when(()->net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player,de.quest.network.FriendsQuestPayloads.View.ID)).thenReturn(true);
  network.when(()->net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(eq(player),any())).thenAnswer(call->{if(call.getArgument(1) instanceof de.quest.network.FriendsQuestPayloads.View v)shown=v;return null;});
  FriendsQuestUiService.openLore(world,player,npc,villageId+":0",lore);assertNotNull(shown);assertTrue(shown.valid());
 }
 @Test void raphaelServerConversationHasOnlyLoreAndNoQuestAction() {
  var view=KingdomNpcService.available(world,npc).orElseThrow();when(view.guest()).thenReturn(3);
  var lore=net.minecraft.network.chat.Component.translatable("message.village-quest.guest.ravel.music");conversation(lore);
  assertEquals("ravel",shown.who());assertEquals(lore,shown.dialogue());assertEquals(0,shown.chapter());assertFalse(shown.active());
  assertTrue(shown.options().isEmpty());assertTrue(shown.objectives().isEmpty());assertEquals(net.minecraft.network.chat.Component.empty(),shown.reward());
  FriendsQuestUiService.handle(player,new de.quest.network.FriendsQuestPayloads.Action(shown.session(),shown.revision(),"accept"));
  assertFalse(new FriendsQuestProgress(data).active("simon"));assertEquals(0,data.getCurrencyBalance());assertTrue(data.vanillaRewards().pending().isEmpty());
 }
 @Test void conversationTurnInReplayCannotConsumeOrRewardTwice() {
  new FriendsQuestProgress(data).accept("simon",0,0,0);
  for(int i=0;i<3;i++)when(inventory.getItem(i)).thenReturn(new ItemStack(List.of(Items.DANDELION,Items.POPPY,Items.FERN).get(i),8));
  conversation();var action=new de.quest.network.FriendsQuestPayloads.Action(shown.session(),shown.revision(),"claim");
  FriendsQuestUiService.handle(player,action);FriendsQuestUiService.handle(player,action);
  assertEquals(1,new FriendsQuestProgress(data).stage("simon"));assertEquals(10,data.getCurrencyBalance());assertEquals(3,data.vanillaRewards().pending().size());
 }
 @Test void staleConversationActionGetsAFreshReplyWithoutMutatingTheQuest() {
  conversation();
  // Opening snapshots carry initial=true; first move to an ordinary live snapshot.
  FriendsQuestUiService.handle(player,new de.quest.network.FriendsQuestPayloads.Action(shown.session(),0,"accept"));
  assertFalse(shown.initial());long revision=shown.revision();
  FriendsQuestUiService.handle(player,new de.quest.network.FriendsQuestPayloads.Action(shown.session(),revision-1,"accept"));
  assertTrue(shown.revision()>revision,"An unchanged snapshot must still acknowledge a rejected click so the client can unlock its controls");
  assertFalse(new FriendsQuestProgress(data).active("simon"));assertEquals(0,data.getCurrencyBalance());assertTrue(data.vanillaRewards().pending().isEmpty());
 }
 @Test void missingInnInvalidatesTheConversationBeforeAcceptance() {
  conversation();var action=new de.quest.network.FriendsQuestPayloads.Action(shown.session(),shown.revision(),"accept");
  when(kingdom.anchors().official(villageId,BuildingScanner.Role.INN)).thenReturn(Optional.empty());
  FriendsQuestUiService.handle(player,action);assertFalse(shown.valid());assertFalse(new FriendsQuestProgress(data).active("simon"));
 }
 @Test void losingSightInvalidatesTheConversationBeforeAcceptance() {
  conversation();var action=new de.quest.network.FriendsQuestPayloads.Action(shown.session(),shown.revision(),"accept");when(player.hasLineOfSight(npc)).thenReturn(false);
  FriendsQuestUiService.handle(player,action);assertFalse(shown.valid());assertFalse(new FriendsQuestProgress(data).active("simon"));
 }
 @Test void leavingRangeInvalidatesTheConversationBeforeAcceptance() {
  conversation();var action=new de.quest.network.FriendsQuestPayloads.Action(shown.session(),shown.revision(),"accept");when(player.distanceToSqr(npc)).thenReturn(65.0);
  FriendsQuestUiService.handle(player,action);assertFalse(shown.valid());assertFalse(new FriendsQuestProgress(data).active("simon"));
 }
 @Test void changedVisitInvalidatesTheConversationBeforeAcceptance() {
  conversation();var action=new de.quest.network.FriendsQuestPayloads.Action(shown.session(),shown.revision(),"accept");when(kingdom.tavernGuests().visitToken(villageId)).thenReturn(1L);
  FriendsQuestUiService.handle(player,action);assertFalse(shown.valid());assertFalse(new FriendsQuestProgress(data).active("simon"));
 }
 @Test void deliveryConsumesExactlyOnceAndKeepsOverflowRewardsInPersistentEscrow() {
  var p=new FriendsQuestProgress(data);p.accept("simon",0,0,0);
  var dandelion=new ItemStack(Items.DANDELION,9);var poppy=new ItemStack(Items.POPPY,8);var fern=new ItemStack(Items.FERN,8);
  when(inventory.getItem(0)).thenReturn(dandelion);when(inventory.getItem(1)).thenReturn(poppy);when(inventory.getItem(2)).thenReturn(fern);
  var encoded=new java.util.concurrent.atomic.AtomicReference<List<ItemStack>>();
  // Capture the actual native stacks before the persistence encoding boundary.
  var encoder=(MockedStatic<RewardItems>)mocks.getLast();
  encoder.when(()->RewardItems.encode(eq(world),anyList())).thenAnswer(call->{
   List<ItemStack> goods=call.getArgument(1);encoded.set(goods.stream().map(ItemStack::copy).toList());var tags=new ListTag();for(var ignored:goods)tags.add(new CompoundTag());return tags;
  });
  assertEquals(1,FriendsQuestService.command(player,body.toString(),"claim"));
  assertEquals(3,encoded.get().size());
  for(var potion:encoded.get()) {
   assertTrue(potion.is(Items.POTION));assertEquals(1,potion.getCount());
   assertEquals(net.minecraft.world.item.alchemy.Potions.STRONG_SWIFTNESS,potion.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS).potion().orElseThrow());
  }
  var effect=net.minecraft.world.item.alchemy.Potions.STRONG_SWIFTNESS.value().getEffects().getFirst();
  assertEquals(1,effect.getAmplifier());assertEquals(1800,effect.getDuration());
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
 @Test void masterpieceCostRequiresTwoDebrisAndConsumesExactAmountsWithoutTakingNetherite() {
  var debris=new ItemStack(Items.ANCIENT_DEBRIS,1);var redstone=new ItemStack(Items.REDSTONE,17);
  var amethyst=new ItemStack(Items.AMETHYST_SHARD,9);var ingot=new ItemStack(Items.NETHERITE_INGOT,1);
  when(inventory.getItem(0)).thenReturn(debris);when(inventory.getItem(1)).thenReturn(redstone);
  when(inventory.getItem(2)).thenReturn(amethyst);when(inventory.getItem(3)).thenReturn(ingot);
  assertFalse(FriendsQuestService.has(player,FriendsQuestService.forgeCosts()));
  debris.setCount(3);assertTrue(FriendsQuestService.has(player,FriendsQuestService.forgeCosts()));
  FriendsQuestService.consume(player,FriendsQuestService.forgeCosts());
  assertEquals(1,debris.getCount());assertEquals(1,redstone.getCount());assertEquals(1,amethyst.getCount());assertEquals(1,ingot.getCount());
 }
 @Test void unrelatedResidentDialogueDoesNotReadBiomeOrChangeQuestProgress() {
  FriendsQuestService.residentContact(world,player,UUID.randomUUID(),BlockPos.ZERO);
  verify(world,never()).getBiome(any());assertEquals(0,new FriendsQuestProgress(data).stage("simon"));
 }

}
