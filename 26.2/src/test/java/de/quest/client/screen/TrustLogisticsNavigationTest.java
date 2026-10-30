package de.quest.client.screen;

import de.quest.network.ReputationPayloads.*;
import de.quest.reputation.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Screen navigation and outgoing requests only, no rendered game or server. */
final class TrustLogisticsNavigationTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    private ReparationScreen screen(ServiceView dispatch,ServiceView convoy) {
        var data=new SocialReputationData(); data.setGuildTrust(8);
        return new ReparationScreen(new InteractionPayload(UUID.randomUUID(),false,false,
                ReputationViewService.build(data,List.of(),0,true),List.of(),"",dispatch,convoy),null);
    }
    private int tab(ReparationScreen screen) throws Exception {
        var field=ReparationScreen.class.getDeclaredField("tab"); field.setAccessible(true); return field.getInt(screen);
    }
    @Test void lockedCardsOnlyOpenTheirInstructionsAndConvoyOpeningNeverSendsAStart() throws Exception {
        try(var client=mockStatic(Minecraft.class); var transport=mockStatic(UiTestTour.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var locked=new ServiceView(0,"screen.village-quest.reputation.desk.logistics.routes",List.of(Component.literal("Build your network")));
            var screen=screen(locked,locked); screen.openLogistics(false); assertEquals(4,tab(screen));
            screen.openLogistics(true); assertEquals(3,tab(screen)); transport.verifyNoInteractions();
            var ready=screen(locked,new ServiceView(2,"screen.village-quest.reputation.desk.logistics.ready",List.of()));
            ready.openLogistics(true); assertEquals(3,tab(ready)); transport.verifyNoInteractions();
        }
    }
    @Test void unlockedDispatchRequestsTheExistingBoardWithoutAcceptingCargo() {
        try(var client=mockStatic(Minecraft.class); var transport=mockStatic(UiTestTour.class)) {
            client.when(Minecraft::getInstance).thenReturn(mock(Minecraft.class));
            var screen=screen(new ServiceView(1,"screen.village-quest.reputation.desk.logistics.ready",List.of()),ServiceView.unknown());
            screen.openLogistics(false);
            transport.verify(()->UiTestTour.send(argThat(p->p instanceof ActionPayload a && a.action()==ActionPayload.OPEN_DISPATCH)));
            transport.verifyNoMoreInteractions();
        }
    }
}
