package de.quest.data;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import java.util.List;
import static org.mockito.Mockito.*;
final class PersonalResetItemsTest {
 @BeforeAll static void boot(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
  var components=net.minecraft.core.component.DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build();
  for(var item:List.of(Items.DIAMOND,Items.FILLED_MAP,Items.FEATHER,Items.PAPER,Items.SHULKER_BOX,Items.IRON_INGOT)) item.builtInRegistryHolder().bindComponents(components);
 }
 @Test void removesOnlyModOrProvenVqIdentityNeverOrdinaryNamedVanilla(){
  var normal=new ItemStack(Items.DIAMOND,12);normal.set(DataComponents.CUSTOM_NAME,Component.literal("My VQ diamonds"));
  assertFalse(PersonalResetItems.isVq(normal));assertEquals(12,PersonalResetItems.cleanForTest(normal).getCount());
  var map=new ItemStack(Items.FILLED_MAP);CustomData.update(DataComponents.CUSTOM_DATA,map,t->t.putString("village_quest_map","lutte"));
  assertTrue(PersonalResetItems.cleanForTest(map).isEmpty());
  var token=new ItemStack(Items.FEATHER);token.set(DataComponents.ITEM_NAME,Component.translatable("quest.village-quest.friends.simon.keepsake"));
  assertTrue(PersonalResetItems.cleanForTest(token).isEmpty());
 }
 @Test void nestedVanillaContainersKeepTheirOrdinaryContentsAndOriginalInput(){
  var vq=new ItemStack(Items.PAPER);CustomData.update(DataComponents.CUSTOM_DATA,vq,t->t.putString("vq_archive_id","ledger"));
  var box=new ItemStack(Items.SHULKER_BOX);box.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(new ItemStack(Items.IRON_INGOT,19),vq)));
  var clean=PersonalResetItems.cleanForTest(box);var contents=clean.get(DataComponents.CONTAINER).allItemsCopyStream().toList();
  // Native ItemContainerContents omits trailing empty slots. Check the retained
  // contents rather than indexing a removed second entry.
  assertEquals(1,contents.size());assertTrue(contents.getFirst().is(Items.IRON_INGOT));
  assertEquals(19,contents.getFirst().getCount());
  assertFalse(box.get(DataComponents.CONTAINER).allItemsCopyStream().toList().get(1).isEmpty());
 }
 @Test void staleInventoryRefusesBeforeWritingAnyOfTheFourStorageSurfaces() throws Exception {
  var f=new Fixture(); f.inventory[0]=new ItemStack(Items.DIAMOND,12);
  f.ender[0]=new ItemStack(Items.PAPER);CustomData.update(DataComponents.CUSTOM_DATA,f.ender[0],t->t.putString("vq_archive_id","ledger"));
  var plan=PersonalResetItems.plan(f.player);
  f.inventory[0]=new ItemStack(Items.DIAMOND,13);
  assertThrows(IllegalStateException.class,()->plan.apply(f.player));
  assertEquals(13,f.inventory[0].getCount());assertFalse(f.ender[0].isEmpty());
  verify(f.bag,never()).setItem(anyInt(),any());verify(f.chest,never()).setItem(anyInt(),any());
 }
 @Test void removedQuestContainerRescuesVanillaContentsAndPlanCopiesCannotRewriteThem() throws Exception {
  var f=new Fixture(); var box=new ItemStack(Items.SHULKER_BOX);
  CustomData.update(DataComponents.CUSTOM_DATA,box,t->t.putString("vq_archive_id","storage"));
  box.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(new ItemStack(Items.IRON_INGOT,19))));
  f.ender[0]=box;
  var plan=PersonalResetItems.plan(f.player);
  plan.inventory().get(0).setCount(1);
  plan.apply(f.player);
  assertEquals(19,f.inventory[0].getCount());assertTrue(f.ender[0].isEmpty());
  assertEquals(19,box.get(DataComponents.CONTAINER).allItemsCopyStream().findFirst().orElseThrow().getCount());
 }
 @Test void changingEnderCursorOrCraftingAlsoRefusesBeforeInventoryWrites() throws Exception {
  for(int surface=0;surface<3;surface++) {
   var f=new Fixture();f.inventory[0]=new ItemStack(Items.PAPER);
   CustomData.update(DataComponents.CUSTOM_DATA,f.inventory[0],t->t.putString("vq_archive_id","ledger"));
   var plan=PersonalResetItems.plan(f.player);
   if(surface==0)f.ender[0]=new ItemStack(Items.DIAMOND);
   if(surface==1)when(f.player.inventoryMenu.getCarried()).thenReturn(new ItemStack(Items.DIAMOND));
   if(surface==2)f.player.inventoryMenu.getSlot(1).set(new ItemStack(Items.DIAMOND));
   assertThrows(IllegalStateException.class,()->plan.apply(f.player));
   assertFalse(f.inventory[0].isEmpty());verify(f.bag,never()).setItem(anyInt(),any());verify(f.chest,never()).setItem(anyInt(),any());
  }
 }
 @Test void insufficientRescueSpaceLeavesEveryOriginalStackUntouched() throws Exception {
  var f=new Fixture();for(int i=0;i<36;i++)f.inventory[i]=new ItemStack(Items.DIAMOND,64);
  var box=new ItemStack(Items.SHULKER_BOX);CustomData.update(DataComponents.CUSTOM_DATA,box,t->t.putString("vq_archive_id","storage"));
  box.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(new ItemStack(Items.IRON_INGOT,19))));f.ender[0]=box;
  assertThrows(IllegalStateException.class,()->PersonalResetItems.plan(f.player));
  assertSame(box,f.ender[0]);assertEquals(64,f.inventory[0].getCount());
  verify(f.bag,never()).setItem(anyInt(),any());verify(f.chest,never()).setItem(anyInt(),any());
 }
 @Test void nativePlanRoundTripKeepsExactFrozenItemsAndRejectsForgedPostimage() throws Exception {
  var f=new Fixture();f.inventory[0]=new ItemStack(Items.DIAMOND,12);
  f.ender[0]=new ItemStack(Items.PAPER);CustomData.update(DataComponents.CUSTOM_DATA,f.ender[0],t->t.putString("vq_archive_id","ledger"));
  var registries=net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
  var saved=PersonalResetItems.plan(f.player).toNbt(registries);
  var restored=PersonalResetItems.fromNbt(saved,registries).orElseThrow();
  assertTrue(restored.matchesBefore(f.player));restored.apply(f.player);assertTrue(restored.matchesAfter(f.player));
  assertEquals(12,f.inventory[0].getCount());assertTrue(f.ender[0].isEmpty());
  var forged=saved.copy();forged.getCompoundOrEmpty("after").getListOrEmpty("inventory").set(0,new net.minecraft.nbt.CompoundTag());
  assertTrue(PersonalResetItems.fromNbt(forged,registries).isEmpty());
  var future=saved.copy();future.putInt("schema",999);assertTrue(PersonalResetItems.fromNbt(future,registries).isEmpty());
 }
 @Test void nativePlanRetainsCursorAndCraftingAndCannotBeAppliedToAnotherOwner() throws Exception {
  var f=new Fixture();f.player.inventoryMenu.getSlot(2).set(new ItemStack(Items.IRON_INGOT,7));
  when(f.player.inventoryMenu.getCarried()).thenReturn(new ItemStack(Items.DIAMOND,3));
  var registries=net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
  var restored=PersonalResetItems.fromNbt(PersonalResetItems.plan(f.player).toNbt(registries),registries).orElseThrow();
  assertEquals(7,restored.crafting().get(1).getCount());assertEquals(3,restored.cursor().getCount());
  when(f.player.getUUID()).thenReturn(java.util.UUID.randomUUID());
  assertFalse(restored.matchesBefore(f.player));assertThrows(IllegalStateException.class,()->restored.apply(f.player));
  verify(f.bag,never()).setItem(anyInt(),any());
 }
 @Test void normalizationMovesCompleteTransientItemsWithoutRemovingVqBeforeTheDurableCheckpoint() throws Exception {
  var f=new Fixture();f.player.inventoryMenu.getSlot(1).set(new ItemStack(Items.IRON_INGOT,9));
  var map=new ItemStack(Items.PAPER);CustomData.update(DataComponents.CUSTOM_DATA,map,t->t.putString("vq_archive_id","ledger"));
  f.player.inventoryMenu.setCarried(map);
  PersonalResetItems.normalizeTransient(f.player);
  assertTrue(PersonalResetItems.nativeStorageOnly(f.player));
  assertEquals(9,java.util.Arrays.stream(f.inventory).filter(s->s.is(Items.IRON_INGOT)).mapToInt(ItemStack::getCount).sum());
  assertEquals(1,java.util.Arrays.stream(f.inventory).filter(PersonalResetItems::isVq).count());
  var registries=net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
  var frozen=PersonalResetItems.plan(f.player).toNbt(registries);
  var recovered=PersonalResetItems.fromNbt(frozen,registries).orElseThrow();
  recovered.apply(f.player);assertTrue(recovered.matchesAfter(f.player));assertTrue(PersonalResetItems.nativeStorageOnly(f.player));
  assertEquals(0,java.util.Arrays.stream(f.inventory).filter(PersonalResetItems::isVq).count());
 }
 @Test void normalizationRefusesIfFullBeforeContentsDoNotFitEvenWhenResetWouldFreeSpace() throws Exception {
  var f=new Fixture();for(int i=0;i<36;i++)f.inventory[i]=new ItemStack(Items.DIAMOND,64);
  CustomData.update(DataComponents.CUSTOM_DATA,f.inventory[0],t->t.putString("vq_archive_id","ledger"));
  f.player.inventoryMenu.getSlot(1).set(new ItemStack(Items.IRON_INGOT,9));
  assertThrows(IllegalStateException.class,()->PersonalResetItems.normalizeTransient(f.player));
  verify(f.bag,never()).setItem(anyInt(),any());assertEquals(9,f.player.inventoryMenu.getSlot(1).getItem().getCount());
 }
 static final class Fixture {
  final net.minecraft.server.level.ServerPlayer player=mock(net.minecraft.server.level.ServerPlayer.class,
          withSettings().extraInterfaces(net.fabricmc.fabric.api.attachment.v1.AttachmentTarget.class));
  final net.minecraft.world.entity.player.Inventory bag=mock(net.minecraft.world.entity.player.Inventory.class);
  final net.minecraft.world.inventory.PlayerEnderChestContainer chest=mock(net.minecraft.world.inventory.PlayerEnderChestContainer.class);
  final ItemStack[] inventory=new ItemStack[41],ender=new ItemStack[27];
  Fixture() throws Exception {
   when(player.getUUID()).thenReturn(java.util.UUID.randomUUID());
   java.util.Arrays.fill(inventory,ItemStack.EMPTY);java.util.Arrays.fill(ender,ItemStack.EMPTY);
   when(player.getInventory()).thenReturn(bag);when(player.getEnderChestInventory()).thenReturn(chest);
   when(bag.getContainerSize()).thenReturn(41);when(chest.getContainerSize()).thenReturn(27);
   when(bag.getItem(anyInt())).thenAnswer(c->inventory[c.getArgument(0,Integer.class)]);
   when(chest.getItem(anyInt())).thenAnswer(c->ender[c.getArgument(0,Integer.class)]);
   doAnswer(c->{inventory[c.getArgument(0,Integer.class)]=c.getArgument(1);return null;}).when(bag).setItem(anyInt(),any());
   doAnswer(c->{ender[c.getArgument(0,Integer.class)]=c.getArgument(1);return null;}).when(chest).setItem(anyInt(),any());
   var menu=mock(net.minecraft.world.inventory.InventoryMenu.class);
   var field=net.minecraft.world.entity.player.Player.class.getField("inventoryMenu");field.setAccessible(true);field.set(player,menu);player.containerMenu=menu;
   var crafting=new net.minecraft.world.SimpleContainer(4);
   for(int i=1;i<=4;i++)when(menu.getSlot(i)).thenReturn(new net.minecraft.world.inventory.Slot(crafting,i-1,0,0));
   var carried=new java.util.concurrent.atomic.AtomicReference<ItemStack>(ItemStack.EMPTY);
   when(menu.getCarried()).thenAnswer(c->carried.get());
   doAnswer(c->{carried.set(c.getArgument(0));return null;}).when(menu).setCarried(any());
  }
 }
}
