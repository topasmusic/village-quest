package de.quest.entity;

import de.quest.ambient.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Direct interaction boundary with mocks; no entity/world ticks or Ingame simulation. */
final class AmbientAdventurerInteractionTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    @Test void twoPlayersCanSpeakAndRejectedSpeechDoesNotStopTravel() throws Exception {
        var npc=mock(AmbientAdventurerEntity.class,CALLS_REAL_METHODS); var world=mock(ServerLevel.class);
        var nav=mock(AmbientAdventurerNavigation.class); var policy=new AmbientAdventurerPolicy(false,9600);
        var field=AmbientAdventurerEntity.class.getDeclaredField("policy"); field.setAccessible(true); field.set(npc,policy);
        Class<?> owner=AmbientAdventurerEntity.class;
        while(java.util.Arrays.stream(owner.getDeclaredFields()).noneMatch(f->f.getName().equals("random"))) owner=owner.getSuperclass();
        var random=owner.getDeclaredField("random"); random.setAccessible(true); random.set(npc,net.minecraft.util.RandomSource.create(1));
        // This fixture tests ordinary speech only; quest offers are a separate boundary.
        doReturn(false).when(npc).encounterCalm(any());
        doReturn(world).when(npc).level(); doReturn(true).when(npc).hasLineOfSight(any()); doReturn(nav).when(npc).travel();
        doReturn(1D).when(npc).distanceToSqr(any(net.minecraft.world.entity.Entity.class));
        doReturn(mock(net.minecraft.world.entity.ai.control.LookControl.class)).when(npc).getLookControl();
        when(world.getGameTime()).thenReturn(100L);
        var a=mock(ServerPlayer.class); var b=mock(ServerPlayer.class);
        when(a.getUUID()).thenReturn(UUID.randomUUID()); when(b.getUUID()).thenReturn(UUID.randomUUID());
        try(var dialogue=mockStatic(AmbientAdventurerDialogue.class)) {
            dialogue.when(() -> AmbientAdventurerDialogue.greet(a,npc,false,false)).thenReturn(true);
            dialogue.when(() -> AmbientAdventurerDialogue.greet(b,npc,false,false)).thenReturn(true);
            npc.mobInteract(a,InteractionHand.MAIN_HAND); npc.mobInteract(b,InteractionHand.MAIN_HAND);
            dialogue.verify(() -> AmbientAdventurerDialogue.greet(a,npc,false,false));
            dialogue.verify(() -> AmbientAdventurerDialogue.greet(b,npc,false,false)); verify(nav,times(2)).cancel();
            clearInvocations(nav);
            dialogue.when(() -> AmbientAdventurerDialogue.greet(a,npc,false,false)).thenReturn(false);
            npc.mobInteract(a,InteractionHand.MAIN_HAND); verifyNoInteractions(nav);
            policy.flee(0); // Retired combat budget alone is no evidence of a current nearby danger.
            npc.mobInteract(b,InteractionHand.MAIN_HAND);
            dialogue.verify(() -> AmbientAdventurerDialogue.greet(b,npc,false,false),times(2));
        }
    }
}
