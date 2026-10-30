package de.quest.commands;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

final class PersonalResetEligibilityTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void onlyLivingSurvivalPlayerCanRequestOrConfirmReset() throws Exception {
        var eligible = PersonalResetCommand.class.getDeclaredMethod("eligible", ServerPlayer.class);
        eligible.setAccessible(true);
        var player = mock(ServerPlayer.class);
        var mode = mock(net.minecraft.server.level.ServerPlayerGameMode.class);
        var field=ServerPlayer.class.getField("gameMode");field.setAccessible(true);field.set(player,mode);
        when(mode.getGameModeForPlayer()).thenReturn(net.minecraft.world.level.GameType.SURVIVAL);
        when(player.isAlive()).thenReturn(true);
        assertEquals(true, eligible.invoke(null, player));
        when(mode.getGameModeForPlayer()).thenReturn(net.minecraft.world.level.GameType.ADVENTURE);
        assertEquals(false, eligible.invoke(null, player));
        when(mode.getGameModeForPlayer()).thenReturn(net.minecraft.world.level.GameType.SURVIVAL);
        when(player.isCreative()).thenReturn(true);
        assertEquals(false, eligible.invoke(null, player));
        when(player.isCreative()).thenReturn(false); when(player.isSpectator()).thenReturn(true);
        assertEquals(false, eligible.invoke(null, player));
        when(player.isSpectator()).thenReturn(false); when(player.isAlive()).thenReturn(false);
        assertEquals(false, eligible.invoke(null, player));
        assertEquals(false, eligible.invoke(null, new Object[]{null}));
    }
}
