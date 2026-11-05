package de.quest.data;

import de.quest.config.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.data.QuestState;
import de.quest.network.QuestSetupPayloads;
import de.quest.quest.QuestBookHelper;
import de.quest.quest.QuestTrackerService;
import de.quest.caravan.TradeRouteService;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

final class QuestSetupServiceTest {
    @org.junit.jupiter.api.BeforeAll static void registries() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void manualSettingsUseEditModeEvenWithoutLegacySetupAndMembersCannotChangeWorldOptions() {
        try (var f = new Fixture()) {
            QuestSetupService.open(f.player);
            var view = f.last.get(); assertFalse(view.firstRun()); assertFalse(view.editable());
            // A forged edit is rejected on the server, even with a valid personal setup token.
            QuestSetupService.save(f.player, new QuestSetupPayloads.Save(view.session(), true, true, f.custom(), false, false));
            assertEquals(1, f.last.get().status()); assertNull(f.state.worldQuestSettings());
            assertFalse(f.state.getPlayerData(f.id).hasMilestoneFlag(QuestSetupService.COMPLETED));
            view = f.last.get();
            QuestSetupService.save(f.player, new QuestSetupPayloads.Save(view.session(), true, false, f.custom(), false, false));
            assertTrue(f.state.getPlayerData(f.id).hasMilestoneFlag(QuestSetupService.COMPLETED));
            assertNull(f.state.worldQuestSettings());
            f.tracker.verify(() -> QuestTrackerService.setEnabled(f.world, f.player, false));
        }
    }
    @Test void closingOptionalSettingsReturnsToJournalWithoutChangingExistingPreferences() {
        try (var f = new Fixture()) {
            VillageQuestServerConfig.bindWorld(f.custom());
            QuestSetupService.open(f.player);
            var view=f.last.get(); assertFalse(view.firstRun()); assertEquals(f.custom(),view.settings());
            QuestSetupService.save(f.player,new QuestSetupPayloads.Save(view.session(),false,false,
                    WorldQuestSettings.recommended(),false,false));
            f.books.verify(() -> QuestBookHelper.openJournal(f.world,f.player));
            assertEquals(f.custom(),VillageQuestServerConfig.get().worldSettings());
            assertFalse(f.state.getPlayerData(f.id).hasMilestoneFlag(QuestSetupService.COMPLETED));
            f.tracker.verifyNoInteractions();
            f.maps.verify(() -> TradeRouteService.setMinimapEnabled(any(ServerLevel.class),any(ServerPlayer.class),anyBoolean()),never());
        }
    }
    @Test void revokingPermissionAndConcurrentAdminChangesBothRejectWorldWrites() {
        try (var f = new Fixture()) {
            f.op.set(true); QuestSetupService.open(f.player); var view = f.last.get(); assertTrue(view.editable());
            f.op.set(false);
            QuestSetupService.save(f.player, new QuestSetupPayloads.Save(view.session(), true, true, f.custom(), true, false));
            assertEquals(1, f.last.get().status()); assertNull(f.state.worldQuestSettings());
            f.op.set(true); QuestSetupService.open(f.player); view = f.last.get();
            VillageQuestServerConfig.bindWorld(f.custom());
            QuestSetupService.save(f.player, new QuestSetupPayloads.Save(view.session(), true, true, WorldQuestSettings.recommended(), true, false));
            assertEquals(2, f.last.get().status()); assertNull(f.state.worldQuestSettings());
        }
    }
    @Test void worldOwnerCanPersistOptionsWithoutOpAndClosingFirstRunDoesNotCompleteIt() {
        try (var f = new Fixture()) {
            when(f.server.isSingleplayerOwner(f.player.nameAndId())).thenReturn(true);
            QuestSetupService.open(f.player); var first = f.last.get(); assertTrue(first.editable());
            QuestSetupService.save(f.player, new QuestSetupPayloads.Save(first.session(), false, false, first.settings(), true, false));
            assertFalse(f.state.getPlayerData(f.id).hasMilestoneFlag(QuestSetupService.COMPLETED));
            QuestSetupService.open(f.player); var view = f.last.get();
            QuestSetupService.save(f.player, new QuestSetupPayloads.Save(view.session(), true, true, f.custom(), false, true));
            assertEquals(f.custom(), f.state.worldQuestSettings());
            assertEquals(f.custom(), VillageQuestServerConfig.get().worldSettings());
            f.maps.verify(() -> TradeRouteService.setMinimapEnabled(f.world, f.player, true));
            assertTrue(f.state.getPlayerData(f.id).hasMilestoneFlag(QuestSetupService.COMPLETED));
        }
    }
    private static final class Fixture implements AutoCloseable {
        final UUID id = UUID.randomUUID();
        final MinecraftServer server = mock(MinecraftServer.class, RETURNS_DEEP_STUBS);
        final ServerLevel world = mock(ServerLevel.class);
        final ServerPlayer player = mock(ServerPlayer.class);
        final QuestState state = QuestState.fromNbt(new CompoundTag());
        final AtomicBoolean op = new AtomicBoolean();
        final AtomicReference<QuestSetupPayloads.View> last = new AtomicReference<>();
        final org.mockito.MockedStatic<QuestState> states = mockStatic(QuestState.class);
        final org.mockito.MockedStatic<QuestBookHelper> books = mockStatic(QuestBookHelper.class);
        final org.mockito.MockedStatic<QuestTrackerService> tracker = mockStatic(QuestTrackerService.class);
        final org.mockito.MockedStatic<TradeRouteService> maps = mockStatic(TradeRouteService.class);
        final org.mockito.MockedStatic<ServerPlayNetworking> network = mockStatic(ServerPlayNetworking.class);
        Fixture() {
            QuestSetupService.stop();
            when(server.isSameThread()).thenReturn(true); when(server.overworld()).thenReturn(world);
            when(world.getServer()).thenReturn(server); when(player.level()).thenReturn(world); when(player.getUUID()).thenReturn(id);
            when(player.nameAndId()).thenReturn(new net.minecraft.server.players.NameAndId(id, "SetupTest"));
            when(server.getPlayerList().isOp(player.nameAndId())).thenAnswer(call -> op.get());
            states.when(() -> QuestState.get(server)).thenReturn(state);
            network.when(() -> ServerPlayNetworking.send(eq(player), any(CustomPacketPayload.class))).thenAnswer(call -> {
                if (call.getArgument(1) instanceof QuestSetupPayloads.View view) last.set(view); return null;
            });
        }
        WorldQuestSettings custom() {
            return new WorldQuestSettings(true, VillageQuestServerConfig.AdventureProfile.HARDENED, false, VillageQuestServerConfig.CaravanVisualMode.REDUCED);
        }
        public void close() {
            QuestSetupService.stop(); network.close(); maps.close(); tracker.close(); books.close(); states.close();
        }
    }
}
