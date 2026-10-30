package de.quest.shrine;

import de.quest.data.QuestState;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

final class StoryLaneRecoveryGuardTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void unknownStoryFormatCannotBeUnblockedByAnEmptyIntroJournal() {
        var root=new CompoundTag(); var manager=new CompoundTag(); manager.putInt("storyLaneFormat",77);
        root.put("questManager",manager);
        var state=QuestState.TYPE.codec().parse(NbtOps.INSTANCE,root).getOrThrow();
        var player=mock(ServerPlayer.class); var world=mock(ServerLevel.class); var server=mock(MinecraftServer.class);
        when(player.level()).thenReturn(world); when(player.getUUID()).thenReturn(UUID.randomUUID());
        when(world.getServer()).thenReturn(server); when(server.isSameThread()).thenReturn(true);
        player.connection=mock(ServerGamePacketListenerImpl.class);
        try(var states=mockStatic(QuestState.class,CALLS_REAL_METHODS);
            var intro=mockStatic(VillageIntroRequestService.class, invocation ->
                    invocation.getMethod().getName().equals("recover")
                            ? VillageIntroDeliveryCoordinator.Result.CANCELLED : invocation.callRealMethod())) {
            states.when(()->QuestState.get(server)).thenReturn(state);
            assertTrue(VillageIntroRequestService.blocksActions(world,player.getUUID()));
            assertFalse(VillageIntroRequestService.recoverBeforeActions(player));
            intro.verify(()->VillageIntroRequestService.recover(player),never());
            verify(player.connection).disconnect(Component.translatable("message.village-quest.story.data_unsupported"));
        }
    }
}
