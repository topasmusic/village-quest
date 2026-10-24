package de.quest.quest.special;

import de.quest.data.PlayerQuestData;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.saveddata.maps.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

final class LutteChartTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        Items.FILLED_MAP.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,1).build());
    }
    private static ItemStack map(ServerLevel world,ServerPlayer player,FriendsQuestProgress p) throws Exception {
        Method method=LutteCoastalQuest.class.getDeclaredMethod("map",ServerLevel.class,ServerPlayer.class,FriendsQuestProgress.class);
        method.setAccessible(true);return (ItemStack)method.invoke(null,world,player,p);
    }
    @Test void everyTargetGetsItsOwnCrossAndAfterThreeSignalsChartDoesNotRetargetTheThird() throws Exception {
        var world=mock(ServerLevel.class);var player=mock(ServerPlayer.class);var id=UUID.randomUUID();when(player.getUUID()).thenReturn(id);
        var p=new FriendsQuestProgress(new PlayerQuestData());p.number("lutte.targets",3);p.number("lutte.generation",1);
        for(int i=0;i<3;i++){p.number("lutte.target."+i+".x",100+i*1024);p.number("lutte.target."+i+".z",200);}
        try(var maps=mockStatic(MapItem.class);var decorations=mockStatic(MapItemSavedData.class)) {
            maps.when(()->MapItem.create(eq(world),anyInt(),anyInt(),eq((byte)2),eq(true),eq(true)))
                    .thenAnswer(c->new ItemStack(Items.FILLED_MAP));
            for(int i=0;i<3;i++) {
                p.number("lutte.signals",i);var chart=map(world,player,p);
                assertEquals(i,chart.get(DataComponents.CUSTOM_DATA).copyTag().getIntOr("target",-99));
                final int x=100+i*1024;
                decorations.verify(()->MapItemSavedData.addTargetDecoration(eq(chart),eq(new BlockPos(x,0,200)),eq("+"),eq(MapDecorationTypes.RED_X)));
            }
            p.number("lutte.signals",3);var done=map(world,player,p);
            assertEquals(-2,done.get(DataComponents.CUSTOM_DATA).copyTag().getIntOr("target",-99));
            decorations.verifyNoMoreInteractions();
        }
    }
    @Test void pendingAndKnownChartsUseSeparateShortLoreLines() throws Exception {
        var world=mock(ServerLevel.class);var player=mock(ServerPlayer.class);when(player.getUUID()).thenReturn(UUID.randomUUID());
        var p=new FriendsQuestProgress(new PlayerQuestData());p.number("lutte.generation",1);
        try(var maps=mockStatic(MapItem.class);var decorations=mockStatic(MapItemSavedData.class)) {
            maps.when(()->MapItem.create(eq(world),anyInt(),anyInt(),eq((byte)2),eq(true),eq(true))).thenAnswer(c->new ItemStack(Items.FILLED_MAP));
            for(int found:new int[]{0,1}) {
                p.number("lutte.targets",found);var lore=map(world,player,p).get(DataComponents.LORE);
                assertTrue(lore.lines().size()>=3,"Instructions and coordinates must be distinct tooltip rows");
                for(String language:List.of("en_us","de_de","es_es")) {
                    try(var reader=new java.io.InputStreamReader(getClass().getResourceAsStream("/assets/village-quest/lang/"+language+".json"),java.nio.charset.StandardCharsets.UTF_8)) {
                        var catalog=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                        for(var line:lore.lines()) {
                            var translated=(net.minecraft.network.chat.contents.TranslatableContents)line.getContents();
                            var rendered=String.format(Locale.ROOT,catalog.get(translated.getKey()).getAsString(),translated.getArgs());
                            assertTrue(rendered.length()<=48,language+": oversized tooltip row: "+rendered);
                        }
                    }
                }
            }
        }
    }

    @Test void oldChartRefreshesLoreOnceEvenWhenItsTargetHasNotChanged() throws Exception {
        var world=mock(ServerLevel.class);var player=mock(ServerPlayer.class);var id=UUID.randomUUID();when(player.getUUID()).thenReturn(id);
        var inventory=mock(net.minecraft.world.entity.player.Inventory.class);when(player.getInventory()).thenReturn(inventory);when(inventory.getContainerSize()).thenReturn(1);
        var menu=mock(net.minecraft.world.inventory.InventoryMenu.class);
        var menuField=net.minecraft.world.entity.player.Player.class.getDeclaredField("inventoryMenu");menuField.setAccessible(true);menuField.set(player,menu);
        var p=new FriendsQuestProgress(new PlayerQuestData());p.number("lutte.generation",1);
        try(var maps=mockStatic(MapItem.class);var decorations=mockStatic(MapItemSavedData.class)) {
            maps.when(()->MapItem.create(eq(world),anyInt(),anyInt(),eq((byte)2),eq(true),eq(true))).thenAnswer(c->new ItemStack(Items.FILLED_MAP));
            var old=map(world,player,p);net.minecraft.world.item.component.CustomData.update(DataComponents.CUSTOM_DATA,old,tag->tag.remove("chartVersion"));
            var slot=new java.util.concurrent.atomic.AtomicReference<>(old);when(inventory.getItem(0)).thenAnswer(c->slot.get());
            doAnswer(c->{slot.set(c.getArgument(1));return null;}).when(inventory).setItem(eq(0),any(ItemStack.class));
            LutteCoastalQuest.updateMap(world,player,p);assertNotSame(old,slot.get());assertTrue(slot.get().get(DataComponents.LORE).lines().size()>=3);
            var refreshed=slot.get();LutteCoastalQuest.updateMap(world,player,p);assertSame(refreshed,slot.get());verify(menu,times(1)).broadcastChanges();
        }
    }
}
