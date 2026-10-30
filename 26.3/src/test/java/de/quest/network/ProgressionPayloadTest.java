package de.quest.network;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.quest.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.*;

final class ProgressionPayloadTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    private RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)); }
    private TrackerEntry entry(String id) { return new TrackerEntry(id,"story",Component.translatable("quest.title"),Component.translatable("progress",2,3)); }
    @Test void trackerCodecKeepsEveryMaterialAndItsCompletionAcrossTheWire() {
        var group=QuestShortObjective.current(List.of(Component.translatable("quest.village-quest.story.failing_harvest.chapter_1.progress",16,16,3,8)));
        var original=new Payloads.QuestTrackerPayload(true,List.of(new TrackerEntry("story:core:failing_harvest","story",Component.literal("Harvest"),group)));
        var buf=buffer();
        try {
            Payloads.QuestTrackerPayload.CODEC.encode(buf,original);buf.writeInt(772);
            var decoded=Payloads.QuestTrackerPayload.CODEC.decode(buf);assertEquals(original,decoded);
            var rows=QuestShortObjective.rows(decoded.entries().getFirst().shortObjective());assertEquals(2,rows.size());
            assertTrue(QuestObjectiveProgress.complete(rows.get(0)));assertFalse(QuestObjectiveProgress.complete(rows.get(1)));
            assertEquals(772,buf.readInt());assertEquals(0,buf.readableBytes());
        } finally {buf.release();}
    }
    @Test void trackerRoundTripKeepsIndependentIdsAndExactlyConsumesBoundedList() {
        var entries=new ArrayList<TrackerEntry>();
        for(int i=0;i<TrackerEntry.MAX_ENTRIES;i++) entries.add(entry("story:local:"+i+":test"));
        var payload=new Payloads.QuestTrackerPayload(true,entries); var buf=buffer();
        try {
            Payloads.QuestTrackerPayload.CODEC.encode(buf,payload); buf.writeInt(991);
            assertEquals(payload,Payloads.QuestTrackerPayload.CODEC.decode(buf));
            assertEquals(991,buf.readInt()); assertEquals(0,buf.readableBytes());
        } finally { buf.release(); }
        assertThrows(IllegalArgumentException.class,() -> new Payloads.QuestTrackerPayload(true,List.of(entry("story:core:a"),entry("story:core:a"))));
        assertThrows(IllegalArgumentException.class,() -> new Payloads.QuestTrackerPayload(true,Collections.nCopies(33,entry("story:core:a"))));
    }
    @Test void malformedCountsAreRejectedBeforeEntryReadsAndOldChannelsAreNotReused() {
        for(int count:new int[]{-1,33,Integer.MAX_VALUE}) {
            var buf=buffer();
            try { buf.writeBoolean(true); buf.writeVarInt(count);
                assertThrows(IllegalArgumentException.class,() -> Payloads.QuestTrackerPayload.CODEC.decode(buf));
            } finally {buf.release();}
        }
        assertNotEquals("quest_tracker",Payloads.QuestTrackerPayload.ID.id().getPath());
        assertNotEquals("journal",Payloads.JournalPayload.ID.id().getPath());
        assertNotEquals("journal_action",Payloads.JournalActionPayload.ID.id().getPath());
    }
    @Test void fullJournalRoundTripIncludesBothStoriesLocalAndCommissionBeforeSentinel() throws Exception {
        var quests=List.of("story:core:failing_harvest","story:kingdom:living_kingdoms","story:local:1:shared_table","story:commission:legacy:winter_feed")
                .stream().map(id -> new JournalActiveQuest(entry(id),List.of(Component.literal("Complete details")),-1)).toList();
        var fields=Payloads.JournalPayload.class.getRecordComponents(); var values=new Object[fields.length];
        for(int i=0;i<fields.length;i++) {
            var type=fields[i].getType();
            values[i]=type==int.class?Integer.valueOf(0):type==long.class?Long.valueOf(0):type==boolean.class?Boolean.FALSE
                    :type==Component.class?Component.empty():type==List.class?List.of():null;
            if(fields[i].getName().equals("activeQuests")) values[i]=quests;
        }
        var constructor=Payloads.JournalPayload.class.getDeclaredConstructor(Arrays.stream(fields).map(java.lang.reflect.RecordComponent::getType).toArray(Class<?>[]::new));
        var payload=constructor.newInstance(values); var buf=buffer();
        try {
            Payloads.JournalPayload.CODEC.encode(buf,payload); buf.writeInt(881);
            assertEquals(quests,Payloads.JournalPayload.CODEC.decode(buf).activeQuests());
            assertEquals(881,buf.readInt()); assertEquals(0,buf.readableBytes());
        } finally {buf.release();}
    }
    @Test void everyRequiredCapabilityIsChecked() {
        assertTrue(ProgressionProtocol.compatible(true,true,true,true));
        for(int missing=0;missing<4;missing++) {
            boolean[] flags={true,true,true,true}; flags[missing]=false;
            assertFalse(ProgressionProtocol.compatible(flags[0],flags[1],flags[2],flags[3]));
        }
    }
    @Test void incompatibleConnectionIsRejectedBeforeAnyChangedPayloadAndOnlyOncePerJoin() {
        var player=org.mockito.Mockito.mock(net.minecraft.server.level.ServerPlayer.class);
        org.mockito.Mockito.when(player.getUUID()).thenReturn(UUID.randomUUID());
        player.connection=org.mockito.Mockito.mock(net.minecraft.server.network.ServerGamePacketListenerImpl.class);
        try(var network=org.mockito.Mockito.mockStatic(net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.class)) {
            assertFalse(ProgressionProtocol.require(player)); assertFalse(ProgressionProtocol.require(player));
            org.mockito.Mockito.verify(player.connection).disconnect(org.mockito.ArgumentMatchers.any(Component.class));
            network.verify(() -> net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any()),org.mockito.Mockito.never());
        } finally { ProgressionProtocol.disconnected(player.getUUID()); }
    }
}
