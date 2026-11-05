package de.quest.party;

import de.quest.quest.story.StoryArcType;
import de.quest.quest.story.StoryQuestKeys;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PartyStoryLaneMigrationTest {
    @Test void futurePartyFormatPreservesWholeManagerAndBlocksActions() {
        var p=new PartyRuntime(UUID.randomUUID(),UUID.randomUUID()); p.members().add(p.leaderId());
        var raw=QuestPartyPersistence.write(Map.of(p.id(),p),Map.of());
        raw.getListOrEmpty("parties").getCompoundOrEmpty(0).putInt("storyLaneFormat",72);
        raw.putString("futureReceipt","must survive");
        var input=new CompoundTag(); input.put("questPartyManager",raw);
        var state=de.quest.data.QuestState.TYPE.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE,input).getOrThrow();
        assertFalse(state.storyLanesWritable());
        var output=(CompoundTag)de.quest.data.QuestState.TYPE.codec().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,state).getOrThrow();
        assertEquals(raw,output.getCompoundOrEmpty("questPartyManager"));
        assertTrue(load(raw).isEmpty());
    }
    @Test void legacyKingdomMovesWholeSessionBeforeRuntimeAndIsIdempotent() {
        UUID leader = UUID.randomUUID(), member = UUID.randomUUID();
        var party = new PartyRuntime(UUID.randomUUID(), leader);
        party.members().addAll(java.util.List.of(leader, member));
        party.story().bind("living_kingdoms#2", 2);
        party.story().setFlag(StoryQuestKeys.SHARED_TURN_IN_CONSUMED, true);
        party.story().setFlag("kingdom_village:" + UUID.randomUUID(), true);
        party.story().markSynced(leader);
        party.storyOffers().put(member, new QuestJoinOffer("living_kingdoms#2", 2, leader));
        var legacy = QuestPartyPersistence.write(Map.of(party.id(), party), Map.of());
        legacy.getListOrEmpty("parties").getCompoundOrEmpty(0).remove("storyLaneFormat");
        legacy.getListOrEmpty("parties").getCompoundOrEmpty(0).remove("kingdomStory");
        legacy.getListOrEmpty("parties").getCompoundOrEmpty(0).remove("kingdomStoryOffers");

        var restored = load(legacy).get(party.id());
        assertNull(restored.story().questId());
        assertTrue(restored.storyOffers().isEmpty());
        assertTrue(QuestPartySessions.matchesStorySession(restored, StoryArcType.LIVING_KINGDOMS, 2));
        var encoded = QuestPartyPersistence.write(Map.of(party.id(), restored), Map.of());
        var saved = encoded.getListOrEmpty("parties").getCompoundOrEmpty(0);
        assertEquals(1, saved.getIntOr("storyLaneFormat", 0));
        assertTrue(saved.getCompoundOrEmpty("kingdomStory").getListOrEmpty("flags").size() >= 2);
        assertEquals(1, saved.getListOrEmpty("kingdomStoryOffers").size());
        assertEquals(encoded, QuestPartyPersistence.write(load(encoded), Map.of()));
    }

    private static Map<UUID, PartyRuntime> load(CompoundTag root) {
        Map<UUID, PartyRuntime> loaded = new HashMap<>();
        QuestPartyPersistence.read(root, loaded, new HashMap<>(), new HashMap<>());
        return loaded;
    }
}
