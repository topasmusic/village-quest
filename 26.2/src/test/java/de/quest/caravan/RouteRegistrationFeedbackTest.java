package de.quest.caravan;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.network.chat.contents.TranslatableContents;
import java.util.UUID;
final class RouteRegistrationFeedbackTest {
 @Test void lockedRegistrationExplainsMissingAccessBeforeAnyVillageLookup() {
  var server=mock(MinecraftServer.class);var world=mock(ServerLevel.class);var player=mock(ServerPlayer.class);var id=UUID.randomUUID();
  when(world.getServer()).thenReturn(server);when(server.overworld()).thenReturn(world);when(player.level()).thenReturn(world);when(player.getUUID()).thenReturn(id);
  try(var routes=mockStatic(TradeRouteService.class,CALLS_REAL_METHODS)) {
   routes.when(()->TradeRouteService.hasRouteAccess(world,id)).thenReturn(false);
   assertFalse(TradeRouteService.registerCurrentVillage(world,player));
   verify(player).sendSystemMessage(argThat(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals("message.village-quest.trade_route.locked")),eq(false));
  }
 }
}
