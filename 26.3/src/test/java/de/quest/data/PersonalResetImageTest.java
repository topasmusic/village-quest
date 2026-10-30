package de.quest.data;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.*;

final class PersonalResetImageTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    @Test void personalImageExcludesOtherPlayersAndPublicationNeverRollsBackTheirLaterChanges() {
        var state=QuestState.fromNbt(new CompoundTag());var owner=UUID.randomUUID();var other=UUID.randomUUID();
        var old=state.getPlayerData(owner);old.setCurrencyBalance(50);
        de.quest.quest.daily.FirstDailyChoiceService.migratePre24Progress(old);
        old.setStoryChapterProgress("the_empty_caravan",3);
        var foreign=state.getPlayerData(other);foreign.setCurrencyBalance(99);
        var before=state.personalResetImage(owner);
        var next=QuestState.personalResetImage(owner,old.personalRestart());
        var isolated=QuestState.fromNbt(before);
        assertEquals(java.util.Set.of(owner),isolated.getPlayersView().keySet());
        assertEquals(before,QuestState.toNbt(isolated),"Native personal image must survive its own codec");
        assertEquals(next,QuestState.personalResetImage(owner,isolated.getPlayerData(owner).personalRestart()),"Frozen restart image must be stable after reload");
        assertEquals(next,QuestState.toNbt(QuestState.fromNbt(next)),"New personal image must not change on its first reload");
        foreign.setCurrencyBalance(123);
        assertTrue(state.publishPersonalReset(owner,before,next));
        assertEquals(0,state.getPlayerData(owner).getCurrencyBalance());assertEquals(2,state.getPlayerData(owner).progressGeneration());
        assertSame(foreign,state.getPlayerData(other));assertEquals(123,foreign.getCurrencyBalance());
        assertFalse(state.publishPersonalReset(owner,before,next));
    }
    @Test void staleOrForeignPersonalImageCannotReplaceEitherPlayer() {
        var state=QuestState.fromNbt(new CompoundTag());var owner=UUID.randomUUID();var other=UUID.randomUUID();
        var old=state.getPlayerData(owner);old.setCurrencyBalance(50);
        var before=state.personalResetImage(owner);var next=QuestState.personalResetImage(owner,old.personalRestart());
        old.setCurrencyBalance(51);
        assertFalse(state.publishPersonalReset(owner,before,next));assertSame(old,state.getPlayerData(owner));
        var beforeForeignAttempt=QuestState.toNbt(state);
        assertFalse(state.publishPersonalReset(other,before,next));assertEquals(51,old.getCurrencyBalance());
        assertEquals(beforeForeignAttempt,QuestState.toNbt(state),"A rejected foreign owner must not create even an empty record");
    }
    @Test void unmigratedBeforeImageRefusesWithoutChangingTheLivePlayer() {
        var state=QuestState.fromNbt(new CompoundTag());var owner=UUID.randomUUID();var old=state.getPlayerData(owner);
        old.setCurrencyBalance(50);var before=state.personalResetImage(owner);
        assertFalse(state.publishPersonalReset(owner,before,QuestState.personalResetImage(owner,old.personalRestart())));
        assertSame(old,state.getPlayerData(owner));assertEquals(before,state.personalResetImage(owner));
    }
}
