package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Exercises only dispatch against mocked query edges; no running server, world or gameplay. */
final class KingdomNpcControllerSchedulingTest {
    @BeforeAll static void registries() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    private static final class Fixture {
        final MinecraftServer server=mock(MinecraftServer.class);
        final ServerLevel world=mock(ServerLevel.class);
        final AtomicLong clock=new AtomicLong();
        final List<ServerPlayer> players=new ArrayList<>();
        final LivingKingdomsState state=new LivingKingdomsState();
        final KingdomNpcController controller;
        final Method tick;
        Fixture(int count) throws ReflectiveOperationException {
            var list=mock(PlayerList.class); when(server.getPlayerList()).thenReturn(list);
            when(list.getPlayers()).thenReturn(players); when(server.overworld()).thenReturn(world);
            when(world.dimension()).thenReturn(Level.OVERWORLD); when(world.getGameTime()).thenAnswer(i -> clock.get());
            for(int i=0;i<count;i++) {
                var player=mock(ServerPlayer.class); when(player.level()).thenReturn(world);
                when(player.blockPosition()).thenReturn(BlockPos.ZERO); players.add(player);
            }
            var constructor=KingdomNpcController.class.getDeclaredConstructor(MinecraftServer.class);
            constructor.setAccessible(true); controller=constructor.newInstance(server);
            tick=KingdomNpcController.class.getDeclaredMethod("tick"); tick.setAccessible(true);
        }
        void at(long now) throws ReflectiveOperationException { clock.set(now); tick.invoke(controller); }
    }
    @ParameterizedTest @ValueSource(ints={1,8,9,16})
    void boundedRotationHasTheDocumentedObservationRateForEveryOnlinePlayer(int count) throws Exception {
        var f=new Fixture(count); var dispatched=new ArrayList<ServerPlayer>();
        try(var states=mockStatic(LivingKingdomsState.class); var crises=mockStatic(KingdomCrisisService.class);
            var attacks=mockStatic(KingdomAttackService.class)) {
            states.when(() -> LivingKingdomsState.get(f.server)).thenReturn(f.state);
            crises.when(() -> KingdomCrisisService.observe(any(ServerPlayer.class))).thenAnswer(i -> { dispatched.add(i.getArgument(0)); return null; });
            var totals=new HashMap<ServerPlayer,Integer>();
            for(int round=1;round<=18;round++) {
                dispatched.clear(); f.at(round*20L);
                assertEquals(Math.min(8,count),dispatched.size());
                assertEquals(dispatched.size(),new java.util.HashSet<>(dispatched).size(),"No duplicate observer within a sampling round");
                dispatched.forEach(player -> totals.merge(player,1,Integer::sum));
            }
            int expected=18*Math.min(8,count)/count;
            for(var player:f.players) {
                assertEquals(expected,totals.get(player));
                attacks.verify(() -> KingdomAttackService.observe(player),times(expected));
            }
            assertEquals(count<=8?360:count==9?320:180,expected*20,
                    "Crisis time credits observed seconds; it does not extrapolate unsampled presence");
        }
    }
    @Test void emptyRosterAndLargeClockGapNeverAccumulateCatchUpWork() throws Exception {
        var f=new Fixture(16); var roster=List.copyOf(f.players);
        try(var states=mockStatic(LivingKingdomsState.class); var crises=mockStatic(KingdomCrisisService.class);
            var attacks=mockStatic(KingdomAttackService.class)) {
            states.when(() -> LivingKingdomsState.get(f.server)).thenReturn(f.state);
            f.at(19); crises.verifyNoInteractions(); attacks.verifyNoInteractions();
            f.at(20);
            for(int i=0;i<16;i++) {
                final var player=roster.get(i);
                crises.verify(() -> KingdomCrisisService.observe(player),times(i<8?1:0));
            }
            crises.clearInvocations(); attacks.clearInvocations();
            f.players.clear(); f.at(40); f.at(100_000); crises.verifyNoInteractions(); attacks.verifyNoInteractions();
            f.players.addAll(roster); f.at(100_020);
            for(int i=0;i<16;i++) {
                final var player=roster.get(i);
                crises.verify(() -> KingdomCrisisService.observe(player),times(i>=8?1:0));
                attacks.verify(() -> KingdomAttackService.observe(player),times(i>=8?1:0));
            }
        }
    }
}
