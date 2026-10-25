package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.client.compat.GuiGraphics;
import de.quest.client.ui.*;
import de.quest.commands.UiTestTourSteps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Items;
import java.util.List;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.Test;

final class FamilyV3LayoutTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(var item:List.of(Items.BOOK,Items.BARREL,Items.OAK_FENCE,Items.HAY_BLOCK,Items.WHEAT))item.builtInRegistryHolder().bindComponents(
            net.minecraft.core.component.DataComponentMap.builder().set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64)
            .set(net.minecraft.core.component.DataComponents.ITEM_NAME,net.minecraft.network.chat.Component.translatable(item.getDescriptionId())).build());
    }
    private static int number(Class<?> type,String name)throws Exception {var f=type.getDeclaredField(name);f.setAccessible(true);return f.getInt(null);}
    @Test void marketHasRoomForTableAndSeparatePriceDetailsWithoutRailCollisions()throws Exception {
        assertTrue(number(MarketScreen.class,"W")>=540,"V3 table and price panel need separately reserved columns");
        try(var client=mockStatic(Minecraft.class);var theme=mockStatic(VillageUiTheme.class);var art=mockStatic(KingdomUiAssets.class);var family=mockStatic(JournalV3Visuals.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            art.when(()->KingdomUiAssets.closeBounds(anyInt(),anyInt(),anyInt(),anyInt())).thenCallRealMethod();
            theme.when(()->VillageUiTheme.ellipsize(any(),anyString(),anyInt())).thenAnswer(a->a.getArgument(1));
            theme.when(()->VillageUiTheme.wrapScaledLines(any(),anyString(),anyInt(),anyFloat())).thenAnswer(a->List.of((String)a.getArgument(1)));
            var font=mock(Font.class);when(font.width(anyString())).thenAnswer(a->((String)a.getArgument(0)).length()*6);
            var g=mock(GuiGraphics.class);when(g.pose()).thenReturn(new Matrix3x2fStack(12));
            for(int index=0;index<3;index++) {
                var screen=(MarketScreen)UiTestTourScreens.create(UiTestTourSteps.ALL.stream().filter(step->step.family().equals("market")).toList().get(index));screen.width=800;screen.height=500;
                var f=Screen.class.getDeclaredField("font");f.setAccessible(true);f.set(screen,font);
                theme.clearInvocations();art.clearInvocations();screen.render(g,0,0,0);
                int left=(800-number(MarketScreen.class,"W"))/2,top=(500-number(MarketScreen.class,"H"))/2;
                var xs=org.mockito.ArgumentCaptor.forClass(Float.class);var ys=org.mockito.ArgumentCaptor.forClass(Float.class);
                theme.verify(()->VillageUiTheme.drawStringScaled(eq(g),eq(font),anyString(),xs.capture(),ys.capture(),anyInt(),anyFloat()),atLeastOnce());
                for(int i=0;i<xs.getAllValues().size();i++)assertTrue(xs.getAllValues().get(i)>=left+28 && ys.getAllValues().get(i)>=top+34 && ys.getAllValues().get(i)+8<=top+number(MarketScreen.class,"H")-18,"market text intersects perimeter");
                var moneyX=org.mockito.ArgumentCaptor.forClass(Integer.class);var moneyW=org.mockito.ArgumentCaptor.forClass(Integer.class);
                art.verify(()->KingdomUiAssets.moneyCentered(eq(g),eq(font),anyLong(),moneyX.capture(),anyInt(),moneyW.capture(),anyBoolean()),atLeastOnce());
                for(int i=0;i<moneyX.getAllValues().size();i++)assertTrue(moneyX.getAllValues().get(i)+moneyW.getAllValues().get(i)<=left+number(MarketScreen.class,"W")-28,"wallet crosses right rail");
            }
        }
    }
    @Test void questCategoriesAndDetailArtHaveSpaceForReferenceLabelsAndIllustrations()throws Exception {
        assertTrue(number(QuestMasterScreen.class,"CATEGORY_SLOT_WIDTH")>=76,"icon-only category rail cannot match V3");
        assertTrue(number(QuestMasterScreen.class,"DETAIL_BODY_WIDTH")>=230);
        assertTrue(number(QuestMasterScreen.class,"DETAIL_BODY_HEIGHT")>=155);
        assertTrue(number(QuestMasterScreen.class,"ENTRY_WIDTH")>=170);
    }
    @Test void guildMapUsesTheActualPackagedImageDimensions()throws Exception {
        try(var input=getClass().getClassLoader().getResourceAsStream("assets/village-quest/textures/gui/guild_path_map.png")) {
            assertNotNull(input);
            var image=javax.imageio.ImageIO.read(input);
            assertEquals(image.getWidth(),number(GuildPathScreen.class,"MAP_TEXTURE_WIDTH"));
            assertEquals(image.getHeight(),number(GuildPathScreen.class,"MAP_TEXTURE_HEIGHT"));
        }
    }
}
