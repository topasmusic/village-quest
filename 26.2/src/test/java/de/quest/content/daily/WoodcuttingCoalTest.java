package de.quest.content.daily;
import de.quest.quest.daily.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import java.util.UUID;
import org.junit.jupiter.api.*;
import static org.mockito.Mockito.*;
class WoodcuttingCoalTest {
 @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
 @Test void coalPickupCountsItsAmountInsteadOfMinedOreBlocks(){
  var world=mock(ServerLevel.class); var player=mock(ServerPlayer.class); var id=UUID.randomUUID();when(player.getUUID()).thenReturn(id);
  try(var daily=mockStatic(DailyQuestService.class)){
   daily.when(()->DailyQuestService.isTrackingQuest(world,id,DailyQuestService.DailyQuestType.WOODCUTTING)).thenReturn(true);
   daily.when(DailyQuestService::coalTarget).thenReturn(28);
   var quest=new WoodcuttingDailyQuest();
   quest.onBlockBreak(world,player,BlockPos.ZERO,Blocks.COAL_ORE.defaultBlockState());
   daily.verify(()->DailyQuestService.setQuestInt(world,id,DailyQuestKeys.COAL_PROGRESS,1),never());
   var coal=mock(ItemStack.class);when(coal.is(Items.COAL)).thenReturn(true);
   quest.onTrackedItemPickup(world,player,coal,5);
   daily.verify(()->DailyQuestService.setQuestInt(world,id,DailyQuestKeys.COAL_PROGRESS,5));
  }
 }
}
