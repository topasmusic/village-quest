package de.quest.commands;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mojang.brigadier.CommandDispatcher;
import de.quest.data.PersonalResetService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

/** Registered two-step dispatch only. Real reset execution is replaced by a test double. */
final class PersonalResetCommandTest {
    @BeforeAll static void bootstrap(){QuestCommandsUiTestTreeTest.bootstrapMinecraft();}
    private CommandDispatcher<CommandSourceStack> dispatcher(){
        QuestCommands.register();var dispatcher=new CommandDispatcher<CommandSourceStack>();
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.invoker().register(dispatcher,
                mock(net.minecraft.commands.CommandBuildContext.class),net.minecraft.commands.Commands.CommandSelection.DEDICATED);return dispatcher;
    }
    @Test void registeredSelfOnlyCommandRequiresTwoStepsAndDispatchesTheNativeServiceOnlyOnce() throws Exception {
        var dispatcher=dispatcher();var root=dispatcher.getRoot().getChild("villagequest");var reset=root.getChild("reset");
        assertNotNull(reset);assertEquals(java.util.Set.of("confirm"),reset.getChildren().stream().map(c->c.getName()).collect(java.util.stream.Collectors.toSet()));
        assertSame(root,dispatcher.getRoot().getChild("vq").getRedirect());
        var source=mock(CommandSourceStack.class);var player=mock(ServerPlayer.class);var world=mock(ServerLevel.class);var server=mock(MinecraftServer.class);
        when(source.getPlayer()).thenReturn(player);when(player.level()).thenReturn(world);when(world.getServer()).thenReturn(server);
        when(player.getUUID()).thenReturn(java.util.UUID.randomUUID());when(player.isAlive()).thenReturn(true);
        var mode=mock(ServerPlayerGameMode.class);var field=ServerPlayer.class.getField("gameMode");field.setAccessible(true);field.set(player,mode);
        when(mode.getGameModeForPlayer()).thenReturn(net.minecraft.world.level.GameType.SURVIVAL);
        try(var service=mockStatic(PersonalResetService.class)) {
            service.when(()->PersonalResetService.restart(player)).thenReturn(true);
            assertTrue(reset.getRequirement().test(source));assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,()->dispatcher.execute("vq reset confirm guessed",source));service.verifyNoInteractions();
            assertEquals(1,dispatcher.execute("vq reset",source));
            var messages=ArgumentCaptor.forClass(Component.class);verify(player,times(2)).sendSystemMessage(messages.capture(),eq(false));
            var command=((TranslatableContents)messages.getAllValues().getLast().getContents()).getArgs()[0].toString();
            assertEquals("/vq reset confirm",command);assertEquals(1,dispatcher.execute(command.substring(1),source));
            assertEquals(1,dispatcher.execute(command.substring(1),source));service.verify(()->PersonalResetService.restart(player),times(1));
            assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,()->dispatcher.execute("vq reset someone_else",source));
        }
    }
    @Test void consoleAndNonSurvivalCannotRequestOrConfirmAReset() throws Exception {
        var dispatcher=dispatcher();var source=mock(CommandSourceStack.class);
        try(var service=mockStatic(PersonalResetService.class)) {
            assertEquals(0,dispatcher.execute("vq reset",source));assertEquals(0,dispatcher.execute("vq reset confirm",source));
            var player=mock(ServerPlayer.class);when(source.getPlayer()).thenReturn(player);when(player.isAlive()).thenReturn(true);
            var mode=mock(ServerPlayerGameMode.class);var field=ServerPlayer.class.getField("gameMode");field.setAccessible(true);field.set(player,mode);
            when(mode.getGameModeForPlayer()).thenReturn(net.minecraft.world.level.GameType.CREATIVE);
            assertEquals(0,dispatcher.execute("vq reset",source));assertEquals(0,dispatcher.execute("vq reset confirm",source));service.verifyNoInteractions();
        }
    }
    @Test void directConfirmFirstShowsWarningAndOnlyItsSecondInvocationRestarts() throws Exception {
        var dispatcher=dispatcher();var source=mock(CommandSourceStack.class);var player=mock(ServerPlayer.class);
        var world=mock(ServerLevel.class);var server=mock(MinecraftServer.class);
        when(source.getPlayer()).thenReturn(player);when(player.level()).thenReturn(world);when(world.getServer()).thenReturn(server);
        when(player.getUUID()).thenReturn(java.util.UUID.randomUUID());when(player.isAlive()).thenReturn(true);
        var mode=mock(ServerPlayerGameMode.class);var gameMode=ServerPlayer.class.getField("gameMode");gameMode.setAccessible(true);gameMode.set(player,mode);
        when(mode.getGameModeForPlayer()).thenReturn(net.minecraft.world.level.GameType.SURVIVAL);
        try(var service=mockStatic(PersonalResetService.class)) {
            service.when(()->PersonalResetService.restart(player)).thenReturn(true);
            assertEquals(1,dispatcher.execute("vq reset confirm",source));service.verifyNoInteractions();
            var messages=ArgumentCaptor.forClass(Component.class);verify(player,times(2)).sendSystemMessage(messages.capture(),eq(false));
            assertEquals("command.village-quest.personal_reset.warning",((TranslatableContents)messages.getAllValues().getFirst().getContents()).getKey());
            assertEquals(1,dispatcher.execute("vq reset confirm",source));service.verify(()->PersonalResetService.restart(player),times(1));
            assertEquals(1,dispatcher.execute("vq reset confirm",source));service.verify(()->PersonalResetService.restart(player),times(1));
        }
    }
}
