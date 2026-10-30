package de.quest.pilgrim;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.*;

final class PilgrimShortObjectiveTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM)
            item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                    .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64)
                    .set(net.minecraft.core.component.DataComponents.ITEM_NAME,Component.translatable(item.getDescriptionId())).build());
    }
    private String key(Component c) { return ((TranslatableContents)c.getContents()).getKey(); }
    @Test void eachPeacefulExpeditionSelectsOnlyItsFirstMissingRealStep() {
        for(var type:PilgrimContractType.values()) {
            if(type==PilgrimContractType.ROADMARKS_FOR_THE_COMPASS) continue;
            var state=PilgrimExpeditionProgress.State.empty();
            assertEquals("quest.village-quest.tracker.expedition_enter",key(PilgrimContractService.expeditionObjective(type,state,0,false)));
            state=PilgrimExpeditionProgress.tick(state,true,false,0,0,false,false);
            assertEquals("quest.village-quest.tracker.expedition_travel",key(PilgrimContractService.expeditionObjective(type,state,0,false)));
            state=PilgrimExpeditionProgress.tick(state,true,false,330,0,false,false);
            assertEquals("quest.village-quest.tracker.friend_material",key(PilgrimContractService.expeditionObjective(type,state,0,false)));
            assertTrue(key(PilgrimContractService.expeditionObjective(type,state,64,false)).startsWith("quest.village-quest.tracker.expedition_return"));
            state=PilgrimExpeditionProgress.tick(state,false,true,0,0,true,false);
            assertEquals("quest.village-quest.tracker.end_city",key(PilgrimContractService.expeditionObjective(type,state,64,true)));
            assertEquals("quest.village-quest.tracker.meet_pilgrim",key(PilgrimContractService.expeditionObjective(type,state,64,false)));
        }
    }
}
