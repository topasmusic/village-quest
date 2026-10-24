package de.quest.quest.special;
import de.quest.content.item.WeatherTonicItem;
import de.quest.data.QuestState;
import de.quest.reward.VanillaRewardService;
import java.util.*;
import net.minecraft.core.component.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** Calls native Item.use/finishUsingItem with mocked external boundaries; no game execution. */
final class WeatherTonicUseTest {
 private ServerLevel world;private ServerPlayer player;private QuestState quests;private WeatherTonicState tonic;
 private WeatherTonicItem item;private ItemStack stack;private Consumable consume;private Abilities abilities;
 private MockedStatic<QuestState> stateMock;private MockedStatic<FriendsQuestService> friends;private MockedStatic<VanillaRewardService> rewards;
 @BeforeAll static void bootstrap() {
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  var components=DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build();
  for(var it:net.minecraft.core.registries.BuiltInRegistries.ITEM)it.builtInRegistryHolder().bindComponents(components);
 }
 @BeforeEach void setup() {
  world=mock(ServerLevel.class);player=mock(ServerPlayer.class);quests=mock(QuestState.class);tonic=new WeatherTonicState();
  var server=mock(MinecraftServer.class);when(world.getServer()).thenReturn(server);when(world.dimension()).thenReturn(Level.OVERWORLD);
  when(player.isAlive()).thenReturn(true);abilities=new Abilities();when(player.getAbilities()).thenReturn(abilities);
  stack=mock(ItemStack.class);consume=mock(Consumable.class);when(stack.get(DataComponents.CONSUMABLE)).thenReturn(consume);
  when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(stack);
  when(consume.startConsuming(player,stack,InteractionHand.MAIN_HAND)).thenReturn(InteractionResult.CONSUME);
  when(consume.onConsume(world,player,stack)).thenAnswer(c->{if(!abilities.instabuild)stack.shrink(1);return stack;});
  item=mock(WeatherTonicItem.class,CALLS_REAL_METHODS);when(quests.weatherTonic()).thenReturn(tonic);
  stateMock=mockStatic(QuestState.class);stateMock.when(()->QuestState.get(server)).thenReturn(quests);
  friends=mockStatic(FriendsQuestService.class,CALLS_REAL_METHODS);
  friends.when(()->FriendsQuestService.enqueue(eq(world),eq(player),anyList())).thenReturn(true);
  friends.when(()->FriendsQuestService.applyWeather(world,tonic)).thenAnswer(c->null);
  rewards=mockStatic(VanillaRewardService.class);
 }
 @AfterEach void close(){rewards.close();friends.close();stateMock.close();}
 @Test void clickOnlyStartsNativeDrinkingAndAbortingHasNoEffectOrCost() {
  assertSame(InteractionResult.CONSUME,item.use(world,player,InteractionHand.MAIN_HAND));
  verify(consume).startConsuming(player,stack,InteractionHand.MAIN_HAND);assertEquals(0,tonic.remaining());
  verify(consume,never()).onConsume(any(),any(),any());verify(stack,never()).shrink(anyInt());
  friends.verify(()->FriendsQuestService.enqueue(any(),any(),anyList()),never());
 }
 @Test void vanillaDrinkComponentStartsTheActualUseAnimation() {
  when(stack.get(DataComponents.CONSUMABLE)).thenReturn(Consumables.DEFAULT_DRINK);
  assertSame(InteractionResult.CONSUME,item.use(world,player,InteractionHand.MAIN_HAND));
  verify(player).startUsingItem(InteractionHand.MAIN_HAND);
  assertEquals(ItemUseAnimation.DRINK,Consumables.DEFAULT_DRINK.animation());
  assertTrue(Consumables.DEFAULT_DRINK.consumeTicks()>0);assertEquals(0,tonic.remaining());
 }
 @Test void completionCommitsWeatherOneBottleAndExactlyOneNativeConsumption() {
  item.finishUsingItem(stack,world,player);assertEquals(24000,tonic.remaining());assertTrue(tonic.rain());
  verify(consume,times(1)).onConsume(world,player,stack);verify(stack,times(1)).shrink(1);
  friends.verify(()->FriendsQuestService.enqueue(eq(world),eq(player),argThat(goods->goods.size()==1 && goods.get(0).is(Items.GLASS_BOTTLE))),times(1));
  rewards.verify(()->VanillaRewardService.deliverItems(world,player),times(1));
  item.finishUsingItem(stack,world,player);verify(consume,times(1)).onConsume(world,player,stack);
 }
 @Test void anotherDrinkWinningDuringAnimationOrDimensionChangeCostsNothing() {
  item.use(world,player,InteractionHand.MAIN_HAND);tonic.start(true);tonic.tick();
  item.finishUsingItem(stack,world,player);assertEquals(23999,tonic.remaining());
  verify(consume,never()).onConsume(any(),any(),any());friends.verify(()->FriendsQuestService.enqueue(any(),any(),anyList()),never());
  verify(player).sendSystemMessage(eq(FriendsQuestService.tr("weather.remaining","20:00")),eq(false));
 }
 @Test void changingDimensionBeforeFinishRejectsWithoutBottleOrConsumption() {
  item.use(world,player,InteractionHand.MAIN_HAND);when(world.dimension()).thenReturn(Level.NETHER);
  item.finishUsingItem(stack,world,player);assertEquals(0,tonic.remaining());verify(consume,never()).onConsume(any(),any(),any());
 }
 @Test void fullRewardEscrowRejectsCompletionWithoutWeatherOrConsumption() {
  friends.when(()->FriendsQuestService.enqueue(eq(world),eq(player),anyList())).thenReturn(false);
  assertSame(stack,item.finishUsingItem(stack,world,player));assertEquals(0,tonic.remaining());verify(consume,never()).onConsume(any(),any(),any());
 }
 @Test void creativeHasNoBottleOrAdditionalConsumption() {
  abilities.instabuild=true;item.finishUsingItem(stack,world,player);assertEquals(24000,tonic.remaining());
  friends.verify(()->FriendsQuestService.enqueue(any(),any(),anyList()),never());verify(stack,never()).shrink(anyInt());
 }
 @Test void clientPredictionDelegatesToNativeDrinkWithoutWorldMutation() {
  var client=mock(Level.class);item.use(client,player,InteractionHand.MAIN_HAND);
  verify(consume).startConsuming(player,stack,InteractionHand.MAIN_HAND);assertEquals(0,tonic.remaining());verifyNoInteractions(quests);
 }
 @Test void clientCompletionKeepsTheStackUntilAuthoritativeServerInventoryArrives() {
  var client=mock(Level.class);assertSame(stack,item.finishUsingItem(stack,client,player));
  verify(consume,never()).onConsume(any(),any(),any());
  verify(consume).emitParticlesAndSounds(any(),eq(player),eq(stack),eq(16));verify(stack,never()).shrink(anyInt());
 }
 @Test void countdownRoundsUpPartialSeconds() {
  assertEquals("0:01",MariusQuestService.activeTime(1));assertEquals("1:00",MariusQuestService.activeTime(1200));assertEquals("1:01",MariusQuestService.activeTime(1201));
 }
}
