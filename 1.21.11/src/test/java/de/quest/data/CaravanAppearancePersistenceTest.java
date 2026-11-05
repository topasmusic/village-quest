package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.caravan.CaravanCrewLifecycle;
import de.quest.caravan.CaravanRole;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class CaravanAppearancePersistenceTest {
    @BeforeAll static void registries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void nativeQuestSaveRetainsAppearanceNameAndLogicalLifeWithoutRewritingPersonalProgress() {
        var state=QuestState.fromNbt(new CompoundTag()); UUID owner=UUID.randomUUID(); var data=state.getPlayerData(owner);
        data.setTradeRouteInt("route_count",1); data.setTradeRouteString("route_0_crew_master_name","Mara Oakridge");
        data.setReputation("crafting",200);
        var before=CaravanCrewLifecycle.resolve(data,0,CaravanRole.MASTER); assertTrue(before.female());
        var loaded=QuestState.fromNbt(QuestState.toNbt(state)).getPlayerData(owner);
        assertEquals(before,CaravanCrewLifecycle.resolve(loaded,0,CaravanRole.MASTER));
        assertEquals(200,loaded.getReputation("crafting"));
    }
}
