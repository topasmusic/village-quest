package de.quest.content.story;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import org.junit.jupiter.api.Test;
import java.util.List;

final class VillageBuildingFootprintTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void vanillaHousesAndCtovJobsitesQualifyButRoadsAndDecorationsDoNot() {
        for (String path : List.of("minecraft:village/plains/houses/plains_small_house_1",
                "ctov:medium/village_desert/house/small_house_1", "ctov:medium/village_desert/jobsite/weaponsmith"))
            assertTrue(ShadowsTradeRoadEncounterService.isVillageBuilding(element(path)), path);
        for (String path : List.of("minecraft:village/plains/streets/corner_01",
                "ctov:medium/village_desert/roads/straight", "ctov:medium/village_desert/deco/lamp",
                "ctov:medium/village_desert/town_center/well"))
            assertFalse(ShadowsTradeRoadEncounterService.isVillageBuilding(element(path)), path);
        var layered = mock(ListPoolElement.class);
        var layers = List.<net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement>of(
                element("ctov:medium/village_desert/house/small_house_1"), element("ctov:medium/village_desert/deco/cactus"));
        when(layered.getElements()).thenReturn(layers);
        assertTrue(ShadowsTradeRoadEncounterService.isVillageBuilding(layered));
    }
    private SinglePoolElement element(String id) {
        var element = mock(SinglePoolElement.class);
        when(element.getTemplateLocation()).thenReturn(Identifier.parse(id));
        return element;
    }
}
