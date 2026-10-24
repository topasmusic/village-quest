package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import de.quest.village.VillageLifeState.VillageKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.junit.jupiter.api.Test;

/** Mocked level API only: no server, client, world or simulation is started. */
final class VillageCheckNativeTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void unloadedAnchorOrScanNeighbourCannotTriggerBlockReadsOrChunkLoads() {
        var world=mock(ServerLevel.class); var server=mock(MinecraftServer.class); var player=mock(ServerPlayer.class);
        var storage=mock(SavedDataStorage.class); var state=new LivingKingdomsState();
        var owner=UUID.randomUUID(); var town=UUID.randomUUID(); var pos=new BlockPos(15,64,15);
        state.registerCustom(town,owner,VillageKey.overworld(15,15),pos,"Home").orElseThrow();
        state.anchors().bind(owner,town,0,UUID.randomUUID(),-1,pos,Direction.NORTH,BuildingScanner.Range.BASIC,
                new BuildingScanner.Report(BuildingScanner.Role.COUNCIL,BuildingScanner.Status.VALID,Map.of(),Map.of(),Set.of()),0,0);
        when(world.getServer()).thenReturn(server); when(server.isSameThread()).thenReturn(true);
        when(server.overworld()).thenReturn(world); when(world.getDataStorage()).thenReturn(storage);
        when(storage.computeIfAbsent(LivingKingdomsState.TYPE)).thenReturn(state);
        when(player.level()).thenReturn(world);
        when(storage.computeIfAbsent(de.quest.data.QuestState.TYPE)).thenReturn(de.quest.data.QuestState.TYPE.codec()
                .parse(net.minecraft.nbt.NbtOps.INSTANCE,new net.minecraft.nbt.CompoundTag()).getOrThrow());
        when(world.dimension()).thenReturn(Level.OVERWORLD); when(player.getUUID()).thenReturn(owner); when(player.blockPosition()).thenReturn(pos);
        assertFalse(KingdomBuildingService.useVillageCheck(world,player));
        when(world.getGameTime()).thenReturn(100L); when(world.hasChunkAt(pos)).thenReturn(true);
        // The anchor chunk is present, but its bounded scan crosses an absent neighbour.
        assertFalse(KingdomBuildingService.useVillageCheck(world,player));
        verify(world,never()).getBlockState(any()); verify(world,never()).getChunk(anyInt(),anyInt());
    }
    @Test void offThreadCheckCannotReadSavedWorldState() {
        var world=mock(ServerLevel.class); var server=mock(MinecraftServer.class);
        when(world.getServer()).thenReturn(server); when(server.isSameThread()).thenReturn(false);
        assertFalse(KingdomBuildingService.useVillageCheck(world,mock(ServerPlayer.class)));
        verify(world,never()).getDataStorage(); verify(server,never()).overworld();
    }
}
