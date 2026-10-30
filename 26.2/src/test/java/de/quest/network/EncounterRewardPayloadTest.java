package de.quest.network;
import static org.junit.jupiter.api.Assertions.*;
import de.quest.network.EncounterQuestPayloads.View;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import org.junit.jupiter.api.*;
final class EncounterRewardPayloadTest {
    @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
    @Test void rewardCountsAndTranslatedTextSurviveTheActualCodec(){
        var text=Component.translatable("quest.village-quest.encounter.reward",4,2);
        var view=new View(UUID.randomUUID(),true,text,text,text,text,text,text,4,4,2);
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        try{View.CODEC.encode(buf,view);buf.writeInt(718);assertEquals(view,View.CODEC.decode(buf));assertEquals(718,buf.readInt());assertEquals(0,buf.readableBytes());}finally{buf.release();}
    }
}
