package de.quest.shrine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.quest.content.story.VillagerDialogueService;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Direct service boundaries must reject babies before looking up or changing player state. */
final class BabyVillagerContactTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
    }

    @Test void babyCannotStartOrAdvanceWelcome() {
        var world = mock(ServerLevel.class);
        var player = mock(ServerPlayer.class);
        var baby = mock(Villager.class);
        when(baby.isBaby()).thenReturn(true);
        assertDoesNotThrow(() -> assertFalse(VillageWelcomeService.onVillagerContact(world, player, baby)));
        verifyNoInteractions(world, player);
    }

    @Test void babyCannotEstablishBondOrConsumeOnboarding() {
        var world = mock(ServerLevel.class);
        var player = mock(ServerPlayer.class);
        var baby = mock(Villager.class);
        when(baby.isBaby()).thenReturn(true);
        assertDoesNotThrow(() -> VillageBondService.onVillagerContact(world, player, baby));
        verifyNoInteractions(world, player);
    }

    @Test void babyCannotEmitDialogueEvenWhenCalledDirectly() {
        var player = mock(ServerPlayer.class);
        var baby = mock(Villager.class);
        when(baby.isBaby()).thenReturn(true);
        assertDoesNotThrow(() -> VillagerDialogueService.sendDialogue(player, baby, Component.literal("Hello")));
        verifyNoInteractions(player);
        verify(baby, never()).getDisplayName();
    }
}
