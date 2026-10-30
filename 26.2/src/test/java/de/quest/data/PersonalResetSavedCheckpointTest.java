package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.quest.guild.VillageGuildState;
import de.quest.guildtown.GuildTownSharedState;
import de.quest.kingdom.LivingKingdomsState;
import de.quest.mixin.MarketPlayerSaveInvoker;
import de.quest.mixin.MarketSavedDataFileInvoker;
import de.quest.village.LivingVillageNetworkState;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.saveddata.*;
import net.minecraft.world.level.storage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** Calls the actual barrier; native lifecycle accessors are test doubles backed by real temporary NBT files. */
final class PersonalResetSavedCheckpointTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap(){PersonalResetItemsTest.boot();}
    @SuppressWarnings("unchecked")
    private static CompoundTag encoded(SavedDataType<?> type,SavedData state) {
        return (CompoundTag)((SavedDataType<SavedData>)type).codec().encodeStart(NbtOps.INSTANCE,state).getOrThrow();
    }
    private static void write(Path path,CompoundTag tag) throws IOException {
        try(var out=new DataOutputStream(Files.newOutputStream(path))){NbtIo.write(tag,out);}
    }
    private static CompoundTag read(Path path) throws IOException {
        try(var in=new DataInputStream(Files.newInputStream(path))){return NbtIo.read(in,NbtAccounter.unlimitedHeap());}
    }
    @Test void nativeBarrierWritesAndReadsEveryActualDocumentAndRejectsEachStaleRead() throws Exception {
        for(int stale=-1;stale<8;stale++)runBarrier(stale);
    }
    private void runBarrier(int stale) throws Exception {
        var f=new PersonalResetIntegrationTest.Fixture();var tx=f.next();f.port(tx).prepare(tx.receipt());
        var server=mock(MinecraftServer.class);var world=mock(ServerLevel.class);
        when(f.storage.player.level()).thenReturn(world);when(world.getServer()).thenReturn(server);when(server.overworld()).thenReturn(world);
        when(server.isSameThread()).thenReturn(true);
        var storage=mock(SavedDataStorage.class,withSettings().extraInterfaces(MarketSavedDataFileInvoker.class));when(world.getDataStorage()).thenReturn(storage);
        var players=mock(PlayerList.class,withSettings().extraInterfaces(MarketPlayerSaveInvoker.class));when(server.getPlayerList()).thenReturn(players);
        var io=mock(PlayerDataStorage.class);when(((MarketPlayerSaveInvoker)players).villageQuest$marketPlayerStorage()).thenReturn(io);
        var guild=VillageGuildState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        var shared=GuildTownSharedState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        var network=LivingVillageNetworkState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        var kingdoms=LivingKingdomsState.TYPE.codec().parse(NbtOps.INSTANCE,new CompoundTag()).getOrThrow();
        var docs=new LinkedHashMap<SavedDataType<?>,SavedData>();docs.put(QuestState.TYPE,f.state);docs.put(VillageGuildState.TYPE,guild);
        docs.put(GuildTownSharedState.TYPE,shared);docs.put(LivingVillageNetworkState.TYPE,network);docs.put(LivingKingdomsState.TYPE,kingdoms);
        var playerPath=directory.resolve("player.dat");
        doAnswer(c->{
            var out=c.getArgument(0,ValueOutput.class);
            out.store("Inventory",CompoundTag.CODEC.listOf(),List.of(new CompoundTag()));
            out.store("EnderItems",CompoundTag.CODEC.listOf(),List.of(new CompoundTag()));
            out.child(AttachmentTarget.NBT_ATTACHMENT_KEY).store(PersonalResetSaveBarrier.PLAYER_PROOF,CompoundTag.CODEC,(CompoundTag)f.proof);
            return null;
        }).when(f.storage.player).saveWithoutId(any());
        doAnswer(c->{int index=0;for(var e:docs.entrySet()) {
            var data=encoded(e.getKey(),e.getValue());if(index++==stale)data.putInt("staleRead",1);
            var root=new CompoundTag();root.put("data",data);write(directory.resolve(e.getKey().id().getPath()+".dat"),root);
        }return null;}).when(storage).saveAndJoin();
        when(((MarketSavedDataFileInvoker)storage).villageQuest$marketDataFile(any())).thenAnswer(c->directory.resolve(c.getArgument(0,net.minecraft.resources.Identifier.class).getPath()+".dat"));
        when(storage.readTagFromDisk(any(),any(),anyInt())).thenAnswer(c->read(c.getArgument(0,Path.class)));
        doAnswer(c->{
            var out=TagValueOutput.createWithContext(new net.minecraft.util.ProblemReporter.Collector(),f.registries);
            f.storage.player.saveWithoutId(out);var data=out.buildResult();
            if(stale==5)data.getListOrEmpty("EnderItems").add(new CompoundTag());
            if(stale==6)data.getCompoundOrEmpty(AttachmentTarget.NBT_ATTACHMENT_KEY).remove(PersonalResetSaveBarrier.PLAYER_PROOF);
            if(stale==7)data.getListOrEmpty("Inventory").add(new CompoundTag());
            write(playerPath,data);return null;
        }).when((MarketPlayerSaveInvoker)players).villageQuest$saveMarketPlayer(f.storage.player);
        when(io.load(any())).thenAnswer(c->Optional.of(read(playerPath)));
        try(var q=mockStatic(QuestState.class,CALLS_REAL_METHODS);var g=mockStatic(VillageGuildState.class,CALLS_REAL_METHODS);
            var s=mockStatic(GuildTownSharedState.class,CALLS_REAL_METHODS);var n=mockStatic(LivingVillageNetworkState.class,CALLS_REAL_METHODS);
            var k=mockStatic(LivingKingdomsState.class,CALLS_REAL_METHODS)) {
            q.when(()->QuestState.get(server)).thenReturn(f.state);g.when(()->VillageGuildState.get(server)).thenReturn(guild);
            s.when(()->GuildTownSharedState.get(server)).thenReturn(shared);n.when(()->LivingVillageNetworkState.get(server)).thenReturn(network);
            k.when(()->LivingKingdomsState.get(server)).thenReturn(kingdoms);
            assertEquals(stale==-1,PersonalResetSaveBarrier.persist(f.storage.player,true),"stale participant="+stale);
            verify(storage).saveAndJoin();verify(storage,times(5)).readTagFromDisk(any(),any(),anyInt());verify(io).load(any());
            assertTrue(Files.isRegularFile(playerPath));
        }
    }
}
