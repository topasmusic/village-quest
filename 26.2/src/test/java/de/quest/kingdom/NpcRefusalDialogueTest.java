package de.quest.kingdom;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class NpcRefusalDialogueTest {
    @Test void responsesArePersonalRotateAndRejectedClicksDoNotMoveTheCooldown() {
        var policy=new NpcRefusalDialogue(); var person=UUID.randomUUID(); var actor=UUID.randomUUID();
        int first=policy.next(person,actor,100).orElseThrow();
        assertTrue(policy.next(person,actor,399).isEmpty());
        assertTrue(policy.next(person,UUID.randomUUID(),101).isPresent());
        int second=policy.next(person,actor,400).orElseThrow(); assertNotEquals(first,second);
        assertTrue(policy.next(person,actor,699).isEmpty());
        assertNotEquals(second,policy.next(person,actor,700).orElseThrow());
    }
}
