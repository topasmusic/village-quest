package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.VillageUiTheme;
import de.quest.network.FriendsQuestPayloads.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.*;

final class FriendsQuestScreenTest {
    @BeforeAll static void bootstrap(){
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM)item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64).set(net.minecraft.core.component.DataComponents.ENCHANTMENTS,net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).set(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS,net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).set(net.minecraft.core.component.DataComponents.ITEM_NAME,Component.translatable(item.getDescriptionId())).build());
    }
    private static int field(FriendsQuestScreen s,String name)throws Exception{var f=FriendsQuestScreen.class.getDeclaredField(name);f.setAccessible(true);return f.getInt(s);}
    @Test void allTourStatesAreIsolatedAndEveryArtworkIsPackaged() {
        for(var step:de.quest.commands.UiTestTourSteps.ALL.subList(0,12)) {
            assertEquals("friends",step.family());var view=FriendsQuestPreview.view(step.state());
            assertEquals(new UUID(0,77),view.session());assertTrue(view.options().size()<=3);
            var art=FriendsQuestScreen.artwork(view.who(),view.chapter());assertNotNull(getClass().getResource("/assets/village-quest/"+art.getPath()),step.state());
        }
    }
    @Test void raphaelShowsHisHarpAndCompositionLoreWithoutAnyQuestOrReward() {
        var view=FriendsQuestPreview.view("ravel_lore");
        assertEquals(0,view.chapter());assertFalse(view.active());assertTrue(view.objectives().isEmpty());assertTrue(view.options().isEmpty());assertTrue(view.progress().isEmpty());assertEquals(Component.empty(),view.reward());
        assertTrue(view.dialogue().getSiblings().stream().anyMatch(c->c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().equals("message.village-quest.guest.ravel.music")));
        var image=FriendsQuestScreen.artwork("ravel",0);assertTrue(image.getPath().endsWith("/raphael_harp.png"));
        var area=de.quest.client.ui.QuestArtwork.contentArea(image);assertEquals(768,area.textureWidth());assertEquals(288,area.textureHeight());
    }
    @Test void longLocalizedContentReachesItsFinalLineWithoutMovingTheFooterAtEveryViewport()throws Exception {
        try(var client=mockStatic(Minecraft.class);var tour=mockStatic(UiTestTour.class);var theme=mockStatic(VillageUiTheme.class,CALLS_REAL_METHODS)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));tour.when(UiTestTour::active).thenReturn(true);
            for(String locale:List.of("en_us","de_de","es_es")) {
                var catalog=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(getClass().getResourceAsStream("/assets/village-quest/lang/"+locale+".json"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                String prose=catalog.get("quest.village-quest.friends.lutte.2.objective").getAsString()+" "+catalog.get("quest.village-quest.friends.lutte.2.objective.rules").getAsString();
                for(var size:new int[][]{{1000,700},{480,270},{320,200}})for(int glyph:List.of(5,8)) {
                    var base=FriendsQuestPreview.view("lutte_map");
                    var v=new View(base.session(),0,true,true,base.who(),2,true,base.name(),base.title(),Component.literal(prose),List.of(Component.literal(prose.repeat(4))),base.reward(),List.of(Component.literal("QA_FINAL_LINE")),base.progress(),base.options());
                    var s=new FriendsQuestScreen(v);s.width=size[0];s.height=size[1];
                    var font=mock(Font.class);when(font.width(anyString())).thenAnswer(a->((String)a.getArgument(0)).length()*glyph);
                    when(font.plainSubstrByWidth(anyString(),anyInt())).thenAnswer(a->((String)a.getArgument(0)).substring(0,Math.min(((String)a.getArgument(0)).length(),Math.max(0,(int)a.getArgument(1)/glyph))));
                    var ff=Screen.class.getDeclaredField("font");ff.setAccessible(true);ff.set(s,font);var lh=Font.class.getDeclaredField("lineHeight");lh.setAccessible(true);lh.setInt(font,9);
                    var g=mock(GuiGraphics.class);when(g.pose()).thenReturn(new Matrix3x2fStack(32));
                    var finalY=new java.util.concurrent.atomic.AtomicReference<Float>();
                    theme.when(()->VillageUiTheme.drawStringScaled(eq(g),eq(font),eq("QA_FINAL_LINE"),anyFloat(),anyFloat(),anyInt(),anyFloat())).thenAnswer(a->{finalY.set(a.getArgument(4));return null;});
                    s.render(g,0,0,0);assertTrue(field(s,"maxScroll")>0);
                    s.keyPressed(new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_END,0,0));s.render(g,0,0,0);
                    int top=(s.height-FriendsQuestScreen.H)/2;
                    assertNotNull(finalY.get());assertTrue(finalY.get()>=top+FriendsQuestScreen.TOP);assertTrue(finalY.get()+9<=top+FriendsQuestScreen.bodyBottom(s.panelHeight()),locale+" final line must clear the footer");
                    assertEquals(field(s,"maxScroll"),field(s,"scroll"));
                }
            }
        }
    }
    @Test void footerActionsStayBoundedAndClearOfTheFrameAndCornerOrnaments() {
        for(int h:List.of(270,340,400,430))for(int options=0;options<=3;options++) {
            var buttons=FriendsQuestScreen.footerBounds(0,0,h,options);assertEquals(options+1,buttons.size());
            for(int i=0;i<buttons.size();i++) {
                var b=buttons.get(i);assertTrue(b.x()>=84);assertTrue(b.x()+b.width()<=FriendsQuestScreen.W-48);
                assertTrue(b.width()<=152);assertTrue(b.y()+b.height()<=h-37);
                assertTrue(h-79>=FriendsQuestScreen.bodyBottom(h)+10);assertTrue(h-79+9<b.y(),"Hint must have its own row above actions");
                if(i>0)assertTrue(buttons.get(i-1).x()+buttons.get(i-1).width()+8<=b.x());
            }
        }
        assertEquals("Accept quest",FriendsQuestScreen.actionLabel(Component.literal("[Accept quest]")));
        assertEquals("Abgeben",FriendsQuestScreen.actionLabel(Component.literal("[Abgeben]")));
        assertEquals("Entregar",FriendsQuestScreen.actionLabel(Component.literal("[Entregar]")));
    }
    @Test void allFriendsStatesKeepTheSameFixedPanelAndQuestArtMatchesTheActualActions()throws Exception {
        try(var client=mockStatic(Minecraft.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var s=new FriendsQuestScreen(FriendsQuestPreview.view("ravel_lore"));var font=mock(Font.class);
            when(font.width(anyString())).thenAnswer(a->((String)a.getArgument(0)).length()*6);
            var f=Screen.class.getDeclaredField("font");f.setAccessible(true);f.set(s,font);
            for(var step:de.quest.commands.UiTestTourSteps.ALL.subList(0,12)) {
                s=new FriendsQuestScreen(FriendsQuestPreview.view(step.state()));f.set(s,font);
                assertEquals(430,s.panelHeight(),step.state()+": fixed geometry must not jump between guests or phases");
            }
        }
        assertTrue(FriendsQuestScreen.artwork("lutte",2).getPath().endsWith("friends_coastal_signals.png"));
        assertTrue(FriendsQuestScreen.artwork("marius",2).getPath().endsWith("friends_golem_repair.png"));
    }
    @Test void rewardIconsUseCrownDenominationsNativePotionsAndNoPhysicalXpBottle() {
        var crown=de.quest.registry.ModItems.CROWN;var silver=de.quest.registry.ModItems.SILVERMARK;
        de.quest.registry.ModItems.CROWN=net.minecraft.world.item.Items.GOLD_NUGGET;de.quest.registry.ModItems.SILVERMARK=net.minecraft.world.item.Items.IRON_NUGGET;
        try {
            var registries=net.minecraft.data.registries.VanillaRegistries.createWorldLookup();
            var view=FriendsQuestPreview.view("simon_offer",registries);var rows=FriendsQuestScreen.rewardRows(view);
            assertEquals(3,rows.size());assertTrue(rows.getFirst().icon().is(net.minecraft.world.item.Items.GOLD_NUGGET));assertEquals(1,rows.getFirst().icon().getCount());
            assertTrue(rows.get(1).icon().isEmpty(),"XP has its own bar, never a bottle reward");
            assertEquals(net.minecraft.world.item.alchemy.Potions.STRONG_SWIFTNESS,rows.get(2).icon().get(net.minecraft.core.component.DataComponents.POTION_CONTENTS).potion().orElseThrow());
            assertTrue(rows.get(2).text().startsWith("3 × "));
            var lutte=FriendsQuestScreen.rewardRows(FriendsQuestPreview.view("lutte_masterpiece",registries));
            assertEquals(1,lutte.size());assertEquals(2,lutte.getFirst().icons().size(),"Both native tools can be inspected inside ONE exclusive choice");
            var money=FriendsQuestScreen.rewardRows(FriendsQuestPreview.view("lutte_offer",registries));
            assertEquals(3,money.size());assertTrue(money.get(0).icon().is(net.minecraft.world.item.Items.GOLD_NUGGET));assertEquals(1,money.get(0).icon().getCount());
            assertTrue(money.get(1).icon().is(net.minecraft.world.item.Items.IRON_NUGGET));assertEquals(2,money.get(1).icon().getCount());
        } finally {de.quest.registry.ModItems.CROWN=crown;de.quest.registry.ModItems.SILVERMARK=silver;}
    }
    @Test void experienceUsesTheSelectedLanguageDecimalAndNeverAddsAnUnwantedFraction() {
        assertEquals("1.5",FriendsQuestScreen.experienceNumber(1.5,"en_us"));
        assertEquals("1,5",FriendsQuestScreen.experienceNumber(1.5,"de_de"));
        assertEquals("1,5",FriendsQuestScreen.experienceNumber(1.5,"es_es"));
        for(String language:List.of("en_us","de_de","es_es"))assertEquals("3",FriendsQuestScreen.experienceNumber(3,language));
    }
    @Test void tooltipHitTestingFollowsScrollAndClipsItemsAboveBodyAndBelowFooter() {
        var stack=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_PICKAXE);
        var hits=List.of(new FriendsQuestScreen.ItemHit(stack,40,70,100,40),new FriendsQuestScreen.ItemHit(stack,40,325,100,40));
        assertTrue(FriendsQuestScreen.hoveredItem(hits,45,83,84,336).isEmpty());
        assertSame(stack,FriendsQuestScreen.hoveredItem(hits,45,84,84,336));
        assertSame(stack,FriendsQuestScreen.hoveredItem(hits,45,335,84,336));
        assertTrue(FriendsQuestScreen.hoveredItem(hits,45,336,84,336).isEmpty());
        assertTrue(FriendsQuestScreen.hoveredItem(hits,140,100,84,336).isEmpty());
    }
    private static Component localized(Component c,com.google.gson.JsonObject catalog) {
        if(c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && catalog.has(t.getKey())) {
            var args=Arrays.stream(t.getArgs()).map(a->a instanceof Component text?localized(text,catalog).getString():a).toArray();
            return Component.literal(String.format(Locale.ROOT,catalog.get(t.getKey()).getAsString(),args));
        }
        return c;
    }
    @Test void normalPlantAndHealingViewsShowCompleteRewardCardsWithoutASpuriousScrollbar()throws Exception {
        try(var client=mockStatic(Minecraft.class);var tour=mockStatic(UiTestTour.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));tour.when(UiTestTour::active).thenReturn(true);
            for(String locale:List.of("en_us","de_de","es_es")) {
                var catalog=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(getClass().getResourceAsStream("/assets/village-quest/lang/"+locale+".json"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for(String state:List.of("simon_offer","marius_healing")) {
                    var b=FriendsQuestPreview.view(state);
                    var v=new View(b.session(),0,true,true,b.who(),b.chapter(),b.active(),localized(b.name(),catalog),localized(b.title(),catalog),localized(b.dialogue(),catalog),b.objectives().stream().map(c->localized(c,catalog)).toList(),localized(b.reward(),catalog),b.notices().stream().map(c->localized(c,catalog)).toList(),b.progress(),b.options());
                    var s=new FriendsQuestScreen(v);s.width=1000;s.height=700;var font=mock(Font.class);when(font.width(anyString())).thenAnswer(a->((String)a.getArgument(0)).length()*6);
                    when(font.plainSubstrByWidth(anyString(),anyInt())).thenAnswer(a->a.getArgument(0));
                    var f=Screen.class.getDeclaredField("font");f.setAccessible(true);f.set(s,font);var lh=Font.class.getDeclaredField("lineHeight");lh.setAccessible(true);lh.setInt(font,9);
                    var g=mock(GuiGraphics.class);when(g.pose()).thenReturn(new Matrix3x2fStack(32));s.render(g,0,0,0);
                    assertEquals(0,field(s,"maxScroll"),locale+" "+state+": normal reward cards must fit and no 4px-only scrolling may appear");
                }
            }
        }
    }
}
