package de.quest.resource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.quest.access.ForgingQuickMoveState;
import org.junit.jupiter.api.Test;

final class MixinPackageBoundaryTest {
    @Test
    void runtimeContractsStayOutsideTheMixinOwnedPackage() {
        String contractPackage = ForgingQuickMoveState.class.getPackageName();

        assertFalse(contractPackage.equals("de.quest.mixin")
                        || contractPackage.startsWith("de.quest.mixin."),
                "A class referenced by transformed targets cannot live in the configured Mixin package");
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("de.quest.mixin.ForgingQuickMoveState"));
    }
}
