package de.quest.network;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import de.quest.network.FriendsQuestPayloads.*;
import org.junit.jupiter.api.Test;

final class FriendsQuestPayloadsTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}

    @Test void translatedViewAndActionsRoundTripWithoutFlatteningText() {
        var text=Component.translatable("quest.village-quest.friends.simon.1.title");
        var view=new View(UUID.randomUUID(),7,true,true,"simon",1,false,Component.literal("Simon"),text,text,List.of(text),text,List.of(text),List.of(new Progress("minecraft:fern",5,8)),List.of(new Option("accept",text,true)));
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
        try {View.CODEC.encode(buf,view);assertEquals(view,View.CODEC.decode(buf));var a=new Action(view.session(),7,"accept");Action.CODEC.encode(buf,a);assertEquals(a,Action.CODEC.decode(buf));}finally{buf.release();}
    }
    @Test void invalidAndOversizedInputsAreRejectedBeforeAllocatingCollections() {
        assertThrows(IllegalArgumentException.class,()->new Action(UUID.randomUUID(),0,"give"));
        assertThrows(IllegalArgumentException.class,()->new Progress("bad item",0,8));
        assertThrows(IllegalArgumentException.class,()->new Progress("minecraft:fern",0,65));
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
        try {
            b.writeUUID(UUID.randomUUID());b.writeVarLong(0);b.writeBoolean(true);b.writeBoolean(true);b.writeUtf("simon");b.writeVarInt(1);b.writeBoolean(false);
            for(int i=0;i<3;i++)net.minecraft.network.chat.ComponentSerialization.TRUSTED_STREAM_CODEC.encode(b,Component.empty());
            b.writeVarInt(Integer.MAX_VALUE);assertThrows(IllegalArgumentException.class,()->View.CODEC.decode(b));
        }finally{b.release();}
    }
}
