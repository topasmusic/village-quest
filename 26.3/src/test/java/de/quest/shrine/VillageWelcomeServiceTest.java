package de.quest.shrine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.quest.data.PlayerQuestData;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class VillageWelcomeServiceTest {
    private static final UUID FIRST = UUID.fromString("7cbec9cf-5f1d-4ca4-b7de-f8d907bc3471");
    private static final UUID SECOND = UUID.fromString("f1304cea-17b8-414b-a5b2-9e941487d7e4");
    private static final UUID THIRD = UUID.fromString("71d0fe6b-c2d4-47eb-afbc-c9310d13b114");

    @Test
    void threeDistinctVillagersCompleteTheWelcome() {
        PlayerQuestData data = new PlayerQuestData();
        VillageWelcomeService.begin(data, 4);

        assertEquals(VillageWelcomeService.GreetingResult.PROGRESSED,
                VillageWelcomeService.greet(data, FIRST));
        assertEquals(VillageWelcomeService.GreetingResult.PROGRESSED,
                VillageWelcomeService.greet(data, SECOND));
        assertEquals(VillageWelcomeService.GreetingResult.COMPLETED,
                VillageWelcomeService.greet(data, THIRD));
        assertTrue(VillageWelcomeService.isCompleted(data));
        assertFalse(data.hasTradeRouteFlag("guild_intro.welcome_active"));
        assertEquals(3, data.getTradeRouteInt("guild_intro.welcome_greetings"));
        assertEquals(5, data.getTradeRouteInt("guild_intro.welcome_village"));
    }

    @Test
    void repeatedVillagerCannotAdvanceTheAssignment() {
        PlayerQuestData data = new PlayerQuestData();
        VillageWelcomeService.begin(data, 0);

        assertEquals(VillageWelcomeService.GreetingResult.PROGRESSED,
                VillageWelcomeService.greet(data, FIRST));
        assertEquals(VillageWelcomeService.GreetingResult.REPEATED,
                VillageWelcomeService.greet(data, FIRST));
        assertEquals(1, data.getTradeRouteInt("guild_intro.welcome_greetings"));
        assertFalse(VillageWelcomeService.isCompleted(data));
    }

    @Test
    void completedWelcomeCannotRestart() {
        PlayerQuestData data = new PlayerQuestData();
        VillageWelcomeService.begin(data, 0);
        VillageWelcomeService.greet(data, FIRST);
        VillageWelcomeService.greet(data, SECOND);
        VillageWelcomeService.greet(data, THIRD);

        VillageWelcomeService.begin(data, 7);

        assertEquals(1, data.getTradeRouteInt("guild_intro.welcome_village"));
        assertEquals(VillageWelcomeService.GreetingResult.IGNORED,
                VillageWelcomeService.greet(data, UUID.randomUUID()));
    }
}
