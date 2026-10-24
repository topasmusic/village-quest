package de.quest.network;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import org.junit.jupiter.api.Test;

final class QuestTrackerPositionPayloadTest {
    @Test void bothSideRequestsRoundTripWithoutQuestOrWorldData() {
        for(boolean right:new boolean[]{false,true}) {
            var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
            try {
                QuestTrackerPositionPayload.CODEC.encode(buffer,new QuestTrackerPositionPayload(right));
                assertEquals(1,buffer.readableBytes());
                assertEquals(right,QuestTrackerPositionPayload.CODEC.decode(buffer).right());
                assertEquals(0,buffer.readableBytes());
            } finally {buffer.release();}
        }
    }
}
