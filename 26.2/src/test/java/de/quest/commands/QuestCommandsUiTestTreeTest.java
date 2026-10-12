package de.quest.commands;

import static org.junit.jupiter.api.Assertions.*;
import de.quest.shrine.VillageBondLevel;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.stream.Collectors;

final class QuestCommandsUiTestTreeTest {
    @Test void rewardTestIsAbsentFromRegisteredCommandsWhileNormalRewardsAndUiTourRemain() {
        QuestCommands.register();
        var dispatcher = new com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>();
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.invoker().register(
                dispatcher, org.mockito.Mockito.mock(net.minecraft.commands.CommandBuildContext.class),
                net.minecraft.commands.Commands.CommandSelection.DEDICATED);
        var root = dispatcher.getRoot().getChild("villagequest");
        assertNotNull(root);
        var admin = root.getChild("admin");
        assertNotNull(admin);
        assertNull(admin.getChild("rewardtest"), "Temporary item/currency granting commands must not be registered");
        assertNotNull(admin.getChild("uitest"));
        assertNotNull(root.getChild("rewards").getChild("claim"));
        assertSame(root, dispatcher.getRoot().getChild("vq").getRedirect());
    }
    @BeforeAll static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        // 26.3 data-driven item defaults are normally bound by game startup.
        var components = net.minecraft.core.component.DataComponentMap.builder()
                .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 64).build();
        for (var request : de.quest.shrine.VillageRequestType.values()) request.item().builtInRegistryHolder().bindComponents(components);
    }
    @Test void oneExecutableTourReplacesAllIndividualPreviewSubcommands() {
        var command = QuestCommands.buildUiTestCommand().build();
        assertNotNull(command.getCommand());
        assertTrue(command.getChildren().isEmpty());
    }
    @Test void tourCoversEveryFrameConsumerAndNavigationNeverLeavesTheCatalog() {
        assertEquals(Set.of("journal", "questmaster", "trust", "pilgrim", "prosperity", "noticeboard", "noticejourney", "dispatch", "caravan", "wayshrine", "routes", "guildpath"),
                UiTestTourSteps.ALL.stream().map(UiTestTourSteps.Step::family).collect(Collectors.toSet()));
        assertEquals(UiTestTourSteps.ALL.size(), UiTestTourSteps.ALL.stream().distinct().count());
        assertEquals(0, UiTestTourSteps.move(0, -1));
        assertEquals(UiTestTourSteps.ALL.size() - 1, UiTestTourSteps.move(UiTestTourSteps.ALL.size() - 1, 1));
        for (int i = 0; i < UiTestTourSteps.ALL.size() - 1; i++) assertEquals(i + 1, UiTestTourSteps.move(i, 1));
    }
    @Test void isolatedSnapshotsHavePreviewFlagsAndImpossibleSessionIdentifiers() {
        for (String mode : new String[] {"travel", "cargo", "incident", "paused"})
            assertEquals(de.quest.network.ReputationPayloads.NONE, UiTestFixtures.caravan(mode).entityId());
        assertTrue(UiTestFixtures.noticeBoard(VillageBondLevel.KNOWN).requestAvailable());
        assertTrue(UiTestFixtures.journey("choice").preview());
        assertTrue(UiTestFixtures.wayshrine(true).currentIndex() >= 10_000);
        assertEquals(2, UiTestFixtures.wayshrine(false).guestMultiplier());
    }

    @Test void everyTourLabelResolvesInAllSupportedLanguages() throws Exception {
        for (String language : new String[] {"en_us", "de_de", "es_es"}) {
            try (var input = getClass().getResourceAsStream("/assets/village-quest/lang/" + language + ".json")) {
                assertNotNull(input);
                var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (var step : UiTestTourSteps.ALL) {
                    for (String key : new String[] {"family." + step.family(), "state." + step.state()}) {
                        assertTrue(json.has("screen.village-quest.ui_tour." + key), language + ": " + key);
                    }
                }
                for (String key : new String[] {"step", "back", "next", "finish", "exit"})
                    assertTrue(json.has("screen.village-quest.ui_tour." + key), language + ": " + key);
            }
        }
    }
}
