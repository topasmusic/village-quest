package de.quest.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.network.ReputationPayloads;
import de.quest.network.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;
import org.junit.jupiter.api.Test;

final class UiReview37Test {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
    }
    private static int constant(Class<?> c,String name)throws Exception {
        var f=c.getDeclaredField(name);f.setAccessible(true);return f.getInt(null);
    }
    @Test void bothPathPreviewsHaveEveryRealUnlockAndMixedProgress()throws Exception {
        var nodes=UiTestTourScreens.nodes();
        assertEquals(List.of("ledger","surveyor_compass","starreach_ring","merchant_seal","shepherd_flute",
                "apiarist_smoker","lens","sigil","wayshrine","notice_board","courier_satchel"),nodes.stream().map(Payloads.GuildPathNodeData::nodeId).toList());
        assertEquals(java.util.Set.of(0,1,2),nodes.stream().map(Payloads.GuildPathNodeData::status).collect(java.util.stream.Collectors.toSet()));
        for(var n:nodes) {
            assertEquals("screen.village-quest.guild_path.node."+n.nodeId()+".ability",
                    ((net.minecraft.network.chat.contents.TranslatableContents)n.ability().getContents()).getKey());
            assertEquals("screen.village-quest.guild_path.node."+n.nodeId()+".requirement",
                    ((net.minecraft.network.chat.contents.TranslatableContents)n.requirement().getContents()).getKey());
            assertFalse(n.title().getString().contains("ui_tour.sample"));
        }
        try(var client=mockStatic(Minecraft.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var preview=(GuildPathScreen)UiTestTourScreens.create(new de.quest.commands.UiTestTourSteps.Step("guildpath","overview"));
            var data=GuildPathScreen.class.getDeclaredField("data");data.setAccessible(true);
            assertEquals(nodes,((Payloads.GuildPathPayload)data.get(preview)).nodes());
        }
    }
    @Test void trustPreviewReturnsToExactParentAndNeverSendsGameplayPackets()throws Exception {
        try(var client=mockStatic(Minecraft.class);var packets=mockStatic(ClientPlayNetworking.class);
            var map=mockStatic(de.quest.client.ui.SurfaceMapRenderer.class)) {
            var mc=mock(Minecraft.class);var gui=mock(Gui.class);
            var gf=Minecraft.class.getDeclaredField("gui");gf.setAccessible(true);gf.set(mc,gui);
            client.when(Minecraft::getInstance).thenReturn(mc);
            var parent=UiTestTourScreens.create(new de.quest.commands.UiTestTourSteps.Step("questmaster","available"));
            parent.width=700;parent.height=440;when(gui.screen()).thenReturn(parent);
            var active=UiTestTour.class.getDeclaredField("active");active.setAccessible(true);active.setBoolean(null,true);
            try {
                var capture=org.mockito.ArgumentCaptor.forClass(Screen.class);
                double scale=1/((CompatScreen)parent).responsiveDrag(1,560,315);
                assertTrue(parent.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(350+(520-350)*scale,220+(76-220)*scale,
                        new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false));
                verify(gui).setScreen(capture.capture());
                var trust=assertInstanceOf(ReparationScreen.class,capture.getValue());
                var sf=Screen.class.getDeclaredField("minecraft");sf.setAccessible(true);sf.set(trust,mc);
                UiTestTour.send(new Payloads.JournalActionPayload(0));
                trust.onClose();
                verify(gui).setScreen(parent);
                assertTrue(UiTestTour.active());packets.verifyNoInteractions();
                UiTestTour.send(new ReputationPayloads.OpenPayload(1,net.minecraft.core.BlockPos.ZERO,-1));
                UiTestTour.end();assertFalse(UiTestTour.active());
                packets.verifyNoInteractions();
                var live=new ReputationPayloads.OpenPayload(1,net.minecraft.core.BlockPos.ZERO,21);
                UiTestTour.send(live);packets.verify(()->ClientPlayNetworking.send(live));
            } finally { UiTestTour.disconnected(); }
        }
    }
    @Test void expertiseAndMapAndFootersFitTheSafeInterior()throws Exception {
        assertTrue(constant(JournalScreen.class,"TRUST_LIST_Y")+4*constant(JournalScreen.class,"TRUST_ROW_STEP")+
                constant(JournalScreen.class,"TRUST_ROW_HEIGHT")<=constant(JournalScreen.class,"ATLAS_MAP_Y")+constant(JournalScreen.class,"ATLAS_MAP_HEIGHT"));
        assertTrue(constant(JournalScreen.class,"BUTTON_Y")+constant(JournalScreen.class,"BUTTON_HEIGHT")<=constant(JournalScreen.class,"WINDOW_HEIGHT")-32);
        assertTrue(constant(QuestMasterScreen.class,"DETAIL_BODY_Y")+constant(QuestMasterScreen.class,"DETAIL_BODY_HEIGHT")<constant(QuestMasterScreen.class,"BUTTON_Y"));
        assertTrue(constant(QuestMasterScreen.class,"BUTTON_Y")+constant(QuestMasterScreen.class,"BUTTON_HEIGHT")<=constant(QuestMasterScreen.class,"WINDOW_HEIGHT")-32);
        assertTrue(constant(GuildPathScreen.class,"MAP_X")>=32);
        assertTrue(constant(GuildPathScreen.class,"MAP_X")+constant(GuildPathScreen.class,"MAP_WIDTH")+10+constant(GuildPathScreen.class,"DETAIL_WIDTH")<=560-32);
    }
}
